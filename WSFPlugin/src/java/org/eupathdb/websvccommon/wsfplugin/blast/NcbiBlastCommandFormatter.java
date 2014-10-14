package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginUserException;

public abstract class NcbiBlastCommandFormatter implements CommandFormatter {

  @SuppressWarnings("unused")
  private static final Logger logger = Logger.getLogger(NcbiBlastCommandFormatter.class);

  protected BlastConfig config;

  public abstract String getBlastDatabase(Map<String, String> params)
      throws PluginUserException, PluginModelException;

  @Override
  public void setConfig(BlastConfig config) {
    this.config = config;
  }

  @Override
  public String[] formatCommand(Map<String, String> params, File seqFile,
      File outFile) throws PluginUserException, PluginModelException {
    // now prepare the commandline
    List<String> cmds = new ArrayList<String>();
    //cmds.add(config.getBlastPath() + "blastall");

    // get the algorithm
    String blastApp = params.remove(AbstractBlastPlugin.PARAM_ALGORITHM);
    //cmds.add("-p");
    //cmds.add(blastApp);

		// Oct 2014: using new blast: blast+
    cmds.add(config.getBlastPath() + blastApp);

    // get the blast database
    String blastDbs = getBlastDatabase(params);
    cmds.add("-db");
    cmds.add(blastDbs);

    // add the input and output file
    cmds.add("-query");
    cmds.add(seqFile.getAbsolutePath());
    cmds.add("-out");
    cmds.add(outFile.getAbsolutePath());

 // set to use 4 cores
    cmds.add("-num_threads");
    cmds.add("4");

    for (String paramName : params.keySet()) {
      if (paramName.equals(AbstractBlastPlugin.PARAM_EVALUE)) {
        cmds.add("-evalue");
        cmds.add(params.get(paramName));
      } else if (paramName.equals(AbstractBlastPlugin.PARAM_MAX_SUMMARY)) {
        String alignments = params.get(paramName);
        cmds.add("-num_alignments");
        cmds.add(alignments);
        cmds.add("-num_descriptions");
        cmds.add(alignments);
      } else if (paramName.equals(AbstractBlastPlugin.PARAM_FILTER)) {   
				if (params.get(paramName).equals("yes")) {                 //default is no filtering
					if ( blastApp.equals("blastn") ) cmds.add("-dust");
					else cmds.add("-seg");
					cmds.add("yes"); 
				}
      }
    }

    String[] cmdArray = new String[cmds.size()];
    cmds.toArray(cmdArray);
    return cmdArray;
  }

}
