package org.esa.beam.coastcolour.processing;

import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.coastcolour.glint.atmosphere.operator.GlintCorrectionOperator;
import org.esa.beam.coastcolour.glint.atmosphere.operator.ReflectanceEnum;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.algorithms.coastcolour.CoastColourClassificationOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.ResourceInstaller;

import javax.media.jai.RenderedOp;
import javax.media.jai.operator.MultiplyConstDescriptor;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

@OperatorMetadata(alias = "CoastColour.L2R",
                  version = "1.8",
                  authors = "C. Brockmann, M. Bouvet, R. Santer, H. Schiller, M. Peters, O. Danne",
                  copyright = "(c) 2011-2013 Brockmann Consult",
                  description = "Performs an atmospheric correction. The result contains (normalised) water leaving " +
                          "reflectance and information about atmospheric properties")
public class L2ROp extends Operator {

    private static final String AGC_FLAGS_NAME = "agc_flags";
    private static final String L2R_FLAGS_NAME = "l2r_flags";

    private File atmoNetMerisFile;
    private File autoassociativeNetFile;

    @SourceProduct(alias = "ccL1P",
                   label = "CC L1P or MERIS L1B product",
                   description = "The CC L1P or MERIS L1B input product")
    private Product sourceProduct;

    @Parameter(defaultValue = "false",
               label = " [L1P] Perform re-calibration",
               description = "Applies correction from MERIS 2nd to 3rd reprocessing quality. " +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.")
    private boolean doCalibration;

    @Parameter(defaultValue = "true",
               label = " [L1P] Perform Smile-effect correction",
               description = "Whether to perform MERIS Smile-effect correction. " +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.")
    private boolean doSmile;

    @Parameter(defaultValue = "true",
               label = " [L1P] Perform equalization",
               description = "Perform removal of detector-to-detector systematic radiometric differences in MERIS L1b data products. " +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.")
    private boolean doEqualization;


    // IdePix parameters  from L1P
    @Parameter(defaultValue = "false",
               description = "Check for sea/lake ice also outside Sea Ice Climatology area." +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.",
               label = "[L1P] Check for sea/lake ice also outside sea ice climatology area"
    )
    private boolean ccIgnoreSeaIceClimatology;

    @Parameter(defaultValue = "2", interval = "[0,100]",
               description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy. " +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.",
               label = " [L1P] Width of cloud buffer (# of pixels)")
    private int ccCloudBufferWidth;

    @Parameter(defaultValue = "1.4",
               description = "Threshold of Cloud Probability Feature Value above which cloud is regarded as still " +
                       "ambiguous (i.e. a higher value results in fewer ambiguous clouds). " +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.",
               label = " [L1P] Cloud screening 'ambiguous' threshold")
    private double ccCloudScreeningAmbiguous = 1.4;      // Schiller

    @Parameter(defaultValue = "1.8",
               description = "Threshold of Cloud Probability Feature Value above which cloud is regarded " +
                       "as sure (i.e. a higher value results in fewer sure clouds). " +
                       "This is a L1P option and has only effectt if the source product is a MERIS L1b product.",
               label = " [L1P] Cloud screening 'sure' threshold")
    private double ccCloudScreeningSure = 1.8;       // Schiller

    @Parameter(defaultValue = "false",
               description = "Write Cloud Probability Feature Value to the target product. " +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.",
               label = " [L1P] Write Cloud Probability Feature Value to the target product")
    private boolean ccOutputCloudProbabilityFeatureValue = false;

    @Parameter(defaultValue = "true",
               label = " Use climatology map for salinity and temperature",
               description = "By default a climatology map is used. " +
                       "If set to 'false' the specified average values are used " +
                       "for the whole scene.")
    private boolean useSnTMap;

    @Parameter(defaultValue = "35", unit = "PSU",
               label = " Average salinity",
               description = "If no climatology is used, the average salinity of the water in the region to be processed is taken.")
    private double averageSalinity;

    @Parameter(defaultValue = "15", unit = "C",
               label = " Average temperature",
               description = "If no climatology is used, the average temperature of the water in the region to be processed is taken.")
    private double averageTemperature;

    @Parameter(defaultValue = "true",
               label = " Use NNs for maximum ranges of CoastColour IOPs",
               description = "If selected a neural network for maximum range of concentrations and IOPs is used. " +
                       "If deselected a neural net with limited ranges but less noisy for low concentration ranges is used.")
    private boolean useExtremeCaseMode;

    @Parameter(defaultValue = "l1p_flags.CC_LAND",
               label = " Land masking expression",
               description = "The arithmetic expression used for land masking.",
               notEmpty = true, notNull = true)
    private String landExpression;

    @Parameter(defaultValue = "(l1p_flags.CC_CLOUD && not l1p_flags.CC_CLOUD_AMBIGUOUS) || l1p_flags.CC_SNOW_ICE",
               label = " Cloud/Ice masking expression",
               description = "The arithmetic expression used for cloud/ice masking.",
               notEmpty = true, notNull = true)
    private String cloudIceExpression;

    @Parameter(defaultValue = "false",
               label = " Write TOA reflectances to the target product",
               description = "Writes the Top of Atmosphere reflectances to the CC L2R target product.")
    private boolean outputL2RToa;

    //  RADIANCE_REFLECTANCES   : x
    //  IRRADIANCE_REFLECTANCES : x * PI      (see GlintCorrection.perform)
    @Parameter(defaultValue = "RADIANCE_REFLECTANCES", valueSet = {"RADIANCE_REFLECTANCES", "IRRADIANCE_REFLECTANCES"},
               label = " Write all reflectances as",
               description = "Select if all output reflectances shall be written as radiances or irradiances. " +
                       "The irradiances ( = radiances multiplied by PI) are compatible with the standard MERIS product.")
    private ReflectanceEnum outputL2RReflecAs;

    private Product glintProduct;
    private Product toaReflProduct;
    private Product l1pProduct;


    @Override
    public void initialize() throws OperatorException {

        if (!ProductValidator.isValidL2RInputProduct(sourceProduct)) {
            final String message = String.format("Input product '%s' is not a valid source for L2R processing",
                                                 sourceProduct.getName());
            throw new OperatorException(message);
        }

        if (!isL1PSourceProduct(sourceProduct)) {
            HashMap<String, Object> l1pParams = createL1pParameterMap();
            l1pProduct = GPF.createProduct("CoastColour.L1P", l1pParams, sourceProduct);
        } else {
            l1pProduct = sourceProduct;
        }

        HashMap<String, Product> glintSourceProducts = new HashMap<String, Product>();
        glintSourceProducts.put("merisProduct", l1pProduct);

        HashMap<String, Object> glintParameters = createGlintAcParameterMap();
        glintProduct = GPF.createProduct("MerisCC.GlintCorrection", glintParameters, glintSourceProducts);

        if (outputL2RToa) {
            HashMap<String, Product> rad2reflSourceProducts = new HashMap<String, Product>();
            rad2reflSourceProducts.put("sourceProduct", l1pProduct);
            toaReflProduct = GPF.createProduct("Meris.Rad2Refl", GPF.NO_PARAMS, rad2reflSourceProducts);
        }

        Product targetProduct = createL2RProduct();
        setTargetProduct(targetProduct);
    }

    private HashMap<String, Object> createGlintAcParameterMap() {
        HashMap<String, Object> glintParameters = new HashMap<String, Object>();
        glintParameters.put("doSmileCorrection", false);
        glintParameters.put("outputReflec", true);
//        glintParameters.put("outputNormReflec", true);
//        glintParameters.put("outputNormReflec", false);  // CB, 20140414
        glintParameters.put("outputNormReflec", true);  // CB, 20140415 :-)
        glintParameters.put("outputReflecAs", outputL2RReflecAs);
        glintParameters.put("outputPath", false);
        glintParameters.put("outputTransmittance", false);
        glintParameters.put("deriveRwFromPath", false);
        glintParameters.put("useSnTMap", useSnTMap);
        glintParameters.put("averageSalinity", averageSalinity);
        glintParameters.put("averageTemperature", averageTemperature);
        if (useExtremeCaseMode) {
            atmoNetMerisFile = new File(GlintCorrectionOperator.MERIS_ATMOSPHERIC_EXTREME_NET_NAME);
            autoassociativeNetFile = new File(GlintCorrectionOperator.ATMO_AANN_EXTREME_NET_NAME);
        } else {
            atmoNetMerisFile = new File(GlintCorrectionOperator.MERIS_ATMOSPHERIC_NET_NAME);
            autoassociativeNetFile = new File(GlintCorrectionOperator.ATMO_AANN_NET_NAME);
        }
        glintParameters.put("atmoNetMerisFile", atmoNetMerisFile);
        glintParameters.put("autoassociativeNetFile", autoassociativeNetFile);
        glintParameters.put("landExpression", landExpression);
        glintParameters.put("cloudIceExpression", cloudIceExpression);
        return glintParameters;
    }

    private HashMap<String, Object> createL1pParameterMap() {
        HashMap<String, Object> l1pParams = new HashMap<String, Object>();
        l1pParams.put("doCalibration", doCalibration);
        l1pParams.put("doSmile", doSmile);
        l1pParams.put("doEqualization", doEqualization);
        l1pParams.put("useIdepix", true);
        l1pParams.put("ccCloudBufferWidth", ccCloudBufferWidth);
        l1pParams.put("ccIgnoreSeaIceClimatology", ccIgnoreSeaIceClimatology);
        l1pParams.put("ccCloudScreeningAmbiguous", ccCloudScreeningAmbiguous);
        l1pParams.put("ccCloudScreeningSure", ccCloudScreeningSure);
        l1pParams.put("ccOutputCloudProbabilityFeatureValue", ccOutputCloudProbabilityFeatureValue);
        return l1pParams;
    }

    private Product createL2RProduct() {
        String l2rProductType = l1pProduct.getProductType().substring(0, 8) + "CCL2R";
        final int sceneWidth = l1pProduct.getSceneRasterWidth();
        final int sceneHeight = l1pProduct.getSceneRasterHeight();
        Product l2rProduct = new Product(l1pProduct.getName(), l2rProductType, sceneWidth, sceneHeight);

        l2rProduct.setDescription("MERIS CoastColour L2R. Reflectance output is " + outputL2RReflecAs.toString());
        l2rProduct.setStartTime(glintProduct.getStartTime());
        l2rProduct.setEndTime(glintProduct.getEndTime());
        ProductUtils.copyMetadata(glintProduct, l2rProduct);
        ProductUtils.copyMasks(glintProduct, l2rProduct);
        if (outputL2RToa) {
            copyRad2ReflProductBands(toaReflProduct, l2rProduct);
        }

        copyGlintProductBands(glintProduct, l2rProduct);
        final Band cloudProbFeatureValueBand = l1pProduct.getBand(CoastColourClassificationOp.CLOUD_PROBABILITY_VALUE);
        if (cloudProbFeatureValueBand != null) {
            ProductUtils.copyBand(cloudProbFeatureValueBand.getName(), l1pProduct, l2rProduct, true);
        }

        ProductUtils.copyFlagBands(glintProduct, l2rProduct, true);
        ProductUtils.copyTiePointGrids(glintProduct, l2rProduct);
        ProductUtils.copyGeoCoding(glintProduct, l2rProduct);

        final String autoGrouping = getAutogroupingPattern();
        l2rProduct.setAutoGrouping(autoGrouping);
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

    private void copyGlintProductBands(Product glintProduct, Product l2rProduct) {
        final Band[] radiometryBands = glintProduct.getBands();
        for (Band band : radiometryBands) {
            if (!band.isFlagBand()) {
                ProductUtils.copyBand(band.getName(), glintProduct, l2rProduct, true);
            }
        }
    }

    private void copyRad2ReflProductBands(Product rad2reflProduct, Product l2rProduct) {

        final Band[] toaReflBands = rad2reflProduct.getBands();

        for (Band band : toaReflBands) {
            if (!band.isFlagBand() &&
                    band.getSpectralBandIndex() != 10 &&
                    band.getSpectralBandIndex() != 13 &&
                    band.getSpectralBandIndex() != 14) {
                if (ReflectanceEnum.RADIANCE_REFLECTANCES.equals(outputL2RReflecAs)) {
                    // we have to divide by PI:
                    final MultiLevelImage sourceImage = band.getSourceImage();
                    final double constFactor = 1.0 / Math.PI;
                    final RenderedOp dividedByPiImage =
                            MultiplyConstDescriptor.create(sourceImage, new double[]{constFactor}, null);
                    ProductUtils.copyBand(band.getName(), rad2reflProduct, l2rProduct, false);
                    l2rProduct.getBand(band.getName()).setSourceImage(dividedByPiImage);
                    l2rProduct.getBand(band.getName()).setUnit("sr^-1");
                } else {
                    // rad2refl output is already multiplied by PI
                    ProductUtils.copyBand(band.getName(), rad2reflProduct, l2rProduct, true);
                    l2rProduct.getBand(band.getName()).setUnit("dl");
                }
            }
        }
    }

    private String getAutogroupingPattern() {
        final List<String> groupList = new ArrayList<String>();
        if (outputL2RToa) {
            groupList.add("rho_toa");
        }
        groupList.add("reflec");
//        groupList.add("norm_reflec");  // CB, 20140414
        groupList.add("norm_reflec");  // CB, 20140415
        final StringBuilder sb = new StringBuilder();
        final Iterator<String> iterator = groupList.iterator();
        while (iterator.hasNext()) {
            sb.append(iterator.next());
            if (iterator.hasNext()) {
                sb.append(":");
            }
        }
        return sb.toString();
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
