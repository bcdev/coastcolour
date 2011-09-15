package org.esa.beam.coastcolour.processing;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.atmosphere.operator.GlintCorrection;
import org.esa.beam.atmosphere.operator.GlintCorrectionOperator;
import org.esa.beam.atmosphere.operator.MerisFlightDirection;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.operators.CloudScreeningSelector;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.VirtualBandOpImage;
import org.esa.beam.meris.case2.Case2AlgorithmEnum;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.ResourceInstaller;

import java.awt.Rectangle;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L2W",
                  version = "1.3")
public class L2WOp extends Operator {

    private static final String L2W_FLAGS_NAME = "l2w_flags";
    private static final String CASE2_FLAGS_NAME = "case2_flags";
    private static final int[] FLH_INPUT_BAND_NUMBERS = new int[]{6, 8, 10};
    private static final String EXP_FLH_681_NAME = "exp_FLH_681";
    private static final String EXP_FLH_681_NORM_NAME = "exp_FLH_681_norm";
    private static final String EXP_FLH_681_ALT_NAME = "exp_FLH_681_alt";
    private static final String EXP_FLH_NORM_OLD_681_NAME = "exp_FLH_norm_old_681";
    private static final String EXP_FLH_ALT_OLD_681_NAME = "exp_FLH_alt_old_681";
    private static final String[] IOP_SOURCE_BAND_NAMES = new String[]{
            "a_total_443",
            "a_ys_443",
            "a_pig_443",
            "bb_spm_443"
    };

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

    @Parameter(label = "Bright Test Threshold ", defaultValue = "0.03")
    private double brightTestThreshold;

    @Parameter(label = "Bright Test Reference Wavelength [nm]", defaultValue = "865",
               valueSet = {
                       "412", "442", "490", "510", "560",
                       "620", "665", "681", "705", "753",
                       "760", "775", "865", "890", "900"
               })
    private int brightTestWavelength;


    @Parameter(label = "Average salinity", defaultValue = "35", unit = "PSU",
               description = "The average salinity of the water in the region to be processed.")
    private double averageSalinity;

    @Parameter(label = "Average temperature", defaultValue = "15", unit = "°C",
               description = "The average temperature of the water in the region to be processed.")
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

    @Parameter(label = "Alternative inverse water neural net (optional)",
               description = "The file of the inverse water neural net to be used instead of the default.")
    private File inverseWaterNnFile;

    @Parameter(label = "Alternative forward water neural net (optional)",
               description = "The file of the forward water neural net to be used instead of the default.")
    private File forwardWaterNnFile;

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

    @Parameter(defaultValue = "false", label = "Output A_Poc",
               description = "Toggles the output of absorption by particulate organic matter.")
    private boolean outputAPoc;

    @Parameter(defaultValue = "true", label = "Output Kd spectrum",
               description = "Toggles the output of downwelling irradiance attenuation coefficients. " +
                             "If disabled only Kd_490 is added to the output.")
    private boolean outputKdSpectrum;

    @Parameter(defaultValue = "false", label = "Output experimental FLH",
               description = "Toggles the output of the experimental fluorescence line height.")
    private boolean outputFLH;

    @Parameter(defaultValue = "false", label = "Use QAA for IOP and concentration computation",
               description = "If enabled IOPs are computed by QAA instead of Case-2-Regional. " +
                             "Concentrations of chlorophyll and total suspended matter will be derived from the IOPs.")
    private boolean useQaaForIops;

    private int nadirColumnIndex;
    private FLHAlgorithm flhAlgorithm;
    private Product l2rProduct;
    private Product qaaProduct;
    private Product case2rProduct;

    @Override
    public void initialize() throws OperatorException {
        if (outputFLH && isL2RSourceProduct(sourceProduct)) {
            throw new OperatorException("In order to compute 'FLH' the input must be L1B or L1P.");
        }
        l2rProduct = sourceProduct;
        if (!isL2RSourceProduct(l2rProduct)) {
            HashMap<String, Object> l2rParams = createL2RParameterMap();
            l2rProduct = GPF.createProduct("CoastColour.L2R", l2rParams, sourceProduct);
        }

        Case2AlgorithmEnum c2rAlgorithm = Case2AlgorithmEnum.REGIONAL;
        Operator case2Op = c2rAlgorithm.createOperatorInstance();

        setCase2rParameters(case2Op, c2rAlgorithm);

        case2rProduct = case2Op.getTargetProduct();

        if (useQaaForIops) {
            HashMap<String, Object> qaaParams = createQaaParameterMap();
            qaaProduct = GPF.createProduct("Meris.QaaIOP", qaaParams, sourceProduct);
        }

        final Product l2wProduct = createL2WProduct(l2rProduct, case2rProduct, qaaProduct);
        setTargetProduct(l2wProduct);
    }

    @Override
    public void dispose() {
        if (l2rProduct != sourceProduct) {
            l2rProduct.dispose();
            l2rProduct = null;
        }
        if (qaaProduct != null) {
            qaaProduct.dispose();
            qaaProduct = null;
        }
        if (case2rProduct != null) {
            case2rProduct.dispose();
            case2rProduct = null;
        }

        super.dispose();
    }

    private Product createL2WProduct(Product l2rProduct, Product case2rProduct, Product qaaProduct) {
        String l2wProductType = l2rProduct.getProductType().substring(0, 8) + "CCL2W";
        final int sceneWidth = case2rProduct.getSceneRasterWidth();
        final int sceneHeight = case2rProduct.getSceneRasterHeight();
        final Product l2wProduct = new Product(case2rProduct.getName(), l2wProductType, sceneWidth, sceneHeight);
        l2wProduct.setStartTime(case2rProduct.getStartTime());
        l2wProduct.setEndTime(case2rProduct.getEndTime());
        l2wProduct.setDescription("MERIS CoastColour L2W");
        ProductUtils.copyMetadata(case2rProduct, l2wProduct);
        copyMasks(case2rProduct, l2wProduct);
        copyMasks(l2rProduct, l2wProduct);
        if (qaaProduct == null) {
            copyIOPBands(case2rProduct, l2wProduct);
        } else {
            copyIOPBands(qaaProduct, l2wProduct);
            addChlAndTsmBands(l2wProduct);
        }

        copyBands(case2rProduct, l2wProduct);
        if (outputFLH) {
            addFLHBands(l2wProduct);
        }
        copyFlagBands(l2rProduct, l2wProduct);
        copyFlagBands(case2rProduct, l2wProduct);
        ProductUtils.copyTiePointGrids(case2rProduct, l2wProduct);
        renameIops(l2wProduct);
        renameConcentrations(l2wProduct);
        renameTurbidityBand(l2wProduct);
        copyReflecBandsIfRequired(l2rProduct, l2wProduct);
        sortFlagBands(l2wProduct);
        changeL2WMasksAndFlags(l2wProduct);
        ProductUtils.copyGeoCoding(case2rProduct, l2wProduct);

        return l2wProduct;
    }

    private void addChlAndTsmBands(Product l2wProduct) {
        final Band tsm = l2wProduct.addBand("tsm", ProductData.TYPE_FLOAT32);
        tsm.setDescription("Total suspended matter dry weight concentration.");
        tsm.setUnit("g m^-3");
        tsm.setValidPixelExpression("!l2w_flags.INVALID");
        final VirtualBandOpImage tsmImage = VirtualBandOpImage.create("1.73 * pow((bb_spm_443 / 0.01), 1.0)",
                                                                      ProductData.TYPE_FLOAT32, Double.NaN,
                                                                      qaaProduct, ResolutionLevel.MAXRES);

        tsm.setSourceImage(tsmImage);

        final Band conc_chl = l2wProduct.addBand("chl_conc", ProductData.TYPE_FLOAT32);
        conc_chl.setDescription("Chlorophyll concentration.");
        conc_chl.setUnit("mg m^-3");
        conc_chl.setValidPixelExpression("!l2w_flags.INVALID");
        final VirtualBandOpImage chlConcImage = VirtualBandOpImage.create("21.0 * pow(a_pig_443, 1.04)",
                                                                          ProductData.TYPE_FLOAT32, Double.NaN,
                                                                          qaaProduct, ResolutionLevel.MAXRES);
        conc_chl.setSourceImage(chlConcImage);

    }

    private void copyIOPBands(Product iopProduct, Product l2wProduct) {
        for (String iopSourceBandName : IOP_SOURCE_BAND_NAMES) {
            final Band targetBand = ProductUtils.copyBand(iopSourceBandName, iopProduct, l2wProduct);
            targetBand.setSourceImage(iopProduct.getBand(iopSourceBandName).getSourceImage());
            targetBand.setValidPixelExpression("!l2w_flags.INVALID");
        }
    }

    private void addFLHBands(Product l2WProduct) {
        Band flhBand = l2WProduct.addBand(EXP_FLH_681_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setDescription("Fluorescence line height at 681 nm");
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = l2WProduct.addBand(EXP_FLH_681_NORM_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = l2WProduct.addBand(EXP_FLH_681_ALT_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = l2WProduct.addBand(EXP_FLH_NORM_OLD_681_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setDescription("Fluorescence line height at 681 nm");
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = l2WProduct.addBand(EXP_FLH_ALT_OLD_681_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setDescription("Fluorescence line height at 681 nm");
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        addPatternToAutoGrouping(l2WProduct, "exp");
        nadirColumnIndex = MerisFlightDirection.findNadirColumnIndex(sourceProduct);
        float[] bandWavelengths = getWavelengths(FLH_INPUT_BAND_NUMBERS);
        flhAlgorithm = new FLHAlgorithm(bandWavelengths[0], bandWavelengths[1], bandWavelengths[2]);
    }

    private void copyBands(Product case2rProduct, Product l2wProduct) {
        final Band[] case2rBands = case2rProduct.getBands();
        for (Band band : case2rBands) {
            if (!band.isFlagBand() && !l2wProduct.containsBand(band.getName())) {
                final Band targetBand = ProductUtils.copyBand(band.getName(), case2rProduct, l2wProduct);
                targetBand.setSourceImage(band.getSourceImage());
            }
        }
    }

    private void copyFlagBands(Product case2rProduct, Product l2wProduct) {
        ProductUtils.copyFlagBands(case2rProduct, l2wProduct);
        final Band[] radiometryBands = case2rProduct.getBands();
        for (Band band : radiometryBands) {
            if (band.isFlagBand()) {
                final Band targetBand = l2wProduct.getBand(band.getName());
                targetBand.setSourceImage(band.getSourceImage());
            }
        }
    }


    private float[] getWavelengths(int[] flhInputBandNumbers) {
        float[] wavelengths = new float[flhInputBandNumbers.length];
        for (int i = 0; i < flhInputBandNumbers.length; i++) {
            Band band = l2rProduct.getBand("reflec_" + flhInputBandNumbers[i]);
            wavelengths[i] = band.getSpectralWavelength();
        }
        return wavelengths;
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws
                                                                                                             OperatorException {
        RasterDataNode satzenNode = l2rProduct.getRasterDataNode(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME);
        RasterDataNode solzenNode = l2rProduct.getRasterDataNode(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
        Tile satzen = getSourceTile(satzenNode, targetRectangle);
        Tile solzen = getSourceTile(solzenNode, targetRectangle);

        final Tile[] reflecTiles = getTiles(targetRectangle, "reflec_");
        final Tile[] tosaReflecTiles = getTiles(targetRectangle, "tosa_reflec_");
        final Tile[] pathTiles = getTiles(targetRectangle, "path_");
        final Tile[] transTiles = getTiles(targetRectangle, "trans_");
        Tile flhTile = targetTiles.get(getTargetProduct().getBand(EXP_FLH_681_NAME));
        Tile flhOldNormTile = targetTiles.get(getTargetProduct().getBand(EXP_FLH_NORM_OLD_681_NAME));
        Tile flhOldAltTile = targetTiles.get(getTargetProduct().getBand(EXP_FLH_ALT_OLD_681_NAME));
        Tile flhAltTile = targetTiles.get(getTargetProduct().getBand(EXP_FLH_681_ALT_NAME));
        Tile flhNormTile = targetTiles.get(getTargetProduct().getBand(EXP_FLH_681_NORM_NAME));

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                double[] flhValues = new double[5];
                if (!isSampleValid(reflecTiles[0], x, y)) {
                    Arrays.fill(flhValues, Double.NaN);
                } else {
                    double cosTetaViewSurfRad = getCosTetaViewSurfRad(satzen, x, y);
                    double cosTetaSunSurfRad = getCosTetaSunSurfRad(solzen, x, y);

                    double[] reflec = getValuesAt(x, y, reflecTiles);
                    double[] tosa = getValuesAt(x, y, tosaReflecTiles);
                    double[] path = getValuesAt(x, y, pathTiles);
                    double[] trans = getValuesAt(x, y, transTiles);
                    flhValues = flhAlgorithm.computeFLH681(reflec, tosa, path, trans,
                                                           cosTetaViewSurfRad, cosTetaSunSurfRad);

                }
                flhTile.setSample(x, y, flhValues[0]);
                flhOldNormTile.setSample(x, y, flhValues[1]);
                flhOldAltTile.setSample(x, y, flhValues[2]);
                flhNormTile.setSample(x, y, flhValues[3]);
                flhAltTile.setSample(x, y, flhValues[4]);
            }
        }
    }

    // this should be the real implementation of for computing FLH; but for testing we've implemented computeTileStack
//    @Override
//    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
//        final Rectangle rectangle = targetTile.getRectangle();
//        RasterDataNode satzenNode = l2rProduct.getRasterDataNode(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME);
//        RasterDataNode solzenNode = l2rProduct.getRasterDataNode(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
//        Tile satzen = getSourceTile(satzenNode, rectangle);
//        Tile solzen = getSourceTile(solzenNode, rectangle);
//
//        final Tile[] reflecTiles = getTiles(rectangle, "reflec_");
//        final Tile[] tosaReflecTiles = getTiles(rectangle, "tosa_reflec_");
//        final Tile[] pathTiles = getTiles(rectangle, "path_");
//        final Tile[] transTiles = getTiles(rectangle, "trans_");
//
//        for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
//            checkForCancellation();
//            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
//                float flh;
//                if(!isSampleValid(reflecTiles[0], x, y)) {
//                    flh = Float.NaN;
//                } else {
//                    double cosTetaViewSurfRad = getCosTetaViewSurfRad(satzen, x, y);
//                    double cosTetaSunSurfRad = getCosTetaSunSurfRad(solzen, x, y);
//
//                    double[] reflec = getValuesAt(x, y, reflecTiles);
//                    double[] tosa = getValuesAt(x, y, tosaReflecTiles);
//                    double[] path = getValuesAt(x, y, pathTiles);
//                    double[] trans = getValuesAt(x, y, transTiles);
//                    flh = (float) computeFLH681(reflec, tosa, path, trans, cosTetaViewSurfRad,
//                                               cosTetaSunSurfRad);
//                }
//                targetTile.setSample(x, y, flh);
//            }
//        }
//
//    }

    private boolean isSampleValid(Tile reflecTile, int x, int y) {
        return reflecTile.isSampleValid(x, y);
    }

    private double getCosTetaViewSurfRad(Tile satzen, int x, int y) {
        double tetaViewSurfDeg = GlintCorrection.correctViewAngle(satzen.getSampleDouble(x, y), x,
                                                                  nadirColumnIndex, true);
        double tetaViewSurfRad = Math.toRadians(tetaViewSurfDeg);
        return Math.cos(tetaViewSurfRad);
    }

    private double getCosTetaSunSurfRad(Tile solzen, int x, int y) {
        double tetaSunSurfDeg = solzen.getSampleDouble(x, y);
        double tetaSunSurfRad = Math.toRadians(tetaSunSurfDeg);
        return Math.cos(tetaSunSurfRad);
    }

    private double[] getValuesAt(int x, int y, Tile[] reflecTiles) {
        double[] values = new double[reflecTiles.length];
        for (int i = 0; i < reflecTiles.length; i++) {
            Tile reflecTile = reflecTiles[i];
            values[i] = reflecTile.getSampleDouble(x, y);
        }
        return values;
    }

    private Tile[] getTiles(Rectangle rectangle, String bandNamePrefix) {
        Tile[] tiles = new Tile[FLH_INPUT_BAND_NUMBERS.length];
        for (int i = 0; i < FLH_INPUT_BAND_NUMBERS.length; i++) {
            int flhInputBandIndex = FLH_INPUT_BAND_NUMBERS[i];
            tiles[i] = getSourceTile(l2rProduct.getBand(bandNamePrefix + flhInputBandIndex), rectangle);
        }
        return tiles;
    }

    private void setCase2rParameters(Operator case2Op, Case2AlgorithmEnum c2rAlgorithm) {
        case2Op.setParameter("forwardWaterNnFile", forwardWaterNnFile);
        case2Op.setParameter("inverseWaterNnFile", inverseWaterNnFile);
        case2Op.setParameter("averageSalinity", averageSalinity);
        case2Op.setParameter("averageTemperature", averageTemperature);
        case2Op.setParameter("tsmConversionExponent", c2rAlgorithm.getDefaultTsmExponent());
        case2Op.setParameter("tsmConversionFactor", c2rAlgorithm.getDefaultTsmFactor());
        case2Op.setParameter("chlConversionExponent", c2rAlgorithm.getDefaultChlExponent());
        case2Op.setParameter("chlConversionFactor", c2rAlgorithm.getDefaultChlFactor());
        case2Op.setParameter("outputKdSpectrum", outputKdSpectrum);
        case2Op.setParameter("outputAPoc", outputAPoc);
        case2Op.setParameter("inputReflecAre", "RADIANCE_REFLECTANCES");
        case2Op.setParameter("invalidPixelExpression", invalidPixelExpression);
        case2Op.setSourceProduct("acProduct", l2rProduct);
    }

    private HashMap<String, Object> createQaaParameterMap() {
        HashMap<String, Object> qaaParams = new HashMap<String, Object>();
        qaaParams.put("invalidPixelExpression", invalidPixelExpression);
        qaaParams.put("divideByPI", false); // L2R are already radiance reflectances; not need to divide by PI
        return qaaParams;
    }

    private HashMap<String, Object> createL2RParameterMap() {
        HashMap<String, Object> l2rParams = createBaseL2RParameterMap();
        if (outputFLH) {
            l2rParams.put("outputTosa", true);
            l2rParams.put("outputTransmittance", true);
            l2rParams.put("outputPath", true);
        }
        return l2rParams;
    }

    private HashMap<String, Object> createBaseL2RParameterMap() {
        HashMap<String, Object> l2rParams = new HashMap<String, Object>();
        l2rParams.put("doCalibration", doCalibration);
        l2rParams.put("doSmile", doSmile);
        l2rParams.put("doEqualization", doEqualization);
        l2rParams.put("useIdepix", true);
        l2rParams.put("algorithm", CloudScreeningSelector.CoastColour);
        l2rParams.put("brightTestThreshold", brightTestThreshold);
        l2rParams.put("brightTestWavelength", brightTestWavelength);
        l2rParams.put("averageSalinity", averageSalinity);
        l2rParams.put("averageTemperature", averageTemperature);
        l2rParams.put("atmoNetMerisFile", atmoNetMerisFile);
        l2rParams.put("autoassociativeNetFile", autoassociativeNetFile);
        l2rParams.put("landExpression", landExpression);
        l2rParams.put("cloudIceExpression", cloudIceExpression);
        l2rParams.put("outputNormReflec", true);
        l2rParams.put("outputReflecAs", "RADIANCE_REFLECTANCES");
        return l2rParams;
    }

    private void changeL2WMasksAndFlags(Product targetProduct) {
        ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        FlagCoding l2wFlags = flagCodingGroup.get(CASE2_FLAGS_NAME);
        l2wFlags.setName(L2W_FLAGS_NAME);
        l2wFlags.removeAttribute(l2wFlags.getFlag("FIT_FAILED"));
        Band band = targetProduct.getBand(CASE2_FLAGS_NAME);
        band.setName(L2W_FLAGS_NAME);

        ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();

        Mask fit_failed = maskGroup.get("case2_fit_failed");
        maskGroup.remove(fit_failed);

        int insertIndex = 0;
        String wlrOorDescription = "Water leaving reflectance out of training range";
        Mask wlr_oor = updateMask(maskGroup, "case2_wlr_oor", "l2w_cc_wlr_ootr", wlrOorDescription);
        reorderMask(maskGroup, wlr_oor, insertIndex);
        l2wFlags.getFlag("WLR_OOR").setDescription(wlrOorDescription);

        String concOorDescription = "Water constituents out of training range";
        Mask conc_oor = updateMask(maskGroup, "case2_conc_oor", "l2w_cc_conc_ootr", concOorDescription);
        reorderMask(maskGroup, conc_oor, ++insertIndex);
        l2wFlags.getFlag("CONC_OOR").setDescription(concOorDescription);

        String ootrDescription = "Spectrum out of training range (chiSquare threshold)";
        Mask ootr = updateMask(maskGroup, "case2_ootr", "l2w_cc_ootr", ootrDescription);
        reorderMask(maskGroup, ootr, ++insertIndex);
        l2wFlags.getFlag("OOTR").setDescription(ootrDescription);

        String whitecapsDescription = "Risk for white caps";
        Mask whitecaps = updateMask(maskGroup, "case2_whitecaps", "l2w_cc_whitecaps", whitecapsDescription);
        reorderMask(maskGroup, whitecaps, ++insertIndex);
        l2wFlags.getFlag("WHITECAPS").setDescription(whitecapsDescription);

        String invalidDescription = "Invalid pixels (" + invalidPixelExpression + " || l2w_flags.OOTR)";
        Mask invalid = updateMask(maskGroup, "case2_invalid", "l2w_cc_invalid", invalidDescription);
        reorderMask(maskGroup, invalid, ++insertIndex);
        Mask.BandMathsType.setExpression(invalid, invalidPixelExpression + " || l2w_flags.OOTR");
        l2wFlags.getFlag("INVALID").setDescription(invalidDescription);
    }

    private void reorderMask(ProductNodeGroup<Mask> maskGroup, Mask wlr_oor, int newIndex) {
        maskGroup.remove(wlr_oor);
        maskGroup.add(newIndex, wlr_oor);
    }

    private Mask updateMask(ProductNodeGroup<Mask> maskGroup, String oldMaskName, String newMaskName,
                            String description) {
        Mask mask = maskGroup.get(oldMaskName);
        mask.setName(newMaskName);
        mask.setDescription(description);
        return mask;
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

    private void renameTurbidityBand(Product targetProduct) {
        targetProduct.getBand("turbidity_index").setName("turbidity");
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
        Band aPocBand = targetProduct.getBand(aPoc);
        if (aPocBand != null) {
            aPocBand.setName("iop_" + aPoc);
        }
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
            AuxdataInstaller.installAuxdata(ResourceInstaller.getSourceUrl(L2WOp.class));
        }
    }
}
