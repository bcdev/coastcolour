package org.esa.beam.coastcolour.processing;

import org.esa.beam.coastcolour.glint.atmosphere.operator.GlintCorrectionOperator;
import org.esa.beam.coastcolour.glint.atmosphere.operator.ReflectanceEnum;
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
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.ResourceInstaller;

import java.io.File;
import java.util.HashMap;

@OperatorMetadata(alias = "CoastColour.L2R",
                  version = "1.7",
                  authors = "Marco Peters, Norman Fomferra",
                  copyright = "(c) 2011 Brockmann Consult",
                  description = "Performs a atmospheric correction. The result contains (normalised) water leaving " +
                          "reflectance and information about atmospheric properties")
public class L2ROp extends Operator {

    private static final String AGC_FLAGS_NAME = "agc_flags";
    private static final String L2R_FLAGS_NAME = "l2r_flags";

    // another new net from RD, 2012/06/28:
    // 31x47x37_57596.9.net
    public static final String MERIS_ATMOSPHERIC_NET_NAME = GlintCorrectionOperator.MERIS_ATMOSPHERIC_EXTREME_NET_NAME;
    // another new net from RD, 2012/06/18:
//    public static final String MERIS_ATMOSPHERIC_NET_NAME = "atmo_correct_meris/31x47x37_26651.6.net";
    // another new net from RD, 2012/06/08:
//    private static final String MERIS_ATMOSPHERIC_NET_NAME = "atmo_correct_meris/31x47x37_72066.8.net";
//    private static final String ATMO_AANN_NET = "atmo_aann/21x5x21_20.4.net";
    private static final String ATMO_AANN_NET = GlintCorrectionOperator.ATMO_AANN_EXTREME_NET_NAME;

    @SourceProduct(alias = "CC_L1P", description = "CC L1P or MERIS L1b product")
    private Product sourceProduct;

    @Parameter(defaultValue = "true",
               label = "[L1P] Perform calibration",
               description = "Whether to perform the calibration.")
    private boolean doCalibration;

    @Parameter(defaultValue = "true",
               label = "[L1P] Perform Smile-effect correction",
               description = "Whether to perform MERIS Smile-effect correction.")
    private boolean doSmile;

    @Parameter(defaultValue = "true",
               label = "[L1P] Perform equalization",
               description = "Perform removal of detector-to-detector systematic radiometric differences in MERIS L1b data products.")
    private boolean doEqualization;

    @Parameter(label = "[L1P] Bright Test Threshold ", defaultValue = "0.03")
    private double brightTestThreshold;

    @Parameter(label = "[L1P] Bright Test Reference Wavelength [nm]", defaultValue = "865",
               valueSet = {
                       "412", "442", "490", "510", "560",
                       "620", "665", "681", "705", "753",
                       "760", "775", "865", "890", "900"
               })
    private int brightTestWavelength;

    @Parameter(label = "Use climatology map for salinity and temperature", defaultValue = "true",
               description = "By default a climatology map is used. If set to 'false' the specified average values are used " +
                       "for the whole scene.")
    private boolean useSnTMap;

    @Parameter(label = "Use NNs for extreme ranges of coastcolour IOPs", defaultValue = "true",
               description = "Use special set of NNs to finally derive water IOPs in extreme ranges.")
    private boolean useExtremeCaseMode;

    @Parameter(label = "Average salinity", defaultValue = "35", unit = "PSU",
               description = "The average salinity of the water in the region to be processed.")
    private double averageSalinity;

    @Parameter(label = "Average temperature", defaultValue = "15", unit = "C",
               description = "The average temperature of the water in the region to be processed.")
    private double averageTemperature;

    @Parameter(label = "TOSA OOS Threshold", defaultValue = "0.05",
               description = "TOSA out of scope threshold: If chi_square_error is larger, TOSA_OOS flag is raised.")
    private double tosaOosThresh;

    @Parameter(label = "MERIS net (full path required for other than default)",
               defaultValue = MERIS_ATMOSPHERIC_NET_NAME,
               description = "The file of the atmospheric net to be used instead of the default neural net.",
               notNull = false)
    private File atmoNetMerisFile;

    @Parameter(label = "Autoassociative net (full path required for other than default)",
               defaultValue = ATMO_AANN_NET,
               description = "The file of the autoassociative net used for error computed instead of the default neural net.",
               notNull = false)
    private File autoassociativeNetFile;

    @Parameter(defaultValue = "l1p_flags.CC_LAND",
               label = "Land detection expression",
               description = "The arithmetic expression used for land detection.",
               notEmpty = true, notNull = true)
    private String landExpression;

    @Parameter(defaultValue = "(l1p_flags.CC_CLOUD && not l1p_flags.CC_CLOUD_AMBIGUOUS) || l1p_flags.CC_SNOW_ICE",
               label = "Cloud/Ice detection expression",
               description = "The arithmetic expression used for cloud/ice detection.",
               notEmpty = true, notNull = true)
    private String cloudIceExpression;

    @Parameter(defaultValue = "false", label = "Output TOSA reflectance",
               description = "Toggles the output of Top of Standard Atmosphere reflectance.")
    private boolean outputTosa;

    // no longer a user option because path and transmittance are no longer output from atmospheric net
//    @Parameter(defaultValue = "false", label = "Output path reflectance",
//               description = "Toggles the output of water leaving path reflectance.")
//    private boolean outputPath;
//
//    @Parameter(defaultValue = "false", label = "Output transmittance",
//               description = "Toggles the output of downwelling irradiance transmittance.")
//    private boolean outputTransmittance;

    @Parameter(defaultValue = "RADIANCE_REFLECTANCES", valueSet = {"RADIANCE_REFLECTANCES", "IRRADIANCE_REFLECTANCES"},
               label = "Output water leaving reflectance as",
               description = "Select if reflectances shall be written as radiances or irradiances. " +
                       "The irradiances are compatible with standard MERIS product.")
    private ReflectanceEnum outputReflecAs;

    private Product glintProduct;
    private Product l1pProduct;


    @Override
    public void initialize() throws OperatorException {
        if (!isL1PSourceProduct(sourceProduct)) {
            HashMap<String, Object> l1pParams = createL1pParameterMap();
            l1pProduct = GPF.createProduct("CoastColour.L1P", l1pParams, sourceProduct);
        } else {
            l1pProduct = sourceProduct;
        }

        HashMap<String, Product> sourceProducts = new HashMap<String, Product>();
        sourceProducts.put("merisProduct", l1pProduct);

        HashMap<String, Object> glintParameters = createGlintAcParameterMap();
        glintProduct = GPF.createProduct("MerisCC.GlintCorrection", glintParameters, sourceProducts);

        Product targetProduct = createL2RProduct();
        setTargetProduct(targetProduct);
    }

    private HashMap<String, Object> createGlintAcParameterMap() {
        HashMap<String, Object> glintParameters = new HashMap<String, Object>();
        glintParameters.put("doSmileCorrection", false);
        glintParameters.put("outputTosa", outputTosa);
        glintParameters.put("outputTosaQualityIndicator", true);
        glintParameters.put("outputReflec", true);
        glintParameters.put("outputNormReflec", true);
        glintParameters.put("outputReflecAs", outputReflecAs);
        glintParameters.put("outputPath", false);
        glintParameters.put("outputTransmittance", false);
        glintParameters.put("deriveRwFromPath", false);
        glintParameters.put("useSnTMap", useSnTMap);
        glintParameters.put("averageSalinity", averageSalinity);
        glintParameters.put("averageTemperature", averageTemperature);
        glintParameters.put("tosaOosThresh", tosaOosThresh);
        if (!useExtremeCaseMode) {
            atmoNetMerisFile = new File(GlintCorrectionOperator.MERIS_ATMOSPHERIC_NET_NAME);
            autoassociativeNetFile = new File(GlintCorrectionOperator.ATMO_AANN_NET_NAME);
        }
        glintParameters.put("atmoNetMerisFile", atmoNetMerisFile);
        glintParameters.put("autoassociativeNetFile", autoassociativeNetFile);
        glintParameters.put("landExpression", landExpression);
        glintParameters.put("cloudIceExpression", cloudIceExpression);
        glintParameters.put("useFlint", false);
        return glintParameters;
    }

    private HashMap<String, Object> createL1pParameterMap() {
        HashMap<String, Object> l1pParams = new HashMap<String, Object>();
        l1pParams.put("doCalibration", doCalibration);
        l1pParams.put("doSmile", doSmile);
        l1pParams.put("doEqualization", doEqualization);
        l1pParams.put("useIdepix", true);
        l1pParams.put("brightTestThreshold", brightTestThreshold);
        l1pParams.put("brightTestWavelength", brightTestWavelength);
        return l1pParams;
    }

    private Product createL2RProduct() {
        String l2rProductType = l1pProduct.getProductType().substring(0, 8) + "CCL2R";
        final int sceneWidth = l1pProduct.getSceneRasterWidth();
        final int sceneHeight = l1pProduct.getSceneRasterHeight();
        Product l2rProduct = new Product(l1pProduct.getName(), l2rProductType, sceneWidth, sceneHeight);
        l2rProduct.setDescription("MERIS CoastColour L2R");
        l2rProduct.setStartTime(glintProduct.getStartTime());
        l2rProduct.setEndTime(glintProduct.getEndTime());
        ProductUtils.copyMetadata(glintProduct, l2rProduct);
        ProductUtils.copyMasks(glintProduct, l2rProduct);
        copyBands(glintProduct, l2rProduct);
        ProductUtils.copyFlagBands(glintProduct, l2rProduct, true);
        ProductUtils.copyTiePointGrids(glintProduct, l2rProduct);
        ProductUtils.copyGeoCoding(glintProduct, l2rProduct);

        l2rProduct.setAutoGrouping(glintProduct.getAutoGrouping());
        changeAgcFlags(l2rProduct);
        removeFlagsAndMasks(l2rProduct);
        sortMasks(l2rProduct);
        sortFlagBands(l2rProduct);
        renameTauBands(l2rProduct);
        removeUnwantedBands(l2rProduct);

        // copy AMORGOS lat/lon bands from L1P if available
        if (sourceProduct.getBand("corr_longitude") != null && sourceProduct.getBand("corr_latitude") != null) {
            if (!l2rProduct.containsBand("corr_longitude")) {
                ProductUtils.copyBand("corr_longitude", sourceProduct, l2rProduct, true);
            }
            if (!l2rProduct.containsBand("corr_latitude")) {
                ProductUtils.copyBand("corr_latitude", sourceProduct, l2rProduct, true);
            }
        }

        return l2rProduct;
    }

    @Override
    public void dispose() {
        if (glintProduct != null) {
            glintProduct.dispose();
            glintProduct = null;
        }
        if (l1pProduct != sourceProduct) {
            l1pProduct.dispose();
            l1pProduct = null;
        }

        super.dispose();
    }

    private void copyBands(Product glintProduct, Product l2rProduct) {
        final Band[] radiometryBands = glintProduct.getBands();
        for (Band band : radiometryBands) {
            if (!band.isFlagBand()) {
                ProductUtils.copyBand(band.getName(), glintProduct, l2rProduct, true);
            }
        }
    }

    private void removeUnwantedBands(Product targetProduct) {
        targetProduct.removeBand(targetProduct.getBand("glint_ratio"));
        targetProduct.removeBand(targetProduct.getBand("a_tot"));
        targetProduct.removeBand(targetProduct.getBand("b_tsm"));
    }

    private void sortMasks(Product targetProduct) {
        ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();
        String l1pMaskPrefix = "l1p_";
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_LAND_FLAG_NAME.toLowerCase(), 0);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_COASTLINE_FLAG_NAME.toLowerCase(), 1);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_CLOUD_FLAG_NAME.toLowerCase(), 2);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_CLOUD_AMBIGUOUS_FLAG_NAME.toLowerCase(), 3);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_CLOUD_BUFFER_FLAG_NAME.toLowerCase(), 4);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_CLOUD_SHADOW_FLAG_NAME.toLowerCase(), 5);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_SNOW_ICE_FLAG_NAME.toLowerCase(), 6);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_MIXEDPIXEL_FLAG_NAME.toLowerCase(), 7);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_GLINTRISK_FLAG_NAME.toLowerCase(), 8);

    }

    private void moveMaskAtIndex(ProductNodeGroup<Mask> maskGroup, String maskName, int index) {
        Mask mask = maskGroup.get(maskName);
        if (mask != null) {
            maskGroup.remove(mask);
            maskGroup.add(index, mask);
        }
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
        band.setDescription("CC L2R atmospheric correction quality flags.");
    }

    private void removeFlagsAndMasks(Product targetProduct) {
        ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        FlagCoding l2rFlags = flagCodingGroup.get(L2R_FLAGS_NAME);
        l2rFlags.removeAttribute(l2rFlags.getFlag("LAND"));
        l2rFlags.removeAttribute(l2rFlags.getFlag("CLOUD_ICE"));
        l2rFlags.removeAttribute(l2rFlags.getFlag("HAS_FLINT"));
        String invalidDescr = "'Input invalid' pixels (" + landExpression + " || " + cloudIceExpression + " || l1_flags.INVALID)";
        l2rFlags.getFlag("INPUT_INVALID").setDescription(invalidDescr);
        String glintDescription = "High sun glint retrieved";
        l2rFlags.getFlag("SUNGLINT").setDescription(glintDescription);
        String toaDescription = "TOA reflectance out of range";
        l2rFlags.getFlag("TOA_OOR").setDescription(toaDescription);
        String tosaDescription = "TOSA reflectance out of range";
        l2rFlags.getFlag("TOSA_OOR").setDescription(tosaDescription);
        String tosaOosDescription = "TOSA reflectance out of scope";
        l2rFlags.getFlag("TOSA_OOS").setDescription(tosaOosDescription);

        String l2rInvalidDescr = "'L2R invalid' pixels (quality indicator > 3 || l1p_flags.CC_CLOUD)";
        l2rFlags.getFlag("L2R_INVALID").setDescription(l2rInvalidDescr);
        String l2rSuspectDescr = "'L2R suspect' pixels " +
                "(quality indicator > 1 || l1p_flags.CC_CLOUD || l1p_flags.CC_CLOUD_BUFFER || l1p_flags.CC_CLOUD_SHADOW || l1p_flags.CC_SNOW_ICE || l1p_flags.CC_MIXEDPIXEL)";
        l2rFlags.getFlag("L2R_SUSPECT").setDescription(l2rSuspectDescr);

        ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();
        maskGroup.remove(maskGroup.get("agc_land"));
        maskGroup.remove(maskGroup.get("cloud_ice"));
        maskGroup.remove(maskGroup.get("has_flint"));
        maskGroup.get("aot560_oor").setName("l2r_cc_aot560_oor");
        maskGroup.get("toa_oor").setDescription(toaDescription);
        maskGroup.get("toa_oor").setName("l2r_cc_toa_oor");
        maskGroup.get("tosa_oor").setDescription(tosaDescription);
        maskGroup.get("tosa_oor").setName("l2r_cc_tosa_oor");
        maskGroup.get("tosa_oos").setDescription(tosaOosDescription);
        maskGroup.get("tosa_oos").setName("l2r_cc_tosa_oos");
        maskGroup.get("solzen").setName("l2r_cc_solzen");
        maskGroup.get("ancil").setName("l2r_cc_ancil");
        maskGroup.get("sunglint").setDescription(glintDescription);
        maskGroup.get("sunglint").setName("l2r_cc_sunglint");
        maskGroup.get("agc_invalid").setDescription(invalidDescr);
        maskGroup.get("agc_invalid").setName("l2r_cc_input_invalid");

        maskGroup.get("l2r_invalid").setDescription(l2rInvalidDescr);
        maskGroup.get("l2r_invalid").setName("l2r_cc_reflec_invalid");
        maskGroup.get("l2r_suspect").setDescription(l2rSuspectDescr);
        maskGroup.get("l2r_suspect").setName("l2r_cc_reflec_suspect");
    }


    private boolean isL1PSourceProduct(Product sourceProduct) {
        return sourceProduct.containsBand("l1p_flags");
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L2ROp.class);
            AuxdataInstaller.installAuxdata(ResourceInstaller.getSourceUrl(L2ROp.class));
        }


    }
}
