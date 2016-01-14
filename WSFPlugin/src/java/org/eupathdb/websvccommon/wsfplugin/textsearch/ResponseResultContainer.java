/**
 * 
 */
package org.eupathdb.websvccommon.wsfplugin.textsearch;

import static org.eupathdb.websvccommon.wsfplugin.textsearch.AbstractOracleTextSearchPlugin.COLUMN_DATASETS;
import static org.eupathdb.websvccommon.wsfplugin.textsearch.AbstractOracleTextSearchPlugin.COLUMN_MAX_SCORE;
import static org.eupathdb.websvccommon.wsfplugin.textsearch.AbstractOracleTextSearchPlugin.COLUMN_RECORD_ID;
import static org.eupathdb.websvccommon.wsfplugin.textsearch.AbstractOracleTextSearchPlugin.COLUMN_PROJECT_ID;
import static org.eupathdb.websvccommon.wsfplugin.textsearch.AbstractOracleTextSearchPlugin.COLUMN_GENE_SOURCE_ID;
import static org.eupathdb.websvccommon.wsfplugin.textsearch.AbstractOracleTextSearchPlugin.COLUMN_MATCHED_RESULT;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.PluginUserException;

/**
 * @author jerric
 * 
 */
public class ResponseResultContainer implements ResultContainer {

  private final PluginResponse response;
  private final Map<String, Integer> columnOrders;
  private final Set<String> sourceIds;

  public ResponseResultContainer(PluginResponse response,
      String[] orderedColumns) {
    this.response = response;
    this.sourceIds = new HashSet<>();
    this.columnOrders = new HashMap<>(orderedColumns.length);
    for (int i = 0; i < orderedColumns.length; i++) {
      columnOrders.put(orderedColumns[i], i);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.eupathdb.websvccommon.wsfplugin.textsearch.ResultContainer#addResult
   * (org.eupathdb.websvccommon.wsfplugin.textsearch.SearchResult)
   */
  @Override
  public void addResult(SearchResult result) throws PluginModelException, PluginUserException {
    String sourceId = result.getSourceId();

    // convert the result to a String[] array
    String[] array = new String[columnOrders.size()];
    for (String column : columnOrders.keySet()) {
      if (column.equals(COLUMN_DATASETS)) {
        array[columnOrders.get(COLUMN_DATASETS)] = result.getFieldsMatched();
      } else if (column.equals(COLUMN_RECORD_ID)) {
        array[columnOrders.get(COLUMN_RECORD_ID)] = result.getSourceId();
      } else if (column.equals(COLUMN_MAX_SCORE)) {
        array[columnOrders.get(COLUMN_MAX_SCORE)] = Float.toString(result.getMaxScore());
      } else if (column.equals(COLUMN_GENE_SOURCE_ID)) {
        array[columnOrders.get(COLUMN_GENE_SOURCE_ID)] = result.getGeneSourceId();
      } else if (column.equals(COLUMN_PROJECT_ID)) {
        array[columnOrders.get(COLUMN_PROJECT_ID)] = result.getGeneSourceId();
      } else if (column.equals(COLUMN_MATCHED_RESULT)) {
	  array[columnOrders.get(COLUMN_MATCHED_RESULT)] = new String("Y");
      } else {
        throw new PluginModelException("Unknown column: " + column);
      }
    }

    // add array to response
    response.addRow(array);
    sourceIds.add(sourceId);
  }

  @Override
  public boolean hasResult(String sourceId) {
    return sourceIds.contains(sourceId);
  }

}
