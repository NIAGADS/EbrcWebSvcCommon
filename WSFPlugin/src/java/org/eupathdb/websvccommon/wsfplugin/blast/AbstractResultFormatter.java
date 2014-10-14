package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.eupathdb.common.model.ProjectMapper;
import org.eupathdb.websvccommon.wsfplugin.EuPathServiceException;

public abstract class AbstractResultFormatter implements ResultFormatter {

  protected static final Pattern SUBJECT_PATTERN = Pattern.compile("Sbjct\\s\\s+(\\d+)\\s+\\S+\\s+(\\d+)");

  private static final String SCORE_REGEX = "(\\d+)\\s+\\S+$";
  private static final String EVALUE_REGEX = "\\s+(\\S+)$";

  private static final Logger logger = Logger.getLogger(AbstractResultFormatter.class);

  private ProjectMapper projectMapper;
  private BlastConfig config;

  @Override
  public void setProjectMapper(ProjectMapper projectMapper) {
    this.projectMapper = projectMapper;
  }

  @Override
  public void setConfig(BlastConfig config) {
    this.config = config;
  }

  protected String getField(String defline, int[] location) {
    return defline.substring(location[0], location[1]);
  }

  protected int[] findSourceId(String defline) {
    return findField(defline, config.getSourceIdRegex());
  }

  protected int[] findOrganism(String defline) {
    return findField(defline, config.getOrganismRegex());
  }

  protected int[] findScore(String summaryLine) {
    return findField(summaryLine, SCORE_REGEX);
  }
  
  protected int[] findEvalue(String summaryLine) {
    return findField(summaryLine, EVALUE_REGEX);
  }

  private int[] findField(String defline, String regex) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(defline);
    if (matcher.find()) {
      // the match is located at group of the given index
      return new int[] { matcher.start(1), matcher.end(1) };
    } else {
      logger.warn("Couldn't find pattern \"" + regex + "\" in defline \""
          + defline + "\"");
      return null;
    }
  }

  protected String getIdUrl(String recordClass, String projectId,
      String sourceId) throws EuPathServiceException {
    try {
    String url = "showRecord.do?name=" + recordClass + "&project_id="
        + URLEncoder.encode(projectId, "UTF-8") + "&source_id="
        + URLEncoder.encode(sourceId, "UTF-8");
    return url;
    } catch (UnsupportedEncodingException ex) {
      throw new EuPathServiceException(ex);
    }
  }

  /**
   * Insert a given url to the specified location, and use the html link tag to
   * wrap around the content at the location.
   * 
   * @param content
   * @param location
   * @param url
   * @return
   */
  protected String insertUrl(String content, int[] location, String url) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(content.substring(0, location[0]));
    buffer.append("<a href=\"").append(url).append("\">");
    buffer.append(content.substring(location[0], location[1]));
    buffer.append("</a>").append(content.substring(location[1]));
    return buffer.toString();
  }
  
  protected String getProject(String organism) throws SQLException {
    return projectMapper.getProjectByOrganism(organism);
  }
  
  protected String getBaseUrl(String projectId) {
    return projectMapper.getBaseUrl(projectId);
  }
}
