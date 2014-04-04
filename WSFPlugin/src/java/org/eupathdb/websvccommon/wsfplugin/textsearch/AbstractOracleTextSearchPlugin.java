/**
 * KeywordSearchPlugin -- text search using Oracle Text
 */
package org.eupathdb.websvccommon.wsfplugin.textsearch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.eupathdb.websvccommon.wsfplugin.EuPathServiceException;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.dbms.ConnectionContainer;
import org.gusdb.wsf.plugin.AbstractPlugin;
import org.gusdb.wsf.plugin.PluginRequest;
import org.gusdb.wsf.plugin.WsfPluginException;

/**
 * @author John I
 * @created Nov 16, 2008
 */
public abstract class AbstractOracleTextSearchPlugin extends AbstractPlugin {

  // required parameter definition
  public static final String PARAM_TEXT_EXPRESSION = "text_expression";
  public static final String PARAM_DATASETS = "text_fields";
  public static final String PARAM_WDK_RECORD_TYPE = "wdk_record_type";

  public static final String PARAM_DETAIL_TABLE = "detail_table";
  public static final String PARAM_PRIMARY_KEY_COLUMN = "primary_key_column";
  public static final String PARAM_PROJECT_ID = "project_id";

  public static final String COLUMN_RECORD_ID = "RecordID";
  public static final String COLUMN_PROJECT_ID = "ProjectId";
  public static final String COLUMN_DATASETS = "Datasets";
  public static final String COLUMN_MAX_SCORE = "MaxScore";

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wsf.WsfPlugin#getColumns()
   */
  @Override
  public String[] getColumns() {
    return new String[] { COLUMN_RECORD_ID, COLUMN_PROJECT_ID, COLUMN_DATASETS,
        COLUMN_MAX_SCORE };
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wsf.WsfPlugin#getRequiredParameters()
   */
  @Override
  public String[] getRequiredParameterNames() {
    return new String[] { PARAM_TEXT_EXPRESSION, PARAM_DATASETS };
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wsf.plugin.WsfPlugin#validateParameters(java.util.Map)
   */
  @Override
  public void validateParameters(PluginRequest request)
      throws WsfPluginException {
    // do nothing in this plugin
  }

  protected String transformQueryString(String queryExpression) {

    // transform the user's search string onto an expression suitable for
    // passing to the Oracle Text CONTAINS() function: drop occurrences of AND
    // and
    // OR, escape anything else with curly braces (to avert errors from the
    // database if any is on the long list of Oracle Text keywords), and (if
    // it's
    // a multi-word phrase) use NEAR and ACCUM to give a higher score when all
    // terms are near each other
    // e.g. "calcium binding" becomes
    // "({calcium} NEAR {binding}) * 1.0 OR ({calcium} ACCUM {binding}) * 0.1"

    double nearWeight = 1;
    double accumWeight = .1;
    String transformed;

    String trimmed = queryExpression.trim().replaceAll("'", "").replaceAll(
        "[-&|~,=;%_]", "\\\\$0");

    ArrayList<String> tokenized = tokenizer(trimmed);
    if (tokenized.size() > 1) {
      transformed = "(" + join(tokenized, " NEAR ") + ") * " + nearWeight
          + " OR (" + join(tokenized, " ACCUM ") + ") * " + accumWeight;
    } else if (tokenized.size() == 1) {
      transformed = tokenized.get(0);
    } else {
      transformed = wildcarded(trimmed);
    }

    return transformed;
  }

  private static String wildcarded(String queryExpression) {

    String wildcarded = queryExpression.replaceAll("\\*", "%");
    if (wildcarded.equals(queryExpression)) {
      // no wildcard
      return ("{" + queryExpression + "}");
    } else {
      return (wildcarded);
    }

  }

  private static ArrayList<String> tokenizer(String input) {

    ArrayList<String> tokenized = new ArrayList<String>();

    boolean insideQuotes = false;
    for (String quoteChunk : input.split("\"")) {
      if (insideQuotes && quoteChunk.length() > 0) {
        tokenized.add(wildcarded(quoteChunk));
      } else {
        for (String spaceChunk : quoteChunk.split(" ")) {
          if (spaceChunk.length() > 0
              && !spaceChunk.toLowerCase().equals("and")
              && !spaceChunk.toLowerCase().equals("or")) {
            tokenized.add(wildcarded(spaceChunk));
          }
        }
      }
      insideQuotes = !insideQuotes;
    }

    return tokenized;
  }

  private static String join(ArrayList<String> parts, String delimiter) {
    boolean notFirstChunk = false;

    StringBuilder conjunction = new StringBuilder();

    for (String part : parts) {
      if (notFirstChunk) {
        conjunction.append(delimiter);
      } else
        notFirstChunk = true;

      conjunction.append(part);
    }

    return conjunction.toString();
  }

  // private PreparedStatement getValidationQuery() throws WsfServiceException {
  // String sql = new String("select attrs.source_id, attrs.project_id \n"
  // + "from ApidbTuning.GeneId alias, ApidbTuning.GeneAttributes attrs \n"
  // + "where alias.Id = ? \n"
  // + "  and alias.gene = attrs.source_id \n"
  // + "  and alias.unique_mapping = 1 \n"
  // + "  and attrs.project_id = ? \n"
  // + "  and ? like '%' || attrs.organism || '%'");
  //
  // WdkModelBean wdkModel = (WdkModelBean) this.context
  // .get(CConstants.WDK_MODEL_KEY);
  // DBPlatform platform = wdkModel.getModel().getQueryPlatform();
  // DataSource dataSource = platform.getDataSource();
  //
  // try {
  // return SqlUtils.getPreparedStatement(dataSource, sql);
  // } catch (SQLException ex) {
  // throw new WsfServiceException(ex);
  // }
  // }

  protected void textSearch(ResultContainer results, PreparedStatement query,
			    String primaryKeyColumn, String sql, String name) throws WsfPluginException {
    ResultSet rs = null;
    try {
      logger.info("about to execute text-search query \"" + name
		  + "\" (set org.gusdb logging to \"debug\" to see its text)");
      query.setFetchSize(100);
      // rs = query.executeQuery();
      rs = SqlUtils.executePreparedQuery(query, sql, name);
      logger.info("finshed execute");
      while (rs.next()) {
        String sourceId = rs.getString(primaryKeyColumn);

        if (results.hasResult(sourceId)) {
          throw new WsfPluginException("duplicate sourceId " + sourceId);
        }

        SearchResult match = getSearchResults(rs, sourceId);
        results.addResult(match);
      }
      logger.info("finished fetching rows");
    } catch (SQLException ex) {
      logger.info("caught Exception " + ex.getMessage());
      ex.printStackTrace();
      String message;
      if (ex.getMessage().indexOf("DRG-51030") >= 0) {
        // DRG-51030: wildcard query expansion resulted in too many terms
        message = new String("Search term with wildcard (asterisk) characters "
            + "matches too many keywords. Please include more non-wildcard "
            + "characters.");
      } else if (ex.getMessage().indexOf("ORA-01460") >= 0) {
        // ORA-01460: unimplemented or unreasonable conversion requested
        // it's unimplemented; it's unreasonable; it's outrageous, egregious,
        // preposterous!
        message = new String(
            "Search term is too long. Please try again with a shorter text term.");
      } else {
        message = ex.getMessage();
      }
      throw new WsfPluginException(message, ex);
    } catch (Exception ex) {
      logger.info("caught Exception " + ex.getMessage());
      ex.printStackTrace();
      throw new WsfPluginException(ex);
    } finally {
      SqlUtils.closeQuietly(rs);
    }
  }

  private SearchResult getSearchResults(ResultSet rs, String sourceId)
      throws SQLException {
    return new SearchResult(rs.getString("project_id"), sourceId,
        rs.getFloat("max_score"), rs.getString("fields_matched"));
  }

  protected Connection getDbConnection(String containerKey, String connectionKey)
      throws SQLException, EuPathServiceException, WdkModelException {
    ConnectionContainer container = (ConnectionContainer) context.get(containerKey);
    if (container == null)
      throw new EuPathServiceException("The container cannot be found in the "
          + "context with key: " + containerKey + ". Please check if the "
          + "container is declared in the context.");

    return container.getConnection(connectionKey);
  }
}