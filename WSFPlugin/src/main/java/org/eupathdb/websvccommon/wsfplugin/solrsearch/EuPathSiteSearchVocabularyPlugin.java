package org.eupathdb.websvccommon.wsfplugin.solrsearch;

import static org.eupathdb.websvccommon.wsfplugin.solrsearch.SiteSearchUtil.getSearchFields;
import static org.eupathdb.websvccommon.wsfplugin.solrsearch.SiteSearchUtil.getSiteSearchServiceUrl;

import java.util.List;
import java.util.Optional;

import org.apache.log4j.Logger;
import org.eupathdb.websvccommon.wsfplugin.PluginUtilities;
import org.eupathdb.websvccommon.wsfplugin.solrsearch.SiteSearchUtil.SearchField;
import org.gusdb.wsf.plugin.AbstractPlugin;
import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginRequest;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.PluginUserException;

public class EuPathSiteSearchVocabularyPlugin extends AbstractPlugin {

  private static final Logger LOG = Logger.getLogger(EuPathSiteSearchVocabularyPlugin.class);

  @Override
  public String[] getRequiredParameterNames() {
    return new String[]{};
  }

  @Override
  public String[] getColumns(PluginRequest request) {
    return new String[]{ "internal", "term", "display" };
  }

  @Override
  public void validateParameters(PluginRequest request) throws PluginModelException, PluginUserException {
    // no params to validate
  }

  @Override
  protected int execute(PluginRequest request, PluginResponse response)
      throws PluginModelException, PluginUserException {
    LOG.debug("Executing " + EuPathSiteSearchVocabularyPlugin.class.getSimpleName() + "...");
    String serviceUrl = getSiteSearchServiceUrl(request);
    String docType = getRequestedDocumentType(request);
    List<SearchField> fields = getSearchFields(serviceUrl, docType, getProjectIdForFilter(request.getProjectId()));
    for (SearchField field : fields) {
      LOG.debug("Adding response row: " + field);
      response.addRow(new String[] { field.getTerm(), field.getTerm(), field.getDisplay() });
    }
    return 0;
  }

  protected String getRequestedDocumentType(PluginRequest request) throws PluginModelException {
    return PluginUtilities.getRecordClass(request).getUrlSegment();
  }

  protected Optional<String> getProjectIdForFilter(String projectId) {
    return Optional.of(projectId);
  }

}
