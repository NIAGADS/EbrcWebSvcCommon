package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.eupathdb.common.model.ProjectMapper;
import org.gusdb.wdk.controller.CConstants;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.jspwrap.WdkModelBean;
import org.gusdb.wsf.plugin.AbstractPlugin;
import org.gusdb.wsf.plugin.PluginRequest;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.WsfPluginException;
import org.xml.sax.SAXException;

public abstract class AbstractBlastPlugin extends AbstractPlugin {

  // ========== Common blast params ==========
  public static final String PARAM_DATA_TYPE = "BlastDatabaseType";
  public static final String PARAM_ALGORITHM = "BlastAlgorithm";
  public static final String PARAM_SEQUENCE = "BlastQuerySequence";
  public static final String PARAM_RECORD_CLASS = "BlastRecordClass";
  public static final String PARAM_MAX_SUMMARY = "-b";
  public static final String PARAM_EVALUE = "-e";
  public static final String PARAM_FILTER = "-filter";

  // ========== Common blast return columns
  public static final String COLUMN_IDENTIFIER = "identifier";
  public static final String COLUMN_PROJECT_ID = "project_id";
  public static final String COLUMN_EVALUE_MANT = "evalue_mant";
  public static final String COLUMN_EVALUE_EXP = "evalue_exp";
  public static final String COLUMN_SCORE = "score";
  public static final String COLUMN_SUMMARY = "summary";
  public static final String COLUMN_ALIGNMENT = "alignment";

  // field definitions in the config file
  private static final String FILE_CONFIG = "blast-config.xml";

  private static final Logger logger = Logger.getLogger(AbstractBlastPlugin.class);

  // ========== member variables ==========
  private final CommandFormatter commandFormatter;
  private final ResultFormatter resultFormatter;

  private BlastConfig config;

  public AbstractBlastPlugin(CommandFormatter commandFormatter,
      ResultFormatter resultFormatter) {
    super(FILE_CONFIG);
    //logger.info("Instantiated AbstractBlastPlugin.  Ready for initialization.");
    //throw new RuntimeException("Test exception:" +
    //    " info? " + logger.isInfoEnabled() +
    //    " debug? " + logger.isDebugEnabled() +
    //    " trace? " + logger.isTraceEnabled()
    //);
    this.commandFormatter = commandFormatter;
    this.resultFormatter = resultFormatter;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wsf.plugin.AbstractPlugin#defineContextKeys()
   */
  @Override
  protected String[] defineContextKeys() {
    return new String[] { CConstants.WDK_MODEL_KEY };
  }

  @Override
  public void initialize(Map<String, Object> context) throws WsfPluginException {
    super.initialize(context);

    config = new BlastConfig(properties);
    commandFormatter.setConfig(config);
    resultFormatter.setConfig(config);

    // create project mapper
    WdkModelBean wdkModel = (WdkModelBean) context.get(CConstants.WDK_MODEL_KEY);
    try {
      ProjectMapper projectMapper = ProjectMapper.getMapper(wdkModel.getModel());
      resultFormatter.setProjectMapper(projectMapper);
    } catch (WdkModelException | SAXException | IOException
        | ParserConfigurationException ex) {
      throw new WsfPluginException(ex);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wsf.plugin.Plugin#getRequiredParameterNames()
   */
  @Override
  public String[] getRequiredParameterNames() {
    return new String[] { PARAM_DATA_TYPE, PARAM_ALGORITHM, PARAM_SEQUENCE,
        PARAM_RECORD_CLASS, PARAM_MAX_SUMMARY, PARAM_EVALUE };
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wsf.plugin.Plugin#getColumns()
   */
  @Override
  public String[] getColumns() {
    return new String[] { COLUMN_IDENTIFIER, COLUMN_PROJECT_ID,
        COLUMN_EVALUE_MANT, COLUMN_EVALUE_EXP, COLUMN_SCORE, COLUMN_SUMMARY,
        COLUMN_ALIGNMENT };
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wsf.plugin.WsfPlugin#validateParameters(java.util.Map)
   */
  @Override
  public void validateParameters(PluginRequest request)
      throws WsfPluginException {
    Map<String, String> params = request.getParams();
    for (String param : params.keySet()) {
      logger.debug("Param - name=" + param + ", value=" + params.get(param));
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wsf.plugin.Plugin#execute(org.gusdb.wsf.plugin.WsfRequest)
   */
  @Override
  public void execute(PluginRequest request, PluginResponse response)
      throws WsfPluginException {
    logger.info("Invoking " + getClass().getSimpleName() + "...");

    // create temporary files for input sequence and output report
    try {
      // get command string
      Map<String, String> params = request.getParams();
      String dbType = params.get(PARAM_DATA_TYPE);
      File seqFile = getSequenceFile(params);
      File outFile = File.createTempFile(this.getClass().getSimpleName(),
          ".out", config.getTempDir());
      String[] command = commandFormatter.formatCommand(params, seqFile,
          outFile);

      // invoke the command
      long timeout = config.getTimeout();
      StringBuffer output = new StringBuffer();
      int signal = invokeCommand(command, output, timeout);
      logger.debug("BLAST output: \n------\n" + output.toString() + "\n-----\n");

      // if the invocation succeeds, prepare the result; otherwise,
      // prepare results for failure scenario
      logger.info("Preparing the result... Output File Size is: " + outFile.length() + "\n\n");
			if (outFile.length() > 90000000) {
				logger.info("Will not prepare Result, too big BYE\n");
				response.setSignal(signal);
				response.setMessage("\n\n***** Sorry we cannot handle this big result, please repeat your BLAST using fewer results or a smaller sequence\n");
			}
			else {
				String recordClass = params.get(PARAM_RECORD_CLASS);
				String[] orderedColumns = request.getOrderedColumns();
				String message = resultFormatter.formatResult(response, orderedColumns,
					outFile, recordClass, dbType);
				logger.info("Result prepared BYE\n");
				logger.debug("signal is:" + signal + "\n");
				logger.debug("message is:" + message + "\n");

				response.setSignal(signal);
				response.setMessage(message + output.toString());
			}
      response.flush();

    } catch (IOException ex) {
				logger.error("IOException: " + ex);
      throw new WsfPluginException(ex);
    } finally {
      cleanup();
    }
  }

  private File getSequenceFile(Map<String, String> params) throws IOException, WsfPluginException {
    // get sequence and save it into the sequence file
    String sequence = params.get(PARAM_SEQUENCE).trim();
    
    // check if the input contains multiple sequences
    if (sequence.indexOf('>', 1) > 0)
      throw new WsfPluginException("Only one input sequence is allowed");
    
    File seqFile = File.createTempFile(this.getClass().getSimpleName() + "_",
        ".in", config.getTempDir());
    PrintWriter out = new PrintWriter(new FileWriter(seqFile));
    if (!sequence.startsWith(">"))
      out.println(">MySeq1");
    out.println(sequence);
    out.flush();
    out.close();
    return seqFile;
  }

  private void cleanup() {
    long todayLong = new Date().getTime();
    File tempDir = config.getTempDir();
    // remove files older than a week (500000000)
    for (File tempFile : tempDir.listFiles()) {
      if (tempFile.isFile() && tempFile.canWrite()
          && (todayLong - (tempFile.lastModified())) > 500000000) {
        logger.info("Temp file to be deleted: " + tempFile.getAbsolutePath()
            + "\n");
        tempFile.delete();
      }
    }
  }

}
