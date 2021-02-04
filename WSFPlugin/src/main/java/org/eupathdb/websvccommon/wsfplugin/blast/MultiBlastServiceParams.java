package org.eupathdb.websvccommon.wsfplugin.blast;

import java.util.Map;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.FormatUtil.Style;
import org.jfree.util.Log;
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

  public static final String BLAST_DATABASE_ORGANISM_PARAM_NAME = "BlastDatabaseOrganism";
  public static final String BLAST_DATABASE_TYPE_PARAM_NAME = "BlastDatabaseType";
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
   * a JSON object passed to the multi-blast service to create a new job for
   * a single input sequence.
   *
   * @param params internal values of params
   * @return json object to be fed to multi-blast service
   */
  public static JSONObject buildNewJobRequestJson(Map<String, String> params) {

    LOG.info("Converting the following param values to JSON: " + FormatUtil.prettyPrint(params, Style.MULTI_LINE));

    /* TODO Convert the typescript code below to Java in order to generate a
     * service-compatible json request object; required constants should already
     * be defined above.

    export function paramValuesToBlastConfig(
      rawParamValues: Record<string, string>
    ): IoBlastConfig {
      const paramValues = mapValues(rawParamValues, (paramValue) =>
        paramValue.replace(/ \(default\)$/, '')
      );

      const {
        [BLAST_QUERY_SEQUENCE_PARAM_NAME]: query,
        [BLAST_ALGORITHM_PARAM_NAME]: selectedTool,
        [EXPECTATION_VALUE_PARAM_NAME]: eValue,
        [NUM_QUERY_RESULTS_PARAM_NAME]: numQueryResultsStr,
        [MAX_MATCHES_QUERY_RANGE_PARAM_NAME]: maxMatchesStr,
        [WORD_SIZE_PARAM_NAME]: wordSizeStr,
        [SCORING_MATRIX_PARAM_NAME]: scoringMatrixStr,
        [COMP_ADJUST_PARAM_NAME]: compBasedStatsStr,
        [FILTER_LOW_COMPLEX_PARAM_NAME]: filterLowComplexityRegionsStr,
        [SOFT_MASK_PARAM_NAME]: softMaskStr,
        [LOWER_CASE_MASK_PARAM_NAME]: lowerCaseMaskStr,
        [GAP_COSTS_PARAM_NAME]: gapCostsStr,
        [MATCH_MISMATCH_SCORE]: matchMismatchStr,
      } = paramValues;

      const [gapOpen, gapExtend] = (gapCostsStr ?? '').split(',').map(Number);

      const maxHSPsConfig =
        Number(maxMatchesStr) >= 1 ? { maxHSPs: Number(maxMatchesStr) } : {};

      const baseConfig = {
        query,
        eValue,
        maxTargetSeqs: Number(numQueryResultsStr),
        wordSize: Number(wordSizeStr),
        softMasking: Boolean(softMaskStr),
        lcaseMasking: Boolean(lowerCaseMaskStr),
        gapOpen,
        gapExtend,
        outFormat: {
          format: 'single-file-json',
        },
        ...maxHSPsConfig,
      } as const;

      const compBasedStats =
        compBasedStatsStr === 'Conditional compositional score matrix adjustment'
          ? 'conditional-comp-based-score-adjustment'
          : compBasedStatsStr === 'No adjustment'
          ? 'none'
          : compBasedStatsStr === 'Composition-based statistics'
          ? 'comp-based-stats'
          : 'unconditional-comp-based-score-adjustment';

      const filterLowComplexityRegions =
        filterLowComplexityRegionsStr !== 'no filter';

      const dustConfig = !filterLowComplexityRegions
        ? {
            dust: {
              enable: false,
            },
          }
        : {
            dust: {
              enable: true,
              level: 20,
              window: 64,
              linker: 1,
            },
          };

      const segConfig = !filterLowComplexityRegions
        ? {}
        : {
            seg: { window: 12, locut: 2.2, hicut: 2.5 },
          };

      const [reward, penalty] = (matchMismatchStr ?? '').split(',').map(Number);

      if (selectedTool === 'blastn') {
        return {
          tool: selectedTool,
          task: selectedTool,
          ...baseConfig,
          ...dustConfig,
          reward,
          penalty,
        };
      }

      if (selectedTool === 'blastp') {
        return {
          tool: selectedTool,
          task: selectedTool,
          ...baseConfig,
          ...segConfig,
          matrix: scoringMatrixStr as IOBlastPScoringMatrix,
          compBasedStats,
        };
      }

      if (selectedTool === 'blastx') {
        return {
          tool: selectedTool,
          queryGeneticCode: 1,
          task: selectedTool,
          ...baseConfig,
          ...segConfig,
          matrix: scoringMatrixStr as IOBlastXScoringMatrix,
          compBasedStats,
        };
      }

      if (selectedTool === 'tblastn') {
        return {
          tool: selectedTool,
          task: selectedTool,
          ...baseConfig,
          ...segConfig,
          matrix: scoringMatrixStr as IOTBlastNScoringMatrix,
          compBasedStats,
        };
      }

      if (selectedTool === 'tblastx') {
        return {
          tool: selectedTool,
          queryGeneticCode: 1,
          ...baseConfig,
          ...segConfig,
          matrix: scoringMatrixStr as IOTBlastXScoringMatrix,
        };
      }

      throw new Error(`The BLAST tool '${selectedTool}' is not supported`);
    */

    return new JSONObject();
  }
}
