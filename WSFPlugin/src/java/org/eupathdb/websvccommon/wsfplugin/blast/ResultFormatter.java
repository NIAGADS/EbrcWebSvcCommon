package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.File;

import org.eupathdb.common.model.ProjectMapper;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.common.WsfException;

public interface ResultFormatter {

  static final String newline = System.getProperty("line.separator");

  void setConfig(BlastConfig config);

  void setProjectMapper(ProjectMapper projectMapper);

  /**
   * Format the result into the response, and return the message which can be
   * passed to the client.
   * 
   * @param response
   * @param orderedColumns
   * @param outFile
   * @param recordClass
   * @param dbType
   * @return
   * @throws WsfException 
   */
  String formatResult(PluginResponse response, String[] orderedColumns,
      File outFile, String recordClass, String dbType)
      throws WsfException;
}
