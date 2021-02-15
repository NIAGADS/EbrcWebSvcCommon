package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.eupathdb.common.model.ProjectMapper;
import org.eupathdb.websvccommon.wsfplugin.CloseableResponse;
import org.eupathdb.websvccommon.wsfplugin.PluginUtilities;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.IoUtil;
import org.gusdb.fgputil.Timer;
import org.gusdb.fgputil.runtime.ThreadUtil;
import org.gusdb.fgputil.web.HttpMethod;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wsf.plugin.AbstractPlugin;
import org.gusdb.wsf.plugin.DelayedResultException;
import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginRequest;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.PluginUserException;
import org.json.JSONObject;

public abstract class AbstractMultiBlastServicePlugin extends AbstractPlugin {

  private static final Logger LOG = Logger.getLogger(AbstractMultiBlastServicePlugin.class);

  private static final int INITIAL_WAIT_TIME_MILLIS = 2 /* seconds */ * 1000;
  private static final int POLLING_INTERVAL_MILLIS = 5 /* seconds */ * 1000;
  private static final int MAX_WAIT_TIME_MILLIS = 5 /* minutes */ * 60 * 1000;

  // field definitions in the config file
  private static final String FILE_CONFIG = "multiblast-config.xml";

  // required properties in model.prop
  private static final String LOCALHOST_PROP_KEY = "LOCALHOST";
  private static final String SERVICE_URL_PROP_KEY = "MULTI_BLAST_SERVICE_URL";

  private final ResultFormatter _resultFormatter;

  public AbstractMultiBlastServicePlugin(ResultFormatter resultFormatter) {
    super(FILE_CONFIG);
    _resultFormatter = resultFormatter;
  }

  @Override
  public void initialize() throws PluginModelException {
    super.initialize();
    _resultFormatter.setConfig(new BlastConfig(properties));
  }

  @Override
  public String[] getRequiredParameterNames() {
    return MultiBlastServiceParams.getAllParamNames();
  }

  @Override
  public String[] getColumns(PluginRequest request) throws PluginModelException {
    return _resultFormatter.getDeclaredColumns();
  }

  @Override
  public void validateParameters(PluginRequest request) throws PluginModelException, PluginUserException {
    // let WDK handle validation for now
  }

  @Override
  protected int execute(PluginRequest request, PluginResponse response)
      throws PluginModelException, PluginUserException, DelayedResultException {
    // find base URL for multi-blast service, as well as the job endpoint
    String multiBlastServiceUrl = getMultiBlastServiceUrl(request);
    String jobsEndpointUrl = multiBlastServiceUrl + "/jobs";

    // retrieve project ID
    WdkModel wdkModel = PluginUtilities.getWdkModel(request);
    String projectId = wdkModel.getProjectId();

    // use passed params to POST new job request to blast service
    JSONObject newJobRequestJson = new JSONObject()
      .put("site", projectId)
      .put("config", MultiBlastServiceParams.buildNewJobRequestConfigJson(request.getParams()))
      .put("targets", MultiBlastServiceParams.buildNewJobRequestTargetJson(request.getParams()));

    String jobId = createJob(newJobRequestJson, jobsEndpointUrl);

    // start timer on wait time
    Timer t = new Timer();
    
    // wait a short interval for blast service to look job up in cache and assign complete status
    ThreadUtil.sleep(INITIAL_WAIT_TIME_MILLIS);

    // keep going until job complete or max wait time expired
    while (true) {

      // query the job status (if results in cache, should return complete immediately)
      if (isJobComplete(multiBlastServiceUrl, jobId)) {
        break;
      }

      // if max wait time reached, throw delayed result exception
      if (t.getElapsed() > (MAX_WAIT_TIME_MILLIS)) {
        throw new DelayedResultException();
      }

      // sleep until ready to poll again
      ThreadUtil.sleep(POLLING_INTERVAL_MILLIS);
    }

    // job complete; gather remaining prerequisites
    RecordClass recordClass = PluginUtilities.getRecordClass(request);
    String dbType = request.getParams().get(MultiBlastServiceParams.BLAST_DATABASE_TYPE_PARAM_NAME);
    String[] orderedColumns = request.getOrderedColumns();

    // set up project mapper
    try {
      ProjectMapper projectMapper = ProjectMapper.getMapper(wdkModel);
      _resultFormatter.setProjectMapper(projectMapper);
    } catch (WdkModelException ex) {
      LOG.error("WdkModelException: " + ex);
      throw new PluginModelException(ex);
    }

    // write results to plugin response
    writeResults(multiBlastServiceUrl, jobId, response, wdkModel, recordClass, dbType, orderedColumns);

    return 0;
  }

  private void writeResults(String multiBlastServiceUrl, String jobId,
      PluginResponse response, WdkModel wdkModel, RecordClass recordClass,
      String dbType, String[] orderedColumns) throws PluginModelException, PluginUserException {

    // define request data
    String jobReportEndpointUrl = multiBlastServiceUrl + "/jobs/" + jobId + "/report";

    LOG.info("Requesting multi-blast job results at " + jobReportEndpointUrl);

    // make job report request
    try (CloseableResponse jobReportResponse = makeRequest(
        jobReportEndpointUrl, HttpMethod.GET, Optional.empty())) {

      if (jobReportResponse.getStatus() != 200) {
        // error occurred; read entire body for error message
        String responseBody = readSmallResponseBody(jobReportResponse);
        throw new PluginModelException("Unexpected response from multi-blast " +
            "service while fetching job report (jobId=" + jobId + "): " +
            jobReportResponse.getStatus() + FormatUtil.NL + responseBody);
      }

      // request appears to be successful; read, parse and write result stream data into plugin response
      try (InputStream resultStream = (InputStream)jobReportResponse.getEntity()) {
        _resultFormatter.formatResult(response, orderedColumns, resultStream, recordClass, dbType, wdkModel);
      }
    }
    catch (IOException e) {
      throw new PluginModelException("Unable to read result stream", e);
    }
  }

  /**
   * Makes a request to the multi-blast service to check the status of the job
   * with the passed ID.  Returns whether job is complete or still running. If
   * job status is "error", throws a PluginModelException with the description.
   *
   * @param multiBlastServiceUrl blast service base URL
   * @param jobId job whose status to fetch
   * @return true if job is complete, else false (if still running)
   * @throws PluginModelException if job has errored
   */
  private static boolean isJobComplete(String multiBlastServiceUrl, String jobId) throws PluginModelException {
    String jobIdEndpointUrl = multiBlastServiceUrl + "/jobs/" + jobId;
    LOG.info("Requesting multi-blast job status at " + jobIdEndpointUrl);

    // make job status request
    try (CloseableResponse jobStatusResponse = makeRequest(
        jobIdEndpointUrl, HttpMethod.GET, Optional.empty())) {
  
      String responseBody = readSmallResponseBody(jobStatusResponse);
      if (jobStatusResponse.getStatus() != 200) {
        throw new PluginModelException("Unexpected response from multi-blast " +
            "service while checking job status (jobId=" + jobId + "): " +
            jobStatusResponse.getStatus() + FormatUtil.NL + responseBody);
      }
  
      // parse response and analyze
      JSONObject responseObj = new JSONObject(responseBody);
      switch(responseObj.getString("status")) {
        case "queued":
        case "in-progress":
          return false;
        case "completed":
          return true;
        case "errored":
          throw new PluginModelException(
            "Multi-blast service job failed: " + responseObj.getString("description"));
        default:
          throw new PluginModelException(
            "Multi-blast service job status endpoint returned unrecognized status value: " + responseObj.getString("status"));
      }
    }
  }

  private static String createJob(JSONObject newJobRequestBody, String jobsEndpointUrl) throws PluginModelException {
    LOG.info("Requesting new multi-blast job at " + jobsEndpointUrl + " with JSON body: " + newJobRequestBody.toString(2));

    // make new job request
    try (CloseableResponse newJobResponse = makeRequest(
        jobsEndpointUrl, HttpMethod.POST, Optional.of(newJobRequestBody))) {

      String responseBody = readSmallResponseBody(newJobResponse);
      if (newJobResponse.getStatus() != 200) {
        throw new PluginModelException("Unexpected response from multi-blast " +
            "service while requesting new job: " + newJobResponse.getStatus() +
            FormatUtil.NL + responseBody);
      }
  
      // return job ID
      return new JSONObject(responseBody).getString("jobId");
    }
  }

  private static CloseableResponse makeRequest(String url, HttpMethod method, Optional<JSONObject> body) {
    Client client = ClientBuilder.newClient();
    WebTarget webTarget = client.target(url);
    Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
    switch(method) {
      case POST:
        return new CloseableResponse(invocationBuilder.post(Entity.entity(body.map(JSONObject::toString)
            .orElseThrow(() -> new RuntimeException("Body is required for POST")), MediaType.APPLICATION_JSON)));
      case GET:
        body.ifPresent(b -> LOG.warn("JSONObject passed to method to generated GET request; it will be ignored."));
        return new CloseableResponse(invocationBuilder.get());
      default:
        throw new RuntimeException("Only POST and GET methods are currently supported (not " + method + ").");
    }
  }

  private static String readSmallResponseBody(Response smallResponse) throws PluginModelException {
    String responseBody = "";
    if (smallResponse.hasEntity()) {
      try (InputStream body = (InputStream)smallResponse.getEntity();
           ByteArrayOutputStream str = new ByteArrayOutputStream()) {
        IoUtil.transferStream(str, body);
        responseBody = str.toString();
      }
      catch (IOException e) {
        throw new PluginModelException("Unable to read response body from service response.", e);
      }
    }
    return responseBody;
  }

  private static String getMultiBlastServiceUrl(PluginRequest request) throws PluginModelException {
    Map<String,String> modelProps = PluginUtilities.getWdkModel(request).getProperties();
    String localhost = modelProps.get(LOCALHOST_PROP_KEY);
    String multiBlastServiceUrl = modelProps.get(SERVICE_URL_PROP_KEY);
    if (localhost == null || multiBlastServiceUrl == null) {
      throw new PluginModelException("model.prop must contain the properties: " +
          LOCALHOST_PROP_KEY + ", " + SERVICE_URL_PROP_KEY);
    }
    return localhost + multiBlastServiceUrl;
  }
}
