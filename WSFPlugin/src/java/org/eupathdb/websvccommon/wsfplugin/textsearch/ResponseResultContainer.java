/**
 * 
 */
package org.eupathdb.websvccommon.wsfplugin.textsearch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.WsfException;
import org.gusdb.wsf.plugin.WsfPluginException;

import static org.eupathdb.websvccommon.wsfplugin.textsearch.AbstractOracleTextSearchPlugin.*;

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
  public void addResult(SearchResult result) throws WsfException {
    String sourceId = result.getSourceId();

    // convert the result to a String[] array
    String[] array = new String[columnOrders.size()];
    for (String column : columnOrders.keySet()) {
      if (column.equals(COLUMN_DATASETS)) {
        array[columnOrders.get(COLUMN_DATASETS)] = result.getFieldsMatched();
      } else if (column.equals(COLUMN_PROJECT_ID)) {
        array[columnOrders.get(COLUMN_PROJECT_ID)] = result.getProjectId();
      } else if (column.equals(COLUMN_RECORD_ID)) {
        array[columnOrders.get(COLUMN_RECORD_ID)] = result.getSourceId();
      } else if (column.equals(COLUMN_MAX_SCORE)) {
        array[columnOrders.get(COLUMN_MAX_SCORE)] = Float.toString(result.getMaxScore());
      } else {
        throw new WsfPluginException("Unknown column: " + column);
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
