package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.gusdb.wsf.plugin.WsfPluginException;

public interface CommandFormatter {

  void setConfig(BlastConfig config);

  String[] formatCommand(Map<String, String> params, File seqFile, File outFile)
      throws WsfPluginException, IOException;

}
