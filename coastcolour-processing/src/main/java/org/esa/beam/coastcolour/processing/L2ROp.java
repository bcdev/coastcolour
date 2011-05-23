package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
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

@OperatorMetadata(alias = "CoastColour.L2R")
public class L2ROp extends Operator {

    private static final String AGC_FLAGS_NAME = "agc_flags";
    private static final String L2R_FLAGS_NAME = "l2r_flags";

    @SourceProduct(description = "MERIS L1B or L1P product")
    private Product sourceProduct;

    @Parameter(defaultValue = "true",
               label = "Perform calibration",
               description = "Whether to perform the calibration.")
    private boolean doCalibration;

    @Parameter(defaultValue = "true",
               label = "Perform Smile-effect correction",
               description = "Whether to perform MERIS Smile-effect correction.")
    private boolean doSmile;

    @Parameter(defaultValue = "false",
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

    @Override
    public void initialize() throws OperatorException {
        Product sourceProduct = this.sourceProduct;
        if (!isL1PSourceProduct(sourceProduct)) {
            HashMap<String, Object> l1pParams = new HashMap<String, Object>();
            l1pParams.put("doCalibration", doCalibration);
            l1pParams.put("doSmile", doSmile);
            l1pParams.put("doEqualization", doEqualization);
            l1pParams.put("useIdepix", useIdepix);
            l1pParams.put("algorithm", algorithm);
            sourceProduct = GPF.createProduct("CoastColour.L1P", l1pParams, sourceProduct);
        }

        HashMap<String, Product> sourceProducts = new HashMap<String, Product>();
        sourceProducts.put("merisProduct", sourceProduct);

        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("doSmileCorrection", false);
        parameters.put("outputTosa", false);
        parameters.put("outputReflec", true);
        parameters.put("outputNormReflec", true);
        parameters.put("outputReflecAs", "RADIANCE_REFLECTANCES");
        parameters.put("outputPath", false);
        parameters.put("outputTransmittance", false);
        parameters.put("deriveRwFromPath", false);
        parameters.put("landExpression", landExpression);
        parameters.put("cloudIceExpression", cloudIceExpression);
        parameters.put("useFlint", false);

        Product glintProduct = GPF.createProduct("Meris.GlintCorrection", parameters, sourceProducts);

        // need a copy of the product in order to make changes to flag names
        // otherwise a GlintCorrection can't handle the changed target product anymore
        Product targetProduct = GPF.createProduct("Subset", GPF.NO_PARAMS, glintProduct);
        targetProduct.setAutoGrouping(glintProduct.getAutoGrouping());
        changeAgcFlags(targetProduct);
        removeFlagsAndMasks(targetProduct);
        sortFlagBands(targetProduct);
        renameTauBands(targetProduct);
        setTargetProduct(targetProduct);
    }

    private void renameTauBands(Product targetProduct) {
        Band[] bands = targetProduct.getBands();
        for (Band band : bands) {
            if (band.getName().startsWith("tau_")) {
                band.setName("atm_" + band.getName());
            }
        }
        String stringPattern = targetProduct.getAutoGrouping().toString();
        targetProduct.setAutoGrouping(stringPattern + ":atm_tau");
    }

    private void sortFlagBands(Product targetProduct) {
        Band l1_flags = targetProduct.getBand("l1_flags");
        Band l1p_flags = targetProduct.getBand("l1p_flags");
        Band l2r_flags = targetProduct.getBand("l2r_flags");
        targetProduct.removeBand(l1_flags);
        targetProduct.removeBand(l1p_flags);
        targetProduct.removeBand(l2r_flags);
        targetProduct.addBand(l1_flags);
        targetProduct.addBand(l1p_flags);
        targetProduct.addBand(l2r_flags);
    }

    private void changeAgcFlags(Product targetProduct) {
        ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        FlagCoding agcFlags = flagCodingGroup.get(AGC_FLAGS_NAME);
        agcFlags.setName(L2R_FLAGS_NAME);
        Band band = targetProduct.getBand(AGC_FLAGS_NAME);
        band.setName(L2R_FLAGS_NAME);
    }

    private void removeFlagsAndMasks(Product targetProduct) {
        ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        FlagCoding l2rFlags = flagCodingGroup.get(L2R_FLAGS_NAME);
        l2rFlags.removeAttribute(l2rFlags.getFlag("LAND"));
        l2rFlags.removeAttribute(l2rFlags.getFlag("CLOUD_ICE"));
        l2rFlags.removeAttribute(l2rFlags.getFlag("HAS_FLINT"));
        ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();
        maskGroup.remove(maskGroup.get("agc_land"));
        maskGroup.remove(maskGroup.get("cloud_ice"));
        maskGroup.remove(maskGroup.get("has_flint"));
    }


    private boolean isL1PSourceProduct(Product sourceProduct) {
        return sourceProduct.containsBand("l1p_flags");
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L2ROp.class);
        }
    }
}
