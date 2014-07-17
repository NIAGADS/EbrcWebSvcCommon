package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.File;
import java.util.Map;

import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginUserException;

public interface CommandFormatter {

  void setConfig(BlastConfig config);

  String[] formatCommand(Map<String, String> params, File seqFile, File outFile)
      throws PluginModelException, PluginUserException;

}
