package org.esa.beam.coastcolour.util;

import ucar.nc2.Variable;

/**
 * Validation class for CC product stitcher
 * Date: 15.03.12
 * Time: 11:33
 *
 * @author olafd
 */
public class ProductStitcherValidation {
    static boolean isValidBandVariable(Variable variable) {
        return variable.getName().equals("metadata") || areBandDimensionsValid(variable);
    }

    static boolean isValidFlagBandVariable(Variable variable) {
        return areBandDimensionsValid(variable);
    }

    static boolean isValidMaskBandVariable(Variable variable) {
        return variable.getName().endsWith("_mask");
    }

    static boolean isMetadataVariable(Variable variable) {
        return variable.getName().equals("metadata");
    }

    static boolean isValidTpVariable(Variable variable) {
        return areTpDimensionsValid(variable);
    }

    static boolean areBandDimensionsValid(Variable variable) {
        return variable.getDimensions().size() == 2 &&
                variable.getDimension(0).getName().equals(ProductStitcher.DIMY_NAME) &&
                variable.getDimension(1).getName().equals(ProductStitcher.DIMX_NAME);
    }

    static boolean areTpDimensionsValid(Variable variable) {
        return variable.getDimensions().size() == 2 &&
                variable.getDimension(0).getName().equals(ProductStitcher.TP_DIMY_NAME) &&
                variable.getDimension(1).getName().equals(ProductStitcher.TP_DIMX_NAME);
    }

}
