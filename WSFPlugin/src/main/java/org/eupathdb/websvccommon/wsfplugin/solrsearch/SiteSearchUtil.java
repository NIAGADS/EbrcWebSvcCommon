package org.eupathdb.websvccommon.wsfplugin.solrsearch;

import static org.gusdb.fgputil.FormatUtil.NL;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.Response.Status.Family;

import org.apache.log4j.Logger;
import org.eupathdb.websvccommon.wsfplugin.PluginUtilities;
import org.gusdb.fgputil.client.ClientUtil;
import org.gusdb.fgputil.client.CloseableResponse;
import org.gusdb.fgputil.json.JsonIterators;
import org.gusdb.fgputil.web.HttpMethod;
import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SiteSearchUtil {

  private static final Logger LOG = Logger.getLogger(SiteSearchUtil.class);

  private static final String LOCALHOST_PROP_KEY = "LOCALHOST";
  private static final String SERVICE_URL_PROP_KEY = "SITE_SEARCH_SERVICE_URL";

  private static final String METADATA_URI = "/categories-metadata";

  public static class SearchField {

    public final String _solrField;
    public final String _display;
    public final String _term;

    public SearchField(String name, String display, String term) {
      _solrField = name;
      _display = display;
      _term = term;
    }

    public String getSolrField() {
      return _solrField;
    }
    public String getDisplay() {
      return _display;
    }
    public String getTerm() {
      return _term;
    }

    @Override
    public String toString() {
      return new JSONObject()
        .put("solrField", _solrField)
        .put("display", _display)
        .put("term", _term)
        .toString();
    }
  }

  public static String getSiteSearchServiceUrl(PluginRequest request) throws PluginModelException {
    Map<String,String> modelProps = PluginUtilities.getWdkModel(request.getProjectId()).getProperties();
    String localhost = modelProps.get(LOCALHOST_PROP_KEY);
    String siteSearchServiceUrl = modelProps.get(SERVICE_URL_PROP_KEY);
    if (localhost == null || siteSearchServiceUrl == null) {
      throw new PluginModelException("model.prop must contain the properties: " +
          LOCALHOST_PROP_KEY + ", " + SERVICE_URL_PROP_KEY);
    }
    return localhost + siteSearchServiceUrl;
  }

  public static List<SearchField> getSearchFields(String siteSearchServiceUrl, String documentType, Optional<String> projectId) throws PluginModelException {
    // only add project ID filter for non-portal sites; for portal get back all fields
    String projectIdParam = projectId.map(proj -> "?projectId=" + proj).orElse("");
    String metadataUrl = siteSearchServiceUrl + METADATA_URI + projectIdParam;
    LOG.info("Querying site search service with: " + metadataUrl);
    String responseBody = null;
    try (CloseableResponse response = ClientUtil.makeRequest(metadataUrl,
        HttpMethod.GET, Optional.empty(), Collections.emptyMap())) {

      responseBody = ClientUtil.readSmallResponseBody(response);
      if (!response.getStatusInfo().getFamily().equals(Family.SUCCESSFUL)) {
        throw new PluginModelException("Unable to retrieve metadata from site " +
            "search service.  Request returned " + response.getStatus() +
            ". Response body:\n" + responseBody);
      }
      JSONArray docTypes = new JSONObject(responseBody).getJSONArray("documentTypes");
      List<JSONArray> fieldsJsons = JsonIterators.arrayStream(docTypes)
        .map(obj -> obj.getJSONObject())
        .filter(obj -> obj.getString("id").equals(documentType))
        .map(obj -> obj.getJSONArray("searchFields"))
        .collect(Collectors.toList());
      if (fieldsJsons.size() != 1) {
        throw new PluginModelException("Could not find unique document type with id " + documentType);
      }
      return JsonIterators.arrayStream(fieldsJsons.get(0))
        .map(obj -> obj.getJSONObject())
        .map(json -> new SearchField(
           json.getString("name"),
           json.getString("displayName"),
           json.getString("term")))
        .collect(Collectors.toList());
    }
    catch (JSONException e) {
      throw new PluginModelException("Unable to read metadata JSON: " + NL + responseBody, e);
    }
    catch (IOException e) {
      throw new PluginModelException("Could not read categories-metadata response", e);
    }
  }
}
