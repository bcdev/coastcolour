package org.esa.beam.coastcolour.processing;

import org.esa.beam.atmosphere.operator.GlintCorrectionOperator;
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
import org.esa.beam.util.ResourceInstaller;

import java.io.File;
import java.util.HashMap;

@OperatorMetadata(alias = "CoastColour.L2R",
                  version = "1.3")
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

    @Parameter(defaultValue = "true",
               label = "Perform equalization",
               description = "Perform removal of detector-to-detector systematic radiometric differences in MERIS L1b data products.")
    private boolean doEqualization;

    @Parameter(defaultValue = "true")
    private boolean useIdepix;

    @Parameter(defaultValue = "CoastColour", valueSet = {"GlobAlbedo", "QWG", "CoastColour"})
    private CloudScreeningSelector algorithm;

    @Parameter(label = "Average salinity", defaultValue = "35", unit = "PSU", description = "The salinity of the water")
    private double averageSalinity;

    @Parameter(label = "Average temperature", defaultValue = "15", unit = "°C", description = "The Water temperature")
    private double averageTemperature;

    @Parameter(label = "MERIS net (full path required for other than default)",
               defaultValue = GlintCorrectionOperator.MERIS_ATMOSPHERIC_NET_NAME,
               description = "The file of the atmospheric net to be used instead of the default neural net.",
               notNull = false)
    private File atmoNetMerisFile;

    @Parameter(label = "Autoassociatve net (full path required for other than default)",
               defaultValue = GlintCorrectionOperator.ATMO_AANN_NET,
               description = "The file of the autoassociative net used for error computed instead of the default neural net.",
               notNull = false)
    private File autoassociativeNetFile;

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

    @Parameter(label = "Bright Test Threshold ", defaultValue = "0.03")
    private double brightTestThreshold;

    @Parameter(label = "Bright Test Reference Wavelength [nm]", defaultValue = "865",
               valueSet = {
                       "412", "442", "490", "510", "560",
                       "620", "665", "681", "705", "753",
                       "760", "775", "865", "890", "900"
               })
    private int brightTestWavelength;

    @Parameter(defaultValue = "false", label = "Output TOSA reflectance",
               description = "Toggles the output of Top of Standard Atmosphere reflectance.")
    private boolean outputTosa;

    @Parameter(defaultValue = "false", label = "Output path reflectance",
               description = "Toggles the output of water leaving path reflectance.")
    private boolean outputPath;

    @Parameter(defaultValue = "false", label = "Output transmittance",
               description = "Toggles the output of downwelling irradiance transmittance.")
    private boolean outputTransmittance;


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
            l1pParams.put("brightTestThreshold", brightTestThreshold);
            l1pParams.put("brightTestWavelength", brightTestWavelength);
            sourceProduct = GPF.createProduct("CoastColour.L1P", l1pParams, sourceProduct);
        }

        HashMap<String, Product> sourceProducts = new HashMap<String, Product>();
        sourceProducts.put("merisProduct", sourceProduct);

        HashMap<String, Object> glintParameters = new HashMap<String, Object>();
        glintParameters.put("doSmileCorrection", false);
        glintParameters.put("outputTosa", outputTosa);
        glintParameters.put("outputReflec", true);
        glintParameters.put("outputNormReflec", true);
        glintParameters.put("outputReflecAs", "RADIANCE_REFLECTANCES");
        glintParameters.put("outputPath", outputPath);
        glintParameters.put("outputTransmittance", outputTransmittance);
        glintParameters.put("deriveRwFromPath", false);
        glintParameters.put("averageSalinity", averageSalinity);
        glintParameters.put("averageTemperature", averageTemperature);
        glintParameters.put("atmoNetMerisFile", atmoNetMerisFile);
        glintParameters.put("autoassociativeNetFile", autoassociativeNetFile);
        glintParameters.put("landExpression", landExpression);
        glintParameters.put("cloudIceExpression", cloudIceExpression);
        glintParameters.put("useFlint", false);

        Product glintProduct = GPF.createProduct("Meris.GlintCorrection", glintParameters, sourceProducts);

        // need a copy of the product in order to make changes to flag names
        // otherwise a GlintCorrection can't handle the changed target product anymore
        Product targetProduct = GPF.createProduct("Subset", GPF.NO_PARAMS, glintProduct);
        targetProduct.setAutoGrouping(glintProduct.getAutoGrouping());
        changeAgcFlags(targetProduct);
        removeFlagsAndMasks(targetProduct);
        sortMasks(targetProduct);
        sortFlagBands(targetProduct);
        renameTauBands(targetProduct);
        removeUnwantedBands(targetProduct);
        String l1pProductType = sourceProduct.getProductType().substring(0, 8) + "CCL2R";
        targetProduct.setProductType(l1pProductType);
        targetProduct.setDescription("MERIS CoastColour L2R");
        setTargetProduct(targetProduct);
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
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_CLOUD_BUFFER_FLAG_NAME.toLowerCase(), 3);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_CLOUD_SHADOW_FLAG_NAME.toLowerCase(), 4);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_SNOW_ICE_FLAG_NAME.toLowerCase(), 5);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_LANDRISK_FLAG_NAME.toLowerCase(), 6);
        moveMaskAtIndex(maskGroup, l1pMaskPrefix + L1POp.CC_GLINTRISK_FLAG_NAME.toLowerCase(), 7);

    }

    private void moveMaskAtIndex(ProductNodeGroup<Mask> maskGroup, String maskName, int index) {
        Mask mask = maskGroup.get(maskName);
        maskGroup.remove(mask);
        maskGroup.add(index, mask);
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
        String invalidDescr = "Invalid pixels (" + landExpression + " || " + cloudIceExpression + " || l1_flags.INVALID)";
        l2rFlags.getFlag("INVALID").setDescription(invalidDescr);
        String glintDescription = "High sun glint retrieved";
        l2rFlags.getFlag("SUNGLINT").setDescription(glintDescription);
        String toaDescription = "TOA reflectance out of range";
        l2rFlags.getFlag("TOA_OOR").setDescription(toaDescription);
        String tosaDescription = "TOSA reflectance out of range";
        l2rFlags.getFlag("TOSA_OOR").setDescription(tosaDescription);
        ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();
        maskGroup.remove(maskGroup.get("agc_land"));
        maskGroup.remove(maskGroup.get("cloud_ice"));
        maskGroup.remove(maskGroup.get("has_flint"));
        maskGroup.get("atc_oor").setName("l2r_cc_atc_oor");
        maskGroup.get("toa_oor").setDescription(toaDescription);
        maskGroup.get("toa_oor").setName("l2r_cc_toa_oor");
        maskGroup.get("tosa_oor").setDescription(tosaDescription);
        maskGroup.get("tosa_oor").setName("l2r_cc_tosa_oor");
        maskGroup.get("solzen").setName("l2r_cc_solzen");
        maskGroup.get("ancil").setName("l2r_cc_ancil");
        maskGroup.get("sunglint").setDescription(glintDescription);
        maskGroup.get("sunglint").setName("l2r_cc_sunglint");
        maskGroup.get("agc_invalid").setDescription(invalidDescr);
        maskGroup.get("agc_invalid").setName("l2r_cc_invalid");
    }


    private boolean isL1PSourceProduct(Product sourceProduct) {
        return sourceProduct.containsBand("l1p_flags");
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L2ROp.class);
            AuxdataInstaller.installAuxdata(ResourceInstaller.getSourceUrl(L2ROp.class));
        }


    }
}
