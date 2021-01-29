package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.InputStream;

import org.eupathdb.common.model.ProjectMapper;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.PluginUserException;

public interface ResultFormatter {

  void setConfig(BlastConfig config);

  void setProjectMapper(ProjectMapper projectMapper);

  String[] getDeclaredColumns();

  /**
   * Format the result into the response, and return the message which can be
   * passed to the client.
   * 
   * @param response
   * @param orderedColumns
   * @param resultStream
   * @param recordClass
   * @param dbType
   * @return
   * @throws WsfException 
   */
  String formatResult(PluginResponse response, String[] orderedColumns,
      InputStream resultStream, RecordClass recordClass, String dbType, WdkModel wdkModel)
      throws PluginModelException, PluginUserException;

}
