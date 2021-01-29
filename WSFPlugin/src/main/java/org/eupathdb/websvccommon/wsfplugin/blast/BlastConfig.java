package org.eupathdb.websvccommon.wsfplugin.blast;

import java.util.Properties;

import org.gusdb.wsf.plugin.PluginModelException;

public class BlastConfig {

  // The following properties are optional, and a default is provided for each.
  public static final String FIELD_TIMEOUT = "Timeout";
  public static final String FIELD_IDENTIFIER_REGEX = "IdentifierRegex";
  public static final String FIELD_ORGANISM_REGEX = "OrganismRegex";
  public static final String FIELD_GENE_REGEX = "GeneRegex";

  // default values for the optional properties
  private static final String DEFAULT_TIMEOUT = "300";
  private static final String DEFAULT_IDENTIFIER_REGEX = "^>*(?:[^\\|]*\\|)?(\\S+)";
  private static final String DEFAULT_ORGANISM_REGEX = "\\|\\s*organism=([^|\\s]+)";
  private static final String DEFAULT_GENE_REGEX = "\\|\\s*gene=([^|\\s]+)";

  protected final Properties _properties;

  /**
   * @throws PluginModelException  
   */
  public BlastConfig(Properties properties) throws PluginModelException {
    _properties = properties;
    validate();
  }

  /**
   * @throws PluginModelException  
   */
  protected void validate() throws PluginModelException {
    // subclasses may implement
  }

  public long getTimeout() {
    return Long.valueOf(_properties.getProperty(FIELD_TIMEOUT, DEFAULT_TIMEOUT));
  }

  public String getSourceIdRegex() {
    return _properties.getProperty(FIELD_IDENTIFIER_REGEX, DEFAULT_IDENTIFIER_REGEX);
  }

  public String getOrganismRegex() {
    return _properties.getProperty(FIELD_ORGANISM_REGEX, DEFAULT_ORGANISM_REGEX);
  }

  public String getGeneRegex() {
    return _properties.getProperty(FIELD_GENE_REGEX, DEFAULT_GENE_REGEX);
  }

}
