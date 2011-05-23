package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.operators.CloudScreeningSelector;

import java.util.HashMap;

@OperatorMetadata(alias = "CoastColour.L2W")
public class L2WOp extends Operator {

    private static final String L2W_FLAGS_NAME = "l2w_flags";
    private static final String CASE2_FLAGS_NAME = "case2_flags";

    @SourceProduct(description = "MERIS L1B, L1P or L2R product")
    private Product sourceProduct;

    @Parameter(defaultValue = "true",
               label = "Perform calibration",
               description = "Whether to perform the calibration.")
    private boolean doCalibration;

    @Parameter(defaultValue = "true",
               label = "Perform Smile-effect correction",
               description = "Whether to perform MERIS Smile-effect correction.")
    private boolean doSmile;

    @Parameter(defaultValue = "true",
               label = "Perform equalization",
               description = "Perform removal of detector-to-detector systematic radiometric differences in MERIS L1b data products.")
    private boolean doEqualization;

    @Parameter(defaultValue = "true")
    private boolean useIdepix;

    @Parameter(defaultValue = "CoastColour", valueSet = {"GlobAlbedo", "QWG", "CoastColour"})
    private CloudScreeningSelector algorithm;

    @Parameter(defaultValue = "l1p_flags.F_LANDCONS",
               label = "Land detection expression",
               description = "The arithmetic expression used for land detection.",
               notEmpty = true, notNull = true)
    private String landExpression;

    @Parameter(defaultValue = "l1p_flags.F_CLOUD || l1p_flags.F_SNOW_ICE",
               label = "Cloud/Ice detection expression",
               description = "The arithmetic expression used for cloud/ice detection.",
               notEmpty = true, notNull = true)
    private String cloudIceExpression;

    @Parameter(defaultValue = "l2r_flags.INVALID",
               description = "Expression defining pixels not considered for case2r processing")
    private String invalidPixelExpression;

    @Parameter(defaultValue = "false", label = "Output water leaving reflectance",
               description = "Toggles the output of water leaving irradiance reflectance.")
    private boolean outputReflec;

    @Override
    public void initialize() throws OperatorException {

        Product sourceProduct = this.sourceProduct;
        if (!isL2RSourceProduct(sourceProduct)) {
            HashMap<String, Object> l2rParams = new HashMap<String, Object>();
            l2rParams.put("doCalibration", doCalibration);
            l2rParams.put("doSmile", doSmile);
            l2rParams.put("doEqualization", doEqualization);
            l2rParams.put("useIdepix", useIdepix);
            l2rParams.put("algorithm", algorithm);
            l2rParams.put("landExpression", landExpression);
            l2rParams.put("cloudIceExpression", cloudIceExpression);
            l2rParams.put("outputNormReflec", true);
            l2rParams.put("outputReflecAs", "RADIANCE_REFLECTANCES");

            sourceProduct = GPF.createProduct("CoastColour.L2R", l2rParams, sourceProduct);
        }
        Band[] l2Rbands = sourceProduct.getBands();
        for (Band l2Rband : l2Rbands) {
            if (l2Rband.getName().startsWith("norm_refl_")) {
                sourceProduct.removeBand(l2Rband);
            }
        }

        HashMap<String, Product> sourceProducts = new HashMap<String, Product>();
        sourceProducts.put("source", sourceProduct);
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("doAtmosphericCorrection", false);
        parameters.put("outputReflec", outputReflec);
        parameters.put("invalidPixelExpression", invalidPixelExpression);
        Product targetProduct = GPF.createProduct("Meris.Case2Regional", parameters, sourceProducts);

        changeCase2RFlags(targetProduct);
        sortFlagBands(targetProduct);
        setTargetProduct(targetProduct);
    }


    private void changeCase2RFlags(Product targetProduct) {
        ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        FlagCoding agcFlags = flagCodingGroup.get(CASE2_FLAGS_NAME);
        agcFlags.setName(L2W_FLAGS_NAME);
        Band band = targetProduct.getBand(CASE2_FLAGS_NAME);
        band.setName(L2W_FLAGS_NAME);
    }

    private void sortFlagBands(Product targetProduct) {
        Band l1_flags = targetProduct.getBand("l1_flags");
        Band l1p_flags = targetProduct.getBand("l1p_flags");
        Band l2r_flags = targetProduct.getBand("l2r_flags");
        Band l2w_flags = targetProduct.getBand("l2w_flags");
        targetProduct.removeBand(l1_flags);
        targetProduct.removeBand(l1p_flags);
        targetProduct.removeBand(l2r_flags);
        targetProduct.removeBand(l2w_flags);
        targetProduct.addBand(l1_flags);
        targetProduct.addBand(l1p_flags);
        targetProduct.addBand(l2r_flags);
        targetProduct.addBand(l2w_flags);
    }

    private boolean isL2RSourceProduct(Product sourceProduct) {
        return sourceProduct.containsBand("l2r_flags");
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L2WOp.class);
        }
    }
}
