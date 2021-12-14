package org.eupathdb.websvccommon.wsfplugin.blast;

import static org.gusdb.fgputil.FormatUtil.NL;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.core.Response.Status.Family;

import org.apache.log4j.Logger;
import org.eupathdb.common.model.MultiBlastServiceUtil;
import org.eupathdb.common.model.ProjectMapper;
import org.eupathdb.common.service.PostValidationUserException;
import org.eupathdb.websvccommon.wsfplugin.PluginUtilities;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.fgputil.MapBuilder;
import org.gusdb.fgputil.Timer;
import org.gusdb.fgputil.Tuples.TwoTuple;
import org.gusdb.fgputil.client.ClientUtil;
import org.gusdb.fgputil.client.CloseableResponse;
import org.gusdb.fgputil.runtime.ThreadUtil;
import org.gusdb.fgputil.web.HttpMethod;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.user.User;
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
  private static final int MAX_REPORT_SIZE_BYTES = 90 /* megabytes */ * 1000 * 1000;

  private static final String CONTENT_MAX_LENGTH_EXCEEDED_STATUS =
    "bad-request";
  private static final String CONTENT_MAX_LENGTH_EXCEEDED_MESSAGE =
    "Requested report is larger than the specified max content size.";

  // field definitions in the config file
  private static final String FILE_CONFIG = "multiblast-config.xml";

  public static class BlastServiceBadRequestException extends PostValidationUserException {
    public BlastServiceBadRequestException(String message) {
      super(message);
    }
  }

  private final ResultFormatter _resultFormatter;

  public AbstractMultiBlastServicePlugin(ResultFormatter resultFormatter) {
    super(FILE_CONFIG);
    _resultFormatter = resultFormatter;
  }

  @Override
  public void initialize(PluginRequest request) throws PluginModelException {
    super.initialize(request);
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
    // WDK handles most validation; simply confirm single submitted sequence
    String sequence = request.getParams().get(MultiBlastServiceParams.BLAST_QUERY_SEQUENCE_PARAM_NAME);
    int firstIndex = sequence.indexOf('>');
    if (firstIndex != -1 && sequence.indexOf('>', firstIndex + 1) != -1) {
      // more than one sequence
      throw new PluginUserException("Only one sequence can be submitted at a time (should have been validated by StringParam regex).");
    }
  }

  @Override
  protected int execute(PluginRequest request, PluginResponse response)
      throws PluginModelException, PluginUserException, DelayedResultException {
  
    // get the WDK model
    WdkModel wdkModel = PluginUtilities.getWdkModel(request);

    // set up project mapper
    try {
      ProjectMapper projectMapper = ProjectMapper.getMapper(wdkModel);
      _resultFormatter.setProjectMapper(projectMapper);
    } catch (WdkModelException ex) {
      LOG.error("WdkModelException: " + ex);
      throw new PluginModelException(ex);
    }

    // get the required authentication header for this user
    TwoTuple<String,String> authHeader = getAuthHeader(wdkModel, request.getContext());

    // find base URL for multi-blast service
    String multiBlastServiceUrl = MultiBlastServiceUtil.getMultiBlastServiceUrl(
        PluginUtilities.getWdkModel(request), e -> new PluginModelException(e));

    // retrieve project ID
    String projectId = wdkModel.getProjectId();

    // use passed params to POST new job request to blast service
    JSONObject newJobRequestJson = new JSONObject()
      .put("site", projectId)
      .put("maxResultSize", 0)
      .put("maxSequences", 1)
      .put("isPrimary", false)
      .put("config", MultiBlastServiceParams.buildNewJobRequestConfigJson(request.getParams()))
      .put("targets", MultiBlastServiceParams.buildNewJobRequestTargetJson(request.getParams()));

    String jobId = createJob(newJobRequestJson, multiBlastServiceUrl, authHeader);

    // start timer on wait time
    Timer t = new Timer();
    
    // wait a short interval for blast service to look job up in cache and assign complete status
    ThreadUtil.sleep(INITIAL_WAIT_TIME_MILLIS);

    // keep going until job complete or max wait time expired
    while (true) {

      // query the job status (if results in cache, should return complete immediately)
      if (isJobComplete(multiBlastServiceUrl, jobId, authHeader)) {
        break;
      }

      // if max wait time reached, throw delayed result exception
      if (t.getElapsed() > (MAX_WAIT_TIME_MILLIS)) {
        throw new DelayedResultException();
      }

      // sleep until ready to poll again
      ThreadUtil.sleep(POLLING_INTERVAL_MILLIS);
    }

    // create a new "pairwise" report for this job
    JSONObject newReportRequestJson = new JSONObject()
      .put("jobID", jobId)
      .put("format", "pairwise");

    String reportId = createReport(newReportRequestJson, multiBlastServiceUrl, authHeader);

    // keep going until report complete or max wait time expired
    while (true) {

      // query the report status (if results in cache, should return complete immediately)
      if (isReportComplete(multiBlastServiceUrl, reportId, authHeader)) {
        break;
      }

      // if max wait time reached, throw delayed result exception
      if (t.getElapsed() > (MAX_WAIT_TIME_MILLIS)) {
        throw new DelayedResultException();
      }

      // sleep until ready to poll again
      ThreadUtil.sleep(POLLING_INTERVAL_MILLIS);
    }

    // job and report complete; gather remaining prerequisites
    RecordClass recordClass = PluginUtilities.getRecordClass(request);
    String dbType = request.getParams().get(MultiBlastServiceParams.BLAST_DATABASE_TYPE_PARAM_NAME);
    String[] orderedColumns = request.getOrderedColumns();

    // write results to plugin response
    writeResults(multiBlastServiceUrl, reportId, authHeader, response, wdkModel, recordClass, dbType, orderedColumns);

    return 0;
  }

  private TwoTuple<String, String> getAuthHeader(WdkModel wdkModel, Map<String, String> requestContext) {
    try {
      User user = wdkModel.getUserFactory().getUserById(
          Long.parseLong(requestContext.get(Utilities.QUERY_CTX_USER))).orElseThrow();
      return MultiBlastServiceUtil.getAuthHeader(wdkModel, user);
    }
    catch(WdkModelException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeResults(String multiBlastServiceUrl, String reportId, TwoTuple<String,String> authHeader,
      PluginResponse response, WdkModel wdkModel, RecordClass recordClass,
      String dbType, String[] orderedColumns) throws PluginModelException, PluginUserException {

    // define request data
    String downloadReportUrl = multiBlastServiceUrl + "/reports/" + reportId + "/files/report.txt?download=false";

    LOG.info("Requesting multi-blast report results at " + downloadReportUrl);

    TwoTuple<String,String> contentMaxLengthHeader =
      new TwoTuple<String, String>("Content-Max-Length", String.valueOf(MAX_REPORT_SIZE_BYTES));

    Map<String,String> headers = new MapBuilder<String,String>(authHeader)
      .put(contentMaxLengthHeader)
      .toMap();

    // make job report request
    try (CloseableResponse downloadReportResponse = ClientUtil.makeRequest(
        downloadReportUrl, HttpMethod.GET, Optional.empty(), headers)) {

      if (downloadReportResponse.getStatus() != 200) {
        // error occurred; read entire body for error message
        String responseBody = ClientUtil.readSmallResponseBody(downloadReportResponse);

        JSONObject responseJson = new JSONObject(responseBody);

        if (
          JsonUtil.getStringOrDefault(responseJson, "status", "").equals(CONTENT_MAX_LENGTH_EXCEEDED_STATUS) &&
          JsonUtil.getStringOrDefault(responseJson, "message", "").equals(CONTENT_MAX_LENGTH_EXCEEDED_MESSAGE)
        ) {
          throw new BlastServiceBadRequestException(
            "We're sorry, but we cannot handle BLAST results larger than " +
            MAX_REPORT_SIZE_BYTES/1000000 + "MB.  To reduce the result size, you " +
            "could decrease V=B or the Expectation value, turn on the Low " +
            "Complexity filter, or decrease the number of target organisms selected.");
        }

        throw new PluginModelException("Unexpected response from multi-blast " +
            "service while fetching report results (reportId=" + reportId + "): " +
            downloadReportResponse.getStatus() + FormatUtil.NL + responseBody);
      }

      // request appears to be successful; read, parse and write result stream data into plugin response
      try (InputStream resultStream = (InputStream)downloadReportResponse.getEntity()) {
        String message = _resultFormatter.formatResult(response, orderedColumns, resultStream, recordClass, dbType, wdkModel);
        response.setMessage(message);
      }
    }
    catch (IOException e) {
      throw new PluginModelException("Unable to read response body from service response.", e);
    }
  }

  /**
   * Makes a request to the multi-blast service to check the status of the job
   * with the passed ID.  Returns whether job is complete or still running. If
   * job status is "errored", throws a PluginModelException with the description.
   *
   * NOTE: If the job if found to be "expired", it will be rerun
   *
   * @param multiBlastServiceUrl blast service base URL
   * @param jobId job whose status to fetch
   * @return true if job is complete, else false (if still running)
   * @throws PluginModelException if job has errored
   */
  private static boolean isJobComplete(String multiBlastServiceUrl, String jobId, TwoTuple<String,String> authHeader) throws PluginModelException {
    String jobIdEndpointUrl = multiBlastServiceUrl + "/jobs/" + jobId;
    LOG.info("Requesting multi-blast job status at " + jobIdEndpointUrl);

    // make job status request
    try (CloseableResponse jobStatusResponse = ClientUtil.makeRequest(
        jobIdEndpointUrl, HttpMethod.GET, Optional.empty(), new MapBuilder<String,String>(authHeader).toMap())) {
  
      String responseBody = ClientUtil.readSmallResponseBody(jobStatusResponse);
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
        case "expired":
          rerunJob(multiBlastServiceUrl, jobId, authHeader);
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
    catch (IOException e) {
      throw new PluginModelException("Unable to read response body from service response.", e);
    }
  }

  /**
   * Makes a request to the multi-blast service to check the status of the report
   * with the passed ID.  Returns whether report is complete or still running. If
   * report status is "errored", throws a PluginModelException with the description.
   *
   * NOTE: If the report if found to be "expired", it will be rerun
   *
   * @param multiBlastServiceUrl blast service base URL
   * @param reportId report whose status to fetch
   * @return true if report is complete, else false (if still running)
   * @throws PluginModelException if report has errored
   */
  private static boolean isReportComplete(String multiBlastServiceUrl, String reportId, TwoTuple<String,String> authHeader) throws PluginModelException {
    String reportIdEndpointUrl = multiBlastServiceUrl + "/reports/" + reportId;
    LOG.info("Requesting multi-blast report status at " + reportIdEndpointUrl);

    // make job status request
    try (CloseableResponse reportStatusResponse = ClientUtil.makeRequest(
        reportIdEndpointUrl, HttpMethod.GET, Optional.empty(), new MapBuilder<String,String>(authHeader).toMap())) {

      String responseBody = ClientUtil.readSmallResponseBody(reportStatusResponse);
      if (reportStatusResponse.getStatus() != 200) {
        throw new PluginModelException("Unexpected response from multi-blast " +
            "service while checking report status (reportId=" + reportId + "): " +
            reportStatusResponse.getStatus() + FormatUtil.NL + responseBody);
      }

      // parse response and analyze
      JSONObject responseObj = new JSONObject(responseBody);
      switch(responseObj.getString("status")) {
        case "queued":
        case "in-progress":
          return false;
        case "expired":
          rerunReport(multiBlastServiceUrl, reportId, authHeader);
          return false;
        case "completed":
          return true;
        case "errored":
          throw new PluginModelException(
            "Multi-blast service report failed: " + responseObj.getString("description"));
        default:
          throw new PluginModelException(
            "Multi-blast service report status endpoint returned unrecognized status value: " + responseObj.getString("status"));
      }
    }
    catch (IOException e) {
      throw new PluginModelException("Unable to read response body from service response.", e);
    }
  }

  private static String createJob(JSONObject newJobRequestBody, String multiBlastServiceUrl, TwoTuple<String,String> authHeader) throws PluginModelException {
    String jobsEndpointUrl = multiBlastServiceUrl + "/jobs";
    LOG.info("Requesting new multi-blast job at " + jobsEndpointUrl + " with JSON body: " + newJobRequestBody.toString(2));

    // make new job request
    try (CloseableResponse newJobResponse = ClientUtil.makeRequest(
        jobsEndpointUrl, HttpMethod.POST, Optional.of(newJobRequestBody), new MapBuilder<String,String>(authHeader).toMap())) {

      String responseBody = ClientUtil.readSmallResponseBody(newJobResponse);

      if (newJobResponse.getStatus() == 200) {
        // success!  return job ID
        return new JSONObject(responseBody).getString("jobId");
      }

      if (Family.CLIENT_ERROR.equals(newJobResponse.getStatusInfo().getFamily())) {
        // error implying bad parameters
        throw new BlastServiceBadRequestException(
            "Multi-Blast service job request returned " + newJobResponse.getStatus() + NL + responseBody);
      }

      // other error
      throw new PluginModelException("Unexpected response from multi-blast " +
          "service while requesting new job: " + newJobResponse.getStatus() + NL + responseBody);
    }
    catch (IOException e) {
      throw new PluginModelException("Unable to read response body from service response.", e);
    }
  }

  private static String createReport(JSONObject newReportRequestBody, String multiBlastServiceUrl, TwoTuple<String,String> authHeader) throws PluginModelException {
    String reportsEndpointUrl = multiBlastServiceUrl + "/reports";
    LOG.info("Requesting new multi-blast report at " + reportsEndpointUrl + " with JSON body: " + newReportRequestBody.toString(2));

    // make new report request
    try (CloseableResponse newReportResponse = ClientUtil.makeRequest(
        reportsEndpointUrl, HttpMethod.POST, Optional.of(newReportRequestBody), new MapBuilder<String,String>(authHeader).toMap())) {

      String responseBody = ClientUtil.readSmallResponseBody(newReportResponse);

      if (newReportResponse.getStatus() == 200) {
        // success!  return report ID
        return new JSONObject(responseBody).getString("reportID");
      }

      throw new PluginModelException("Unexpected response from multi-blast " +
          "service while requesting new report: " + newReportResponse.getStatus() + NL + responseBody);
    }
    catch (IOException e) {
      throw new PluginModelException("Unable to read response body from service response.", e);
    }
  }

  private static void rerunJob(String multiBlastServiceUrl, String jobId, TwoTuple<String,String> authHeader) throws PluginModelException {
    String jobsIdEndpointUrl = multiBlastServiceUrl + "/jobs/" + jobId;
    LOG.info("Rerunning expired multi-blast job at " + jobsIdEndpointUrl + " with job id " + jobId);

    // make rerun job request
    try (CloseableResponse rerunJobResponse = ClientUtil.makeRequest(
        jobsIdEndpointUrl, HttpMethod.POST, Optional.of(new JSONObject()), new MapBuilder<String,String>(authHeader).toMap())) {

      String responseBody = ClientUtil.readSmallResponseBody(rerunJobResponse);

      if (!rerunJobResponse.getStatusInfo().getFamily().equals(Family.SUCCESSFUL)) {
        throw new PluginModelException("Unexpected response from multi-blast " +
            "service while rerunning job: " + rerunJobResponse.getStatus() + NL + responseBody);
      }
    }
    catch (IOException e) {
      throw new PluginModelException("Unable to read response body from service response.", e);
    }
  }

  private static void rerunReport(String multiBlastServiceUrl, String reportId, TwoTuple<String,String> authHeader) throws PluginModelException {
    String reportsIdEndpointUrl = multiBlastServiceUrl + "/reports/" + reportId;
    LOG.info("Rerunning expired multi-blast report at " + reportsIdEndpointUrl + " with report id " + reportId);

    // make rerun report request
    try (CloseableResponse rerunReportResponse = ClientUtil.makeRequest(
        reportsIdEndpointUrl, HttpMethod.POST, Optional.of(new JSONObject()), new MapBuilder<String,String>(authHeader).toMap())) {

      String responseBody = ClientUtil.readSmallResponseBody(rerunReportResponse);

      if (!rerunReportResponse.getStatusInfo().getFamily().equals(Family.SUCCESSFUL)) {
        throw new PluginModelException("Unexpected response from multi-blast " +
            "service while rerunning report: " + rerunReportResponse.getStatus() + NL + responseBody);
      }
    }
    catch (IOException e) {
      throw new PluginModelException("Unable to read response body from service response.", e);
    }
  }
}
