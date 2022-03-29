package org.eupathdb.websvccommon.wsfplugin.solrsearch;

import static org.eupathdb.websvccommon.wsfplugin.solrsearch.SiteSearchUtil.getSearchFields;
import static org.eupathdb.websvccommon.wsfplugin.solrsearch.SiteSearchUtil.getSiteSearchServiceUrl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.eupathdb.websvccommon.wsfplugin.PluginUtilities;
import org.eupathdb.websvccommon.wsfplugin.solrsearch.SiteSearchUtil.SearchField;
import org.gusdb.fgputil.ArrayUtil;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.FormatUtil.Style;
import org.gusdb.fgputil.functional.Functions;
import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.fgputil.web.MimeTypes;
import org.gusdb.wdk.model.record.PrimaryKeyDefinition;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wsf.plugin.AbstractPlugin;
import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginRequest;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.PluginUserException;
import org.json.JSONArray;
import org.json.JSONObject;

public class EuPathSiteSearchPlugin extends AbstractPlugin {

  private static final Logger LOG = Logger.getLogger(EuPathSiteSearchPlugin.class);

  protected static final String SEARCH_TEXT_PARAM_NAME = "text_expression";
  protected static final String SEARCH_DOC_TYPE = "document_type";
  protected static final String SEARCH_FIELDS_PARAM_NAME = "text_fields";

  @Override
  public String[] getRequiredParameterNames() {
    return new String[]{ SEARCH_TEXT_PARAM_NAME, SEARCH_FIELDS_PARAM_NAME };
  }

  @Override
  public String[] getColumns(PluginRequest request) throws PluginModelException {
    RecordClass recordClass = PluginUtilities.getRecordClass(request);
    PrimaryKeyDefinition pkDef = recordClass.getPrimaryKeyDefinition();
    String[] dynamicColumns = getDynamicColumns(recordClass);
    String[] columns = ArrayUtil.concatenate(pkDef.getColumnRefs(), dynamicColumns);
    LOG.info("SiteSearchPlugin instance will return the following columns: " + FormatUtil.join(columns, ", "));
    return columns;
  }

  /**
   * @param recordClass recordClass for this request; dynamic columns may differ by recordclass
   * @return dynamic columns expected for this request
   */
  protected String[] getDynamicColumns(RecordClass recordClass) {
    return new String[]{ "max_score" };
  }

  @Override
  public void validateParameters(PluginRequest request) throws PluginModelException, PluginUserException {
    // most validation already performed by WDK; make sure passed doc type
    //   matches urlSegment of requested record class
    if (!getRequestedDocumentType(request).equals(request.getParams().get(SEARCH_DOC_TYPE))) {
      throw new PluginUserException("Invalid param value '" +
          request.getParams().get(SEARCH_DOC_TYPE) + "' for " + SEARCH_DOC_TYPE +
          ".  Value for this recordclass must be " + getRequestedDocumentType(request));
    }
  }

  @Override
  protected int execute(PluginRequest request, PluginResponse response)
      throws PluginModelException, PluginUserException {
    LOG.info("Executing " + EuPathSiteSearchPlugin.class.getSimpleName() +
        " with params " + FormatUtil.prettyPrint(request.getParams(), Style.MULTI_LINE));
    Response searchResponse = null;
    try {
      // build request elements
      String searchUrl = getSiteSearchServiceUrl(request);
      JSONObject requestBody = buildRequestJson(request);
      LOG.info("Querying site search service at " + searchUrl + " with JSON body: " + requestBody.toString(2));

      // make request
      Client client = ClientBuilder.newClient();
      WebTarget webTarget = client.target(searchUrl);
      Invocation.Builder invocationBuilder = webTarget.request(MimeTypes.ND_JSON);
      searchResponse = invocationBuilder.post(Entity.entity(requestBody.toString(), MediaType.APPLICATION_JSON));

      LOG.info("Received response from site search service with status: " + searchResponse.getStatus());

      BufferedReader br = new BufferedReader(new InputStreamReader((InputStream)searchResponse.getEntity()));
      String line;
      RecordClass recordClass = PluginUtilities.getRecordClass(request);
      boolean pkHasProjectId = recordClass.getPrimaryKeyDefinition().hasColumn("project_id");
      Priority recordLoggingPriority = Level.DEBUG;
      boolean logRecordProcessing = LOG.isEnabledFor(recordLoggingPriority);
      while ((line = br.readLine()) != null) {
        if (logRecordProcessing) LOG.log(recordLoggingPriority,
          "Site Search Service response line: " + line);
        String[] tokens = line.split(FormatUtil.TAB);
        if (tokens.length < 2 || tokens.length > 3) {
          throw new PluginModelException("Unexpected format in line: " + line);
        }
        JSONArray primaryKey = new JSONArray(tokens[0]);
        String score = tokens[1];
        Optional<String> solrRecordProjectId = tokens.length == 3 && !tokens[2].isBlank() ?
            Optional.of(tokens[2].trim()) : Optional.empty();
        String recordProjectId = computeRecordProjectId(solrRecordProjectId, primaryKey, request);

        // build WSF plugin result row from parsed site search row
        String[] row = readResultRow(recordClass, primaryKey, pkHasProjectId, recordProjectId, score);

        if (logRecordProcessing) LOG.log(recordLoggingPriority,
          "Returning row (project ID appended? " + solrRecordProjectId.isEmpty() + "): " + new JSONArray(row).toString());
        response.addRow(row);
      }
      return 0;
    }
    catch (Exception e) {
      throw new PluginModelException("Could not read response from site search service", e);
    }
    finally {
      if (searchResponse != null) searchResponse.close();
    }
  }

  /**
   * Figure out what projectId to add to the primary key of the row put in the WDK cache
   *
   * @param solrRecordProjectId projectId sent by site search service, if any
   * @param primaryKey primary key of the row sent by site search service
   * @param request plugin request (context may matter to subclasses)
   * @return project ID to assign to this row
   * @throws PluginModelException if unable to calculate project ID
   */
  protected String computeRecordProjectId(Optional<String> solrRecordProjectId, JSONArray primaryKey, PluginRequest request) throws PluginModelException {
    // supplement with local project ID if not sent by site search service
    return solrRecordProjectId.orElse(request.getProjectId());
  }

  /**
   * @param recordClass recordClass for this row (may impact how row is constructed)
   * @param primaryKey primary key array
   * @param pkHasProjectId whether this RC's PK has a project_id
   * @param recordProjectId the record's project ID (include if PK has project_id)
   * @param score score returned by SOLR
   * @return result row array to be returned to WSF
   */
  protected String[] readResultRow(RecordClass recordClass, JSONArray primaryKey,
      boolean pkHasProjectId, String recordProjectId, String score) {
    return ArrayUtil.concatenate(JsonUtil.toStringArray(primaryKey),
      // only include projectId if it is a primary key field
      pkHasProjectId ? new String[] { recordProjectId, score } : new String[] { score });
  }

  /**
   * Builds something like this:
   * {
   *   searchText: string,
   *   restrictToProject?: string,
   *   restrictSearchToOrganisms?: string[], (must be subset of metadata orgs)
   *   documentTypeFilter?: {
   *     documentType: string,
   *     foundOnlyInFields?: string[]
   *   }
   * }
   */
  private JSONObject buildRequestJson(PluginRequest request) throws PluginModelException {
    String docType = getRequestedDocumentType(request);
    Optional<String> projectIdForFilter = getProjectIdForFilter(request.getProjectId());
    Map<String,SearchField> searchFieldMap = Functions.getMapFromValues(
        getSearchFields(getSiteSearchServiceUrl(request), docType, projectIdForFilter), field -> field.getTerm());
    Map<String,String> internalValues = request.getParams();
    String searchTerm = unquoteString(internalValues.get(SEARCH_TEXT_PARAM_NAME));
    List<String> searchFieldTerms = getTermsFromInternal(internalValues.get(SEARCH_FIELDS_PARAM_NAME), true);
    List<String> searchFieldSolrNames =
      (searchFieldTerms.isEmpty() ?
        searchFieldMap.values().stream() :
        searchFieldTerms.stream()
          .map(term -> searchFieldMap.get(term))
          .filter(field -> field != null)
      )
      .map(field -> field.getSolrField())
      .collect(Collectors.toList());
    JSONObject baseSolrRequestJson = new JSONObject()
      .put("searchText", searchTerm)
      .put("documentTypeFilter", new JSONObject()
        .put("documentType", docType)
        .put("foundOnlyInFields", searchFieldSolrNames)
      );
    return supplementSearchParams(request, baseSolrRequestJson);
  }

  protected JSONObject supplementSearchParams(PluginRequest request, JSONObject baseSolrRequestJson) {
    return baseSolrRequestJson.put("restrictToProject", request.getProjectId());
  }

  protected String getRequestedDocumentType(PluginRequest request) throws PluginModelException {
    return PluginUtilities.getRecordClass(request).getUrlSegment();
  }

  protected Optional<String> getProjectIdForFilter(String projectId) {
    return Optional.of(projectId);
  }

  protected static List<String> getTermsFromInternal(String internalEnumValue, boolean performDequote) {
    return Arrays.stream(internalEnumValue.split(","))
      .map(string -> performDequote ? unquoteString(string) : string)
      .collect(Collectors.toList());
  }

  private static String unquoteString(String quotedString) {
    return quotedString.substring(1, quotedString.length() - 1);
  }
}
