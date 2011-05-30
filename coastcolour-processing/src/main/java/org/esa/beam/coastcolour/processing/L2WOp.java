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
import org.esa.beam.meris.case2.Case2AlgorithmEnum;
import org.esa.beam.util.ProductUtils;

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

    @Parameter(label = "Bright Test Threshold ", defaultValue = "0.03")
    private double brightTestThreshold;
    @Parameter(label = "Bright Test Reference Wavelength [nm]", defaultValue = "865",
               valueSet = {
                       "412", "442", "490", "510", "560",
                       "620", "665", "681", "705", "753",
                       "760", "775", "865", "890", "900"
               })
    private int brightTestWavelength;


    @Parameter(defaultValue = "l1p_flags.CC_LAND",
               label = "Land detection expression",
               description = "The arithmetic expression used for land detection.",
               notEmpty = true, notNull = true)
    private String landExpression;

    @Parameter(defaultValue = "l1p_flags.CC_CLOUD || l1p_flags.CC_SNOW_ICE",
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
            l2rParams.put("brightTestThreshold", brightTestThreshold);
            l2rParams.put("brightTestWavelength", brightTestWavelength);
            l2rParams.put("landExpression", landExpression);
            l2rParams.put("cloudIceExpression", cloudIceExpression);
            l2rParams.put("outputNormReflec", true);
            l2rParams.put("outputReflecAs", "RADIANCE_REFLECTANCES");

            sourceProduct = GPF.createProduct("CoastColour.L2R", l2rParams, sourceProduct);
        }

        Case2AlgorithmEnum c2rAlgorithm = Case2AlgorithmEnum.REGIONAL;
        Operator case2Op = c2rAlgorithm.createOperatorInstance();

        case2Op.setParameter("tsmConversionExponent", c2rAlgorithm.getDefaultTsmExponent());
        case2Op.setParameter("tsmConversionFactor", c2rAlgorithm.getDefaultTsmFactor());
        case2Op.setParameter("chlConversionExponent", c2rAlgorithm.getDefaultChlExponent());
        case2Op.setParameter("chlConversionFactor", c2rAlgorithm.getDefaultChlFactor());
        case2Op.setParameter("inputReflecAre", "RADIANCE_REFLECTANCES");
        case2Op.setParameter("invalidPixelExpression", invalidPixelExpression);
        case2Op.setSourceProduct("acProduct", sourceProduct);
        final Product targetProduct = case2Op.getTargetProduct();

        copyMasks(sourceProduct, targetProduct);
        renameIops(targetProduct);
        renameConcentrations(targetProduct);
        copyReflecBandsIfRequired(sourceProduct, targetProduct);
        sortFlagBands(targetProduct);
        changeL2WMasksAndFlags(targetProduct);
        String l1pProductType = sourceProduct.getProductType().substring(0, 8) + "CCL2W";
        targetProduct.setProductType(l1pProductType);
        setTargetProduct(targetProduct);
    }

    private void changeL2WMasksAndFlags(Product targetProduct) {
        ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        FlagCoding l2wFlags = flagCodingGroup.get(CASE2_FLAGS_NAME);
        l2wFlags.setName(L2W_FLAGS_NAME);
        l2wFlags.removeAttribute(l2wFlags.getFlag("FIT_FAILED"));
        Band band = targetProduct.getBand(CASE2_FLAGS_NAME);
        band.setName(L2W_FLAGS_NAME);

        ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();
        int lastL1PIndex = 0;
        for (int i = 0; i < maskGroup.getNodeCount(); i++) {
            Mask mask = maskGroup.get(i);
            if (!mask.getName().startsWith("l1p")) {
                lastL1PIndex = i;
                break;
            }
        }


        Mask fit_failed = maskGroup.get("case2_fit_failed");
        maskGroup.remove(fit_failed);

        Mask wlr_oor = maskGroup.get("case2_wlr_oor");
        wlr_oor.setName("l2w_cc_wlr_ootr");
        String wlrOorDescription = "Water leaving reflectance out of training range";
        wlr_oor.setDescription(wlrOorDescription);
        maskGroup.remove(wlr_oor);
        maskGroup.add(lastL1PIndex, wlr_oor);
        l2wFlags.getFlag("WLR_OOR").setDescription(wlrOorDescription);

        Mask conc_oor = maskGroup.get("case2_conc_oor");
        conc_oor.setName("l2w_cc_conc_ootr");
        String concOorDescription = "Water constituents out of training range";
        conc_oor.setDescription(concOorDescription);
        maskGroup.remove(conc_oor);
        maskGroup.add(++lastL1PIndex, conc_oor);
        l2wFlags.getFlag("CONC_OOR").setDescription(concOorDescription);

        Mask ootr = maskGroup.get("case2_ootr");
        ootr.setName("l2w_cc_ootr");
        String ootrDescription = "Spectrum out of training range (chiSquare threshold)";
        ootr.setDescription(ootrDescription);
        maskGroup.remove(ootr);
        maskGroup.add(++lastL1PIndex, ootr);
        l2wFlags.getFlag("OOTR").setDescription(ootrDescription);


        Mask whitecaps = maskGroup.get("case2_whitecaps");
        whitecaps.setName("l2w_cc_whitecaps");
        String whitecapsDescription = "Risk for white caps";
        whitecaps.setDescription(whitecapsDescription);
        maskGroup.remove(whitecaps);
        maskGroup.add(++lastL1PIndex, whitecaps);
        l2wFlags.getFlag("WHITECAPS").setDescription(whitecapsDescription);


        Mask invalid = maskGroup.get("case2_invalid");
        String l2rInvalidExpr = Mask.BandMathsType.getExpression(invalid);
        Mask.BandMathsType.setExpression(invalid, l2rInvalidExpr + " || l2w_flags.OOTR");
        invalid.setName("l2w_cc_invalid");
        String invalidDescription = "Invalid pixels (" + invalidPixelExpression + " || l2w_flags.OOTR)";
        invalid.setDescription(invalidDescription);
        maskGroup.remove(invalid);
        maskGroup.add(++lastL1PIndex, invalid);
        l2wFlags.getFlag("INVALID").setDescription(invalidDescription);
    }

    private void copyMasks(Product sourceProduct, Product targetProduct) {
        ProductNodeGroup<Mask> maskGroup = sourceProduct.getMaskGroup();
        for (int i = 0; i < maskGroup.getNodeCount(); i++) {
            Mask mask = maskGroup.get(i);
            if (!mask.getImageType().getName().equals(Mask.VectorDataType.TYPE_NAME)) {
                mask.getImageType().transferMask(mask, targetProduct);
            }
        }
    }

    private void renameConcentrations(Product targetProduct) {
        targetProduct.getBand("tsm").setName("conc_tsm");
        targetProduct.getBand("chl_conc").setName("conc_chl");
        addPatternToAutoGrouping(targetProduct, "conc");

    }

    private void renameIops(Product targetProduct) {
        String aTotal = "a_total_443";
        String aGelbstoff = "a_ys_443";
        String aPigment = "a_pig_443";
        String aPoc = "a_poc_443";
        String bbSpm = "bb_spm_443";
        targetProduct.getBand(aTotal).setName("iop_" + aTotal);
        targetProduct.getBand(aGelbstoff).setName("iop_" + aGelbstoff);
        targetProduct.getBand(aPigment).setName("iop_" + aPigment);
        targetProduct.getBand(aPoc).setName("iop_" + aPoc);
        targetProduct.getBand(bbSpm).setName("iop_" + bbSpm);
        addPatternToAutoGrouping(targetProduct, "iop");

    }

    private void copyReflecBandsIfRequired(Product sourceProduct, Product targetProduct) {
        if (outputReflec) {
            Band[] bands = sourceProduct.getBands();
            for (Band band : bands) {
                if (band.getName().startsWith("reflec_")) {
                    Band targetBand = ProductUtils.copyBand(band.getName(), sourceProduct, targetProduct);
                    Band sourceBand = sourceProduct.getBand(band.getName());
                    targetBand.setSourceImage(sourceBand.getSourceImage());
                }
            }
            addPatternToAutoGrouping(targetProduct, "reflec");
        }

    }

    private void addPatternToAutoGrouping(Product targetProduct, String groupPattern) {
        Product.AutoGrouping autoGrouping = targetProduct.getAutoGrouping();
        String stringPattern = autoGrouping != null ? autoGrouping.toString() + ":" + groupPattern : groupPattern;
        targetProduct.setAutoGrouping(stringPattern);
    }

    private void changeCase2RFlags(Product targetProduct) {
        ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        FlagCoding agcFlags = flagCodingGroup.get(CASE2_FLAGS_NAME);
        agcFlags.setName(L2W_FLAGS_NAME);
        agcFlags.removeAttribute(agcFlags.getFlag("case2_fit_failed"));
        Band band = targetProduct.getBand(CASE2_FLAGS_NAME);
        band.setName(L2W_FLAGS_NAME);
    }

    private void sortFlagBands(Product targetProduct) {
        Band l1_flags = targetProduct.getBand("l1_flags");
        Band l1p_flags = targetProduct.getBand("l1p_flags");
        Band l2r_flags = targetProduct.getBand("l2r_flags");
        Band case2_flags = targetProduct.getBand("case2_flags");
        targetProduct.removeBand(l1_flags);
        targetProduct.removeBand(l1p_flags);
        targetProduct.removeBand(l2r_flags);
        targetProduct.removeBand(case2_flags);
        targetProduct.addBand(l1_flags);
        targetProduct.addBand(l1p_flags);
        targetProduct.addBand(l2r_flags);
        targetProduct.addBand(case2_flags);
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
