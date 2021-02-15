package org.eupathdb.websvccommon.wsfplugin.blast;

import java.util.Arrays;
import java.util.Map;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.FormatUtil.Style;
import org.gusdb.fgputil.Tuples.TwoTuple;
import org.gusdb.wsf.plugin.PluginUserException;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Encapsulates the processing and conversion of multi-blast service params from
 * their WDK question form representation into the JSON accepted by the
 * multi-blast service.
 *
 * NOTE: This is a transcription of the logic contained in:
 * 
 *     web-multi-blast/blob/main/src/lib/utils/params.ts
 * 
 * The two must be kept in sync so unexpected results are not shown in the
 * multi-blast UI and so users get the same result when they export to WDK.
 *
 * @author rdoherty
 */
public class MultiBlastServiceParams {

  private static final Logger LOG = Logger.getLogger(MultiBlastServiceParams.class);

  public static final String BLAST_DATABASE_ORGANISM_PARAM_NAME = "MultiBlastDatabaseOrganism";
  public static final String BLAST_DATABASE_TYPE_PARAM_NAME = "MultiBlastDatabaseType";
  public static final String BLAST_QUERY_SEQUENCE_PARAM_NAME = "BlastQuerySequence";
  public static final String BLAST_ALGORITHM_PARAM_NAME = "BlastAlgorithm";

  // General config for all BLAST applications
  public static final String EXPECTATION_VALUE_PARAM_NAME = "ExpectationValue";
  public static final String NUM_QUERY_RESULTS_PARAM_NAME = "NumQueryResults";
  public static final String MAX_MATCHES_QUERY_RANGE_PARAM_NAME = "MaxMatchesQueryRange";

  // General config specific to each BLAST application
  public static final String WORD_SIZE_PARAM_NAME = "WordSize";
  public static final String SCORING_MATRIX_PARAM_NAME = "ScoringMatrix";
  public static final String COMP_ADJUST_PARAM_NAME = "CompAdjust";

  // Filter and masking config
  public static final String FILTER_LOW_COMPLEX_PARAM_NAME = "FilterLowComplex";
  public static final String SOFT_MASK_PARAM_NAME = "SoftMask";
  public static final String LOWER_CASE_MASK_PARAM_NAME = "LowerCaseMask";

  // Scoring config
  public static final String GAP_COSTS_PARAM_NAME = "GapCosts";
  public static final String MATCH_MISMATCH_SCORE = "MatchMismatchScore";

  public static String[] getAllParamNames() {
    return new String[] {
      BLAST_DATABASE_TYPE_PARAM_NAME,
      BLAST_ALGORITHM_PARAM_NAME,
      BLAST_DATABASE_ORGANISM_PARAM_NAME,
      BLAST_QUERY_SEQUENCE_PARAM_NAME,
      EXPECTATION_VALUE_PARAM_NAME,
      NUM_QUERY_RESULTS_PARAM_NAME,
      MAX_MATCHES_QUERY_RANGE_PARAM_NAME,
      WORD_SIZE_PARAM_NAME,
      SCORING_MATRIX_PARAM_NAME,
      MATCH_MISMATCH_SCORE,
      GAP_COSTS_PARAM_NAME,
      COMP_ADJUST_PARAM_NAME,
      FILTER_LOW_COMPLEX_PARAM_NAME,
      SOFT_MASK_PARAM_NAME,
      LOWER_CASE_MASK_PARAM_NAME
    };
  }

  /**
   * Converts the internal values of the WDK multiblaset query params into
   * a JSON object passed to the multi-blast service to configure a new job for
   * a single input sequence.
   *
   * @param params internal values of params
   * @return json object to be passed as "config" to multi-blast service
   */
  public static JSONObject buildNewJobRequestConfigJson(Map<String, String> params) throws PluginUserException {

    LOG.info("Converting the following param values to JSON: " + FormatUtil.prettyPrint(params, Style.MULTI_LINE));

    var requestConfig = buildBaseRequestConfig(params);

    var selectedTool = params.get(BLAST_ALGORITHM_PARAM_NAME);

    var filterLowComplexityRegionsStr = params.get(FILTER_LOW_COMPLEX_PARAM_NAME);
    var filterLowComplexityRegions = !filterLowComplexityRegionsStr.equals("no filter");

    if (selectedTool.equals("blastn")) {
      var dustConfig = buildDustFilterConfig(filterLowComplexityRegions);

      var matchMismatchStr = params.get(MATCH_MISMATCH_SCORE);
      var rewardPenaltyPair = paramValueToIntPair(matchMismatchStr);
      var reward = rewardPenaltyPair.getFirst();
      var penalty = rewardPenaltyPair.getSecond();

      return requestConfig
        .put("selectedTool", selectedTool)
        .put("task", selectedTool)
        .put("dust", dustConfig)
        .put("reward", reward)
        .put("penalty", penalty);
    }

    var scoringMatrix = params.get(SCORING_MATRIX_PARAM_NAME);
    requestConfig.put("matrix", scoringMatrix);

    if (filterLowComplexityRegions) {
      var enabledSegFilterConfig = buildEnabledSegFilterConfig();
      requestConfig.put("seq", enabledSegFilterConfig);
    }

    if (selectedTool.equals("tblastx")) {
      return requestConfig
        .put("tool", selectedTool)
        .put("queryGeneticCode", 1);
    }

    var compBasedStats = params.get(COMP_ADJUST_PARAM_NAME);
    requestConfig.put("compBasedStats", compBasedStats);

    if (selectedTool.equals("blastp") || selectedTool.equals("tblastn")) {
      return requestConfig
        .put("tool", selectedTool)
        .put("task", selectedTool);
    }

    if (selectedTool.equals("blastx")) {
      return requestConfig
        .put("tool", selectedTool)
        .put("queryGeneticCode", 1);
    }

    throw new PluginUserException("The tool type '" + selectedTool + "' is unsupported");
  }

  /**
   * Converts the internal values of the WDK multiblaset query params into
   * a JSON array passed to the multi-blast service which specifies the
   * databases the job should target
   *
   * @param params internal values of params
   * @return json array to be passed as "targets" to multi-blast service
   */
  public static JSONArray buildNewJobRequestTargetJson(Map<String, String> params) {
    var organismsStr = params.get(BLAST_DATABASE_ORGANISM_PARAM_NAME);
    var targetType = params.get(BLAST_DATABASE_TYPE_PARAM_NAME);

    var organisms = organismsStr.split(",");

    var targets = Arrays.stream(organisms)
      .filter(organism -> !(organism.equals("-1") || organism.length() <= 3))
      .map(leafOrganism ->
        new JSONObject()
          .put("organism", leafOrganism)
          .put("target", leafOrganism + targetType)
      )
      .toArray();

    return new JSONArray(targets);
  }

  private static JSONObject buildBaseRequestConfig(Map<String, String> params) {
    var query = params.get(BLAST_QUERY_SEQUENCE_PARAM_NAME);
    var eValue = params.get(EXPECTATION_VALUE_PARAM_NAME);
    var numQueryResultsStr = params.get(NUM_QUERY_RESULTS_PARAM_NAME);
    var maxMatchesStr = params.get(MAX_MATCHES_QUERY_RANGE_PARAM_NAME);
    var wordSizeStr = params.get(WORD_SIZE_PARAM_NAME);
    var softMaskStr = params.get(SOFT_MASK_PARAM_NAME);
    var lowerCaseMaskStr = params.get(LOWER_CASE_MASK_PARAM_NAME);
    var gapCostsStr = params.get(GAP_COSTS_PARAM_NAME);

    var gapCostsPair = paramValueToIntPair(gapCostsStr);
    var gapOpen = gapCostsPair.getFirst();
    var gapExtend = gapCostsPair.getSecond();

    var baseConfig =
      new JSONObject()
        .put("query", query)
        .put("eValue", eValue)
        .put("maxTargetSeqs", paramValueToInt(numQueryResultsStr))
        .put("wordSize", paramValueToInt(wordSizeStr))
        .put("softMasking", paramValueToBoolean(softMaskStr))
        .put("lcaseMasking", paramValueToBoolean(lowerCaseMaskStr))
        .put("gapOpen", gapOpen)
        .put("gapExtend", gapExtend);

    var maxMatches = paramValueToInt(maxMatchesStr);

    if (maxMatches >= 1) {
      baseConfig.put("maxHSPs", paramValueToInt(maxMatchesStr));
    }

    return baseConfig;
  }

  private static JSONObject buildDustFilterConfig(boolean enable) {
    var dustFilterConfig = new JSONObject().put("enable", enable);

    if (enable) {
      dustFilterConfig.put("level", 20);
      dustFilterConfig.put("window", 64);
      dustFilterConfig.put("linker", 1);
    }

    return dustFilterConfig;
  }

  private static JSONObject buildEnabledSegFilterConfig() {
    return new JSONObject()
      .put("window", 12)
      .put("locut", 2.2)
      .put("hicut", 2.5);
  }

  private static boolean paramValueToBoolean(String paramValue) {
    return paramValue.replace("'", "").equals("true");
  }

  private static int paramValueToInt(String paramValue) {
    return Integer.parseInt(paramValue.replace("'", ""));
  }

  private static TwoTuple<Integer, Integer> paramValueToIntPair(String paramValue) {
    var pairStrValues = paramValue.replace("'", "").split(",", 2);

    return new TwoTuple<>(
      Integer.parseInt(pairStrValues[0]),
      Integer.parseInt(pairStrValues[1])
    );
  }
}
