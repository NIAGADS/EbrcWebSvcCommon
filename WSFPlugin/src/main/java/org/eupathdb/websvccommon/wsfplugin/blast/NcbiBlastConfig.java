package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.File;
import java.util.Properties;

import org.gusdb.wsf.plugin.PluginModelException;

public class NcbiBlastConfig extends BlastConfig {

  /**
   * This is the only required field in the config file.
   */
  public static final String FIELD_BLAST_PATH = "BlastPath";

  // The following properties are optional, and a default is provided for each.
  public static final String FIELD_TEMP_PATH = "TempPath";
  public static final String FIELD_EXTRA_OPTIONS = "ExtraOptions";

  // default values for the optional properties
  private static final String DEFAULT_TEMP_PATH = "/var/www/Common/tmp/blast";
  private static final String DEFAULT_EXTRA_OPTIONS = "";

  public NcbiBlastConfig(Properties properties) throws PluginModelException {
    super(properties);
  }

  public String getBlastPath() {
    return _properties.getProperty(FIELD_BLAST_PATH);
  }

  @Override
  protected void validate() throws PluginModelException {
    // check if required blastPath is specified.
    if (!_properties.containsKey(FIELD_BLAST_PATH))
      throw new PluginModelException("The required BLAST program path is not "
          + "specified in the config file.");

    // create temp path if it doesn't exist
    File tempDir = getTempDir();
    if (!tempDir.exists())
      tempDir.mkdirs();

    // timeout has to be positive
    long timeout = getTimeout();
    if (timeout < 1)
      throw new PluginModelException("Invalid timeout for blast: " + timeout
          + " seconds. The value must be a positive integer.");
  }

  public File getTempDir() {
    return new File(_properties.getProperty(FIELD_TEMP_PATH, DEFAULT_TEMP_PATH));
  }

  public String getExtraOptions() {
    return _properties.getProperty(FIELD_EXTRA_OPTIONS, DEFAULT_EXTRA_OPTIONS);
  }
}
