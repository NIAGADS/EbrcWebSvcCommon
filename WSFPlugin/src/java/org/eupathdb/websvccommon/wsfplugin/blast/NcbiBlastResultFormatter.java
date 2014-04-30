package org.eupathdb.websvccommon.wsfplugin.blast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

import org.apache.log4j.Logger;
import org.eupathdb.common.model.view.BlastSummaryViewHandler;
import org.eupathdb.websvccommon.wsfplugin.EuPathServiceException;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.WsfPluginException;

public class NcbiBlastResultFormatter extends AbstractResultFormatter {

  @SuppressWarnings("unused")
  private static final Logger logger = Logger.getLogger(NcbiBlastResultFormatter.class);

  private static final String DB_TYPE_GENOME = "Genome";

  @Override
  public String formatResult(PluginResponse response, String[] orderedColumns,
      File outFile, String recordClass, String dbType)
      throws WsfPluginException {

    // read and parse the output
    StringBuilder content = new StringBuilder();
    Map<String, String> summaries = new LinkedHashMap<>();
    String line;
    try {
      BufferedReader reader = new BufferedReader(new FileReader(outFile));
      boolean inSummary = false, inAlignment = false;
      StringBuilder alignment = new StringBuilder();
      while ((line = reader.readLine()) != null) {
        String lineTrimmed = line.trim();
        if (inSummary) { // in summary section
          if (lineTrimmed.length() == 0) {
            // found the end of summary section, no need to output empty line,
            // since it's already been written to the content.
            inSummary = false;
          } else {
            // get source id, and store the summary line for later process,
            // since
            // some of the info here might be truncated, and can only be
            // processed
            // with the info from the correlated alignment section.
            String sourceId = getField(line, findSourceId(line));
            summaries.put(sourceId, lineTrimmed);
          }
        } else if (inAlignment) {
          if (lineTrimmed.startsWith("Database:")) { // end of alignment section
            inAlignment = false;
            // process previous alignment
            processAlignment(response, orderedColumns, recordClass, dbType,
                summaries, alignment.toString());
            content.append(line).append(newline);
          } else {
            if (line.startsWith(">")) { // start of a new alignment
              // process previous alignment
              processAlignment(response, orderedColumns, recordClass, dbType,
                  summaries, alignment.toString());
              alignment = new StringBuilder();
            }
            alignment.append(line).append(newline);
          }
        } else { // not in summary nor in alignment
          if (lineTrimmed.startsWith("Sequences producing significant alignments")) {
            // found the start of the summary section
            inSummary = true;
            content.append(newline + BlastSummaryViewHandler.MACRO_SUMMARY + newline + newline);
            // read and skip an empty line
            reader.readLine();
          } else if (line.startsWith(">")) {
            // found the first alignment section
            inAlignment = true;
            content.append(newline + BlastSummaryViewHandler.MACRO_ALIGNMENT + newline + newline);
            // add the line to the alignment
            alignment.append(line).append(newline);
          } else {
            content.append(line).append(newline);
          }
        }
      }
      reader.close();
    } catch (IOException ex) {
      throw new EuPathServiceException(ex);
    }
    return content.toString();
  }

  private void processAlignment(PluginResponse response, String[] columns,
      String recordClass, String dbType, Map<String, String> summaries,
      String alignment) throws WsfPluginException {
    try {
      // get the defline, and get organism from it
      String defline = alignment.substring(0, alignment.indexOf("Length = "));
			String organism = "none";
			try { //Ortho does not have organism info in defline
					organism = getField(defline, findOrganism(defline));
			}
			catch (NullPointerException e){}
      String projectId = getProject(organism);

      // get the source id in the alignment, and insert a link there
      int[] sourceIdLocation = findSourceId(alignment);
      String sourceId = getField(defline, sourceIdLocation);
      String idUrl = getIdUrl(recordClass, projectId, sourceId);
      alignment = insertUrl(alignment, sourceIdLocation, idUrl);

      // get score and e-value from summary;
      String summary = summaries.get(sourceId);
      String evalue = getField(summary, findEvalue(summary));
      int score = Double.valueOf(getField(summary, findScore(summary))).intValue();

      // insert id url into the summary
      summary = insertUrl(summary, findSourceId(summary), idUrl);

      // insert the gbrowse link if the DB type is genome
      if (dbType != null && dbType.equals(DB_TYPE_GENOME))
        alignment = insertGbrowseLink(alignment, projectId, sourceId);

      // format and write the row
      String[] row = formatRow(columns, projectId, sourceId, summary,
          alignment, evalue, score);
      response.addRow(row);
    } catch (SQLException ex) {
      throw new EuPathServiceException(ex);
    }
  }

  private String insertGbrowseLink(String alignment, String projectId,
    String sourceId) {
		//logger.debug("insertGBrowseLink: alignment: ********\n" + alignment + "\n*******\n");
    StringBuilder buffer = new StringBuilder();
    String[] pieces = alignment.split("Strand =");
    for (String piece : pieces) {
      if (buffer.length() > 0)
        buffer.append("Strand = ");
      Matcher matcher = SUBJECT_PATTERN.matcher(piece);
      int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
      while (matcher.find()) {
        int start = Integer.valueOf(matcher.group(1));
        int end = Integer.valueOf(matcher.group(2));
        if (min > start)
          min = start;
        if (min > end)
          min = end;
        if (max < start)
          max = start;
        if (max < end)
          max = end;
      }
      // check if any subject has been found
      if (min <= max) {
        String gb_url = getBaseUrl(projectId);
        gb_url += "/cgi-bin/gbrowse/" + projectId.toLowerCase() + "/?name="
            + sourceId + ":" + min + "-" + max;
        buffer.append("\n<a href=\"" + gb_url + "\"> <B><font color=\"red\">"
            + "Link to Genome Browser</font></B></a>,   Strand = ");
      } else if (buffer.length() > 0)
        buffer.append("Strand = ");
      buffer.append(piece);
    }
    return buffer.toString();
  }

  private String[] formatRow(String[] columns, String projectId,
      String sourceId, String summary, String alignment, String evalue,
      int score) throws EuPathServiceException {
    String[] evalueParts = evalue.split("e");
    String evalueExp = (evalueParts.length == 2) ? evalueParts[1] : "0";
    String evalueMant = evalueParts[0];
    // sometimes the mant part is empty if the blast score is very high, assign a default 1.
    if (evalueMant.length() == 0) evalueMant = "1";
    String[] row = new String[columns.length];
    for (int i = 0; i < columns.length; i++) {
      if (columns[i].equals(AbstractBlastPlugin.COLUMN_ALIGNMENT)) {
        row[i] = alignment;
      } else if (columns[i].equals(AbstractBlastPlugin.COLUMN_EVALUE_EXP)) {
        row[i] = evalueExp;
      } else if (columns[i].equals(AbstractBlastPlugin.COLUMN_EVALUE_MANT)) {
        row[i] = evalueMant;
      } else if (columns[i].equals(AbstractBlastPlugin.COLUMN_IDENTIFIER)) {
        row[i] = sourceId;
      } else if (columns[i].equals(AbstractBlastPlugin.COLUMN_PROJECT_ID)) {
        row[i] = projectId;
      } else if (columns[i].equals(AbstractBlastPlugin.COLUMN_SCORE)) {
        row[i] = Integer.toString(score);
      } else if (columns[i].equals(AbstractBlastPlugin.COLUMN_SUMMARY)) {
        row[i] = summary;
      } else {
        throw new EuPathServiceException("Unsupported blast result column: "
            + columns[i]);
      }
    }
    return row;
  }
}
