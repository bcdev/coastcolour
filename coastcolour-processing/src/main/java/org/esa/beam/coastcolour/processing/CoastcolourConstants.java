package org.esa.beam.coastcolour.processing;

import java.util.regex.Pattern;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 14.04.14
 * Time: 17:46
 *
 * @author olafd
 */
public class CoastcolourConstants {

    public static final String[] L1P_PARAMETER_NAMES = new String[] {
            "doIcol", "doCalibration", "doSmile", "doEqualization",
            "ccCloudBufferWidth",
            "ccCloudScreeningAmbiguous", "ccCloudScreeningSure",
            "ccGlintCloudThresholdAddition", "ccOutputCloudProbabilityFeatureValue"
    };

    public static final String[] L2R_PARAMETER_NAMES = new String[] {
            "useSnTMap", "averageSalinity", "averageTemperature", "useExtremeCaseMode",
            "landExpression", "cloudIceExpression",
            "outputL2RToa", "outputL2RReflecAs"
    };

    public static Pattern MERIS_CCL1P_TYPE_PATTERN = Pattern.compile("MER_..._CCL1P");
    public static Pattern MERIS_CCL2R_TYPE_PATTERN = Pattern.compile("MER_..._CCL2R");

}
