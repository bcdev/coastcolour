package org.esa.beam.coastcolour.processing;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;

/**
 * Class providing methods for CC processor input product validation
 *
 * @author olafd
 */
public class ProductValidator {

    public static boolean isValidL1PInputProduct(Product product) {
        // accept MERIS L1B and ICOL L1N products...
        final boolean merisL1TypePatternMatches = EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(product.getProductType()).matches();
        final boolean merisIcolTypePatternMatches = isValidMerisIcolL1NProduct(product);

        return merisL1TypePatternMatches || merisIcolTypePatternMatches;
    }

    public static boolean isValidL2RInputProduct(Product product) {
        // accept MERIS L1B, ICOL L1N products, and CC L1P products...
        final boolean merisCCL1PTypePatternMatches = isValidMerisCCL1PProduct(product);
        return isValidL1PInputProduct(product) || merisCCL1PTypePatternMatches;
    }

    public static boolean isValidL2WInputProduct(Product product) {
        // accept MERIS L1B, ICOL L1N products, CC L1P, and CC L2R products...
        final boolean merisCCL2RTypePatternMatches = isValidMerisCCL2RProduct(product);
        return isValidL1PInputProduct(product) || isValidL2RInputProduct(product) || merisCCL2RTypePatternMatches;
    }

    private static boolean isValidMerisIcolL1NProduct(Product product) {
        final String icolProductType = product.getProductType();
        if (icolProductType.endsWith("_1N")) {
            int index = icolProductType.indexOf("_1");
            final String merisProductType = icolProductType.substring(0, index) + "_1P";
            return (EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(merisProductType).matches());
        } else {
            return false;
        }
    }

    private static boolean isValidMerisCCL1PProduct(Product product) {
        return CoastcolourConstants.MERIS_CCL1P_TYPE_PATTERN.matcher(product.getProductType()).matches();
    }

    private static boolean isValidMerisCCL2RProduct(Product product) {
        return CoastcolourConstants.MERIS_CCL2R_TYPE_PATTERN.matcher(product.getProductType()).matches();
    }


}
