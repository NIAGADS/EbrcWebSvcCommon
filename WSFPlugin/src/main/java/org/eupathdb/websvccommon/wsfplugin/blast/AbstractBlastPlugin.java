package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eupathdb.common.model.ProjectMapper;
import org.eupathdb.common.service.PostValidationUserException;
import org.eupathdb.websvccommon.wsfplugin.PluginUtilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wsf.plugin.AbstractPlugin;
import org.gusdb.wsf.plugin.PluginModelException;
import org.gusdb.wsf.plugin.PluginRequest;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.PluginTimeoutException;
import org.gusdb.wsf.plugin.PluginUserException;

public abstract class AbstractBlastPlugin extends AbstractPlugin {

  public static class BlastResultProblemException extends PostValidationUserException {
    public BlastResultProblemException(String message) {
      super(message);
    }
  }

  public static final int MAX_OUTFILE_SIZE = 90000000; // 90MB

  // ========== Common blast params ==========
  public static final String PARAM_DATA_TYPE = "BlastDatabaseType";
  public static final String PARAM_ALGORITHM = "BlastAlgorithm";
  public static final String PARAM_SEQUENCE = "BlastQuerySequence";
  public static final String PARAM_RECORD_CLASS = "BlastRecordClass";
  public static final String PARAM_MAX_SUMMARY = "-b";
  public static final String PARAM_EVALUE = "-e";
  public static final String PARAM_FILTER = "-filter";

  // field definitions in the config file
  private static final String FILE_CONFIG = "multiblast-config.xml";

  private static final Logger logger = Logger.getLogger(AbstractBlastPlugin.class);

  // ========== member variables ==========
  private final NcbiBlastCommandFormatter commandFormatter;
  private final ResultFormatter resultFormatter;

  private NcbiBlastConfig config;

  public AbstractBlastPlugin(NcbiBlastCommandFormatter commandFormatter, ResultFormatter resultFormatter) {
    super(FILE_CONFIG);
    this.commandFormatter = commandFormatter;
    this.resultFormatter = resultFormatter;
  }

  @Override
  public void initialize() throws PluginModelException {
    super.initialize();

    config = new NcbiBlastConfig(properties);
    commandFormatter.setConfig(config);
    resultFormatter.setConfig(config);
  }

  @Override
  public String[] getRequiredParameterNames() {
    return new String[] { PARAM_DATA_TYPE, PARAM_ALGORITHM, PARAM_SEQUENCE, PARAM_RECORD_CLASS,
        PARAM_MAX_SUMMARY, PARAM_EVALUE };
  }

  @Override
  public String[] getColumns(PluginRequest request) {
    return resultFormatter.getDeclaredColumns();
  }

  @Override
  public void validateParameters(PluginRequest request) {
    Map<String, String> params = request.getParams();
    for (String param : params.keySet()) {
      logger.debug("Param - name=" + param + ", value=" + params.get(param));
    }
  }

  @Override
  public int execute(PluginRequest request, PluginResponse response) throws PluginModelException, PluginUserException {
    logger.info("Invoking " + getClass().getSimpleName() + "...");

    // create temporary files for input sequence and output report
    try {
      WdkModel wdkModel = PluginUtilities.getWdkModel(request);
      ProjectMapper projectMapper = ProjectMapper.getMapper(wdkModel);
      resultFormatter.setProjectMapper(projectMapper);

      // get command string
      Map<String, String> params = request.getParams();
      String dbType = params.get(PARAM_DATA_TYPE);
      File seqFile = getSequenceFile(params);
      File outFile = File.createTempFile(this.getClass().getSimpleName(), ".out", config.getTempDir());
      String[] command = commandFormatter.formatCommand(params, seqFile, outFile);

      // invoke the command
      long timeout = config.getTimeout();
      StringBuffer output = new StringBuffer();
      int signal = invokeCommand(command, output, timeout);
      logger.debug("BLAST output: \n------\n" + output.toString() + "\n-----\n");

      // if the invocation succeeds, prepare the result; otherwise,
      // prepare results for failure scenario
      logger.info("Preparing the result... Output File Size is: " + outFile.length() + "\n\n");
      if (outFile.length() > MAX_OUTFILE_SIZE) {
        logger.error("Will not prepare Result, too big BYE\n");
        //response.setMessage("\n\n***** Sorry we cannot handle this big result, please repeat your BLAST using fewer results (parameter V=B) or a smaller sequence\n");
        throw new BlastResultProblemException(
            "We're sorry, but we cannot handle a BLAST result this large (" +
            outFile.length()/1000000 + "MB).  To reduce the result size, you " +
            "could decrease V=B or the Expectation value, turn on the Low " +
            "Complexity Rilter, or decrease the number of target organisms selected.");
      }
      else {
        RecordClass recordClass = PluginUtilities.getRecordClass(request);
        logger.debug("*********recordclass is:" + recordClass + "\n");
        try (FileInputStream outFileStream = new FileInputStream(outFile)) {
          String message = resultFormatter.formatResult(response, request.getOrderedColumns(), outFileStream, recordClass, dbType, wdkModel);
          logger.info("Result prepared BYE\n");
          logger.debug("signal is:" + signal + "\n");
          logger.debug("message is:" + message + "\n");
  
          response.setMessage(message + output.toString());
        }
      }
      return signal;
    }
    catch (IOException | WdkModelException ex) {
      logger.error("IOException: " + ex);
      throw new PluginModelException(ex);
    }
    catch (PluginTimeoutException ex) {
      logger.error("PluginTimeoutException: " + ex);
      throw new BlastResultProblemException(
          "The BLAST execution has timed out.  If this issue persists, it is " +
          "likely because the input sequence was too long, or too many target " +
          "organisms were selected.");
    }
    finally {
      cleanup();
    }
  }

  private File getSequenceFile(Map<String, String> params) throws IOException, PluginUserException {
    // get sequence and save it into the sequence file
    String sequence = params.get(PARAM_SEQUENCE).trim();

    // may need to filter out certain character sequences; additional sequences should be added as needed
    sequence = sequence.replaceAll("&#65532;", "");

    // check if the input contains multiple sequences
    if (sequence.indexOf('>', 1) > -1)
      throw new PluginUserException("Only one input sequence is allowed");

    File seqFile = File.createTempFile(this.getClass().getSimpleName() + "_", ".in", config.getTempDir());
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
      if (tempFile.isFile() && tempFile.canWrite() && (todayLong - (tempFile.lastModified())) > 500000000) {
        logger.info("Temp file to be deleted: " + tempFile.getAbsolutePath() + "\n");
        tempFile.delete();
      }
    }
  }

}
