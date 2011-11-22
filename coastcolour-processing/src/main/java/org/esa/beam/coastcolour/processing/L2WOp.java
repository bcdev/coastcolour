package org.esa.beam.coastcolour.processing;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.atmosphere.operator.GlintCorrection;
import org.esa.beam.atmosphere.operator.GlintCorrectionOperator;
import org.esa.beam.atmosphere.operator.MerisFlightDirection;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
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
import org.esa.beam.meris.case2.algorithm.KMin;
import org.esa.beam.meris.case2.water.WaterAlgorithm;
import org.esa.beam.util.ResourceInstaller;

import java.awt.Rectangle;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L2W", version = "1.4",
                  authors = "Marco Peters, Norman Fomferra",
                  copyright = "(c) 2011 Brockmann Consult",
                  description = "Computes information about water properties such as IOPs, concentrations and " +
                                "other variables")
public class L2WOp extends Operator {

    private static final int[] FLH_INPUT_BAND_NUMBERS = new int[]{6, 8, 10};
    private static final double TURBIDITY_RLW620_MAX = 0.03823;
    private static final double TURBIDITY_AT = 174.41;
    private static final double TURBIDITY_BT = 0.39;
    private static final double TURBIDITY_C = 0.1533;


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
               description = "Expression defining pixels not considered for L2W processing")
    private String invalidPixelExpression;

    @Parameter(defaultValue = "false", label = "Output water leaving reflectance",
               description = "Toggles the output of water leaving reflectance.")
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

    @Parameter(defaultValue = "-0.02", label = "'A_TOTAL' lower bound",
               description = "The lower bound of the valid value range.")
    private float qaaATotalLower;
    @Parameter(defaultValue = "5.0", label = "'A_TOTAL' upper bound",
               description = "The upper bound of the valid value range.")
    private float qaaATotalUpper;
    @Parameter(defaultValue = "-0.2", label = "'BB_SPM' lower bound",
               description = "The lower bound of the valid value range.")
    private float qaaBbSpmLower;
    @Parameter(defaultValue = "5.0", label = "'BB_SPM' upper bound",
               description = "The upper bound of the valid value range.")
    private float qaaBbSpmUpper;
    @Parameter(defaultValue = "-0.02", label = "'A_PIG' lower bound",
               description = "The lower bound of the valid value range.")
    private float qaaAPigLower;
    @Parameter(defaultValue = "3.0", label = "'A_PIG' upper bound",
               description = "The upper bound of the valid value range.")
    private float qaaAPigUpper;
    @Parameter(defaultValue = "1.0", label = "'A_YS' upper bound",
               description = "The upper bound of the valid value range. The lower bound is always 0.")
    private float aYsUpper;
    @Parameter(defaultValue = "false", label = "Divide source Rrs by PI(3.14)",
               description = "If selected the source remote reflectances are divided by PI.")
    private boolean qaaDivideByPI;

    private int nadirColumnIndex;
    private FLHAlgorithm flhAlgorithm;
    private Product l2rProduct;
    private Product qaaProduct;
    private Product case2rProduct;
    private VirtualBandOpImage invalidOpImage;

    @Override
    public void initialize() throws OperatorException {
        if (outputFLH && isL2RSourceProduct(sourceProduct)) {
            throw new OperatorException("In order to compute 'FLH' the input must be L1B or L1P.");
        }
        nadirColumnIndex = MerisFlightDirection.findNadirColumnIndex(sourceProduct);

        l2rProduct = sourceProduct;
        if (!isL2RSourceProduct(l2rProduct)) {
            HashMap<String, Object> l2rParams = createL2RParameterMap();
            l2rProduct = GPF.createProduct("CoastColour.L2R", l2rParams, sourceProduct);
        }

        if (outputFLH) {
            float[] bandWavelengths = getWavelengths(l2rProduct, FLH_INPUT_BAND_NUMBERS);
            flhAlgorithm = new FLHAlgorithm(bandWavelengths[0], bandWavelengths[1], bandWavelengths[2]);
        }

        Case2AlgorithmEnum c2rAlgorithm = Case2AlgorithmEnum.REGIONAL;
        Operator case2Op = c2rAlgorithm.createOperatorInstance();

        setCase2rParameters(case2Op, c2rAlgorithm);

        case2rProduct = case2Op.getTargetProduct();

        invalidOpImage = VirtualBandOpImage.createMask(invalidPixelExpression,
                                                       l2rProduct,
                                                       ResolutionLevel.MAXRES);

        final L2WProductFactory l2wProductFactory;
        if (useQaaForIops) {
            HashMap<String, Object> qaaParams = createQaaParameterMap();
            qaaProduct = GPF.createProduct("Meris.QaaIOP", qaaParams, l2rProduct);
            l2wProductFactory = new QaaL2WProductFactory(l2rProduct, qaaProduct);
        } else {
            l2wProductFactory = new Case2rL2WProductFactory(l2rProduct, case2rProduct);
        }

        l2wProductFactory.setInvalidPixelExpression(invalidPixelExpression);
        l2wProductFactory.setOutputFLH(outputFLH);
        l2wProductFactory.setOutputKdSpectrum(outputKdSpectrum);
        l2wProductFactory.setOutputReflectance(outputReflec);

        setTargetProduct(l2wProductFactory.createL2WProduct());
    }

    @Override
    public void dispose() {
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


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws
                                                                                                             OperatorException {
        Tile satzen = null;
        Tile solzen = null;
        Tile[] reflecTiles = null;
        Tile[] tosaReflecTiles = null;
        Tile[] pathTiles = null;
        Tile[] transTiles = null;
        Tile flhTile = null;
        Tile flhOldNormTile = null;
        Tile flhOldAltTile = null;
        Tile flhAltTile = null;
        Tile flhNormTile = null;
        final Product targetProduct = getTargetProduct();
        if (outputFLH) {
            RasterDataNode satzenNode = l2rProduct.getRasterDataNode(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME);
            RasterDataNode solzenNode = l2rProduct.getRasterDataNode(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
            satzen = getSourceTile(satzenNode, targetRectangle);
            solzen = getSourceTile(solzenNode, targetRectangle);

            reflecTiles = getTiles(targetRectangle, "reflec_");
            tosaReflecTiles = getTiles(targetRectangle, "tosa_reflec_");
            pathTiles = getTiles(targetRectangle, "path_");
            transTiles = getTiles(targetRectangle, "trans_");
            flhTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.EXP_FLH_681_NAME));
            flhOldNormTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.EXP_FLH_NORM_OLD_681_NAME));
            flhOldAltTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.EXP_FLH_ALT_OLD_681_NAME));
            flhAltTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.EXP_FLH_681_ALT_NAME));
            flhNormTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.EXP_FLH_681_NORM_NAME));
        }

        Tile l2wFlagTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.L2W_FLAGS_NAME));
        Tile c2rFlags = null;
        Tile qaaFlags = null;
        if (qaaProduct != null) {
            qaaFlags = getSourceTile(qaaProduct.getRasterDataNode("analytical_flags"), targetRectangle);
        } else {
            c2rFlags = getSourceTile(case2rProduct.getRasterDataNode("case2_flags"), targetRectangle);
        }
        final Tile rho620Tile = getSourceTile(l2rProduct.getBand("reflec_6"), targetRectangle);

        // Maybe problematic: get a source tile from the target product. Weird.
        final Tile bbSPM443Tile = getSourceTile(targetProduct.getBand(L2WProductFactory.IOP_BB_SPM_443_NAME),
                                                targetRectangle);
        final Tile aPig443Tile = getSourceTile(targetProduct.getBand(L2WProductFactory.IOP_A_PIG_443_NAME),
                                               targetRectangle);
        final Tile aYs443Tile = getSourceTile(targetProduct.getBand(L2WProductFactory.IOP_A_YS_443_NAME),
                                              targetRectangle);

        final Tile kMinTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.K_MIN_NAME));
        Tile[] kdTiles = new Tile[L2WProductFactory.KD_LAMBDAS.length];
        for (int i = 0; i < L2WProductFactory.KD_LAMBDAS.length; i++) {
            kdTiles[i] = targetTiles.get(targetProduct.getBand("Kd_" + L2WProductFactory.KD_LAMBDAS[i]));
        }

        final Tile z90Tile = targetTiles.get(targetProduct.getBand(L2WProductFactory.Z90_MAX_NAME));
        final Tile turbidityTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.TURBIDITY_NAME));

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                final boolean isSampleInvalid = isSampleInvalid(x, y);
                if (outputFLH && !isSampleInvalid) {
                    computeFLHValues(x, y, satzen, solzen, reflecTiles, tosaReflecTiles, pathTiles, transTiles, flhTile,
                                     flhOldNormTile, flhOldAltTile, flhAltTile, flhNormTile, isSampleInvalid);
                }

                if (useQaaForIops) {
                    final double bTsm443 = bbSPM443Tile.getSampleDouble(x, y) / WaterAlgorithm.BTSM_TO_SPM_FACTOR;
                    final double aPig443 = aPig443Tile.getSampleDouble(x, y);
                    final double aYs443 = aYs443Tile.getSampleDouble(x, y);
                    KMin kMin = new KMin(bTsm443, aPig443, aYs443);
                    final double kMinValue = kMin.computeKMinValue();
                    kMinTile.setSample(x, y, isSampleInvalid ? Double.NaN : kMinValue);
                    if (outputKdSpectrum) {
                        double[] kds = new double[kdTiles.length];
                        if (isSampleInvalid) {
                            Arrays.fill(kds, Double.NaN);
                        } else {
                            kds = kMin.computeKdSpectrum();
                        }
                        for (int i = 0; i < kds.length; i++) {
                            kdTiles[i].setSample(x, y, kds[i]);
                        }
                    } else {
                        kdTiles[2].setSample(x, y, isSampleInvalid ? Double.NaN : kMin.computeKd490());
                    }
                    z90Tile.setSample(x, y, isSampleInvalid ? Double.NaN : -1 / kMinValue);
                    final double turbidityValue = computeTurbidity(rho620Tile.getSampleDouble(x, y));
                    turbidityTile.setSample(x, y, isSampleInvalid ? Double.NaN : turbidityValue);
                }
                final int invalidFlagValue = isSampleInvalid ? 1 : 0;
                int l2wFlag = computeL2wFlags(x, y, c2rFlags, qaaFlags, invalidFlagValue);
                l2wFlagTile.setSample(x, y, l2wFlag);
            }
        }
    }

    private int computeL2wFlags(int x, int y, Tile c2rFlags, Tile qaaFlags, int invalidFlagValue) {
        int l2wFlag = 0;
        if (c2rFlags != null) {
            final int sampleInt = c2rFlags.getSampleInt(x, y);
            l2wFlag = sampleInt & 0x0F;
        }
        if (qaaFlags != null) {
            l2wFlag = qaaFlags.getSampleInt(x, y) & 0x30;
        }

        l2wFlag = l2wFlag | (invalidFlagValue << 7);

        return l2wFlag;
    }

    private double computeTurbidity(double rlw620) {
        if (rlw620 > TURBIDITY_RLW620_MAX) {  // maximum value for computing the turbidity Index
            rlw620 = TURBIDITY_RLW620_MAX;
        }
        double rho = rlw620 * Math.PI;
        return TURBIDITY_AT * rho / (1 - rho / TURBIDITY_C) + TURBIDITY_BT;
    }

    private boolean isSampleInvalid(int x, int y) {
        return invalidOpImage.getData(new Rectangle(x, y, 1, 1)).getSample(x, y, 0) != 0;
    }

    private void computeFLHValues(int x, int y, Tile satzen, Tile solzen, Tile[] reflecTiles, Tile[] tosaReflecTiles,
                                  Tile[] pathTiles, Tile[] transTiles, Tile flhTile, Tile flhOldNormTile,
                                  Tile flhOldAltTile, Tile flhAltTile, Tile flhNormTile, boolean sampleInvalid) {
        double[] flhValues = new double[5];
        if (sampleInvalid) {
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

    private float[] getWavelengths(Product l2rProduct, int[] flhInputBandNumbers) {
        float[] wavelengths = new float[flhInputBandNumbers.length];
        for (int i = 0; i < flhInputBandNumbers.length; i++) {
            Band band = l2rProduct.getBand("reflec_" + flhInputBandNumbers[i]);
            wavelengths[i] = band.getSpectralWavelength();
        }
        return wavelengths;
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
        qaaParams.put("aTotalLower", qaaATotalLower);
        qaaParams.put("aTotalUpper", qaaATotalUpper);
        qaaParams.put("bbSpmLower", qaaBbSpmLower);
        qaaParams.put("bbSpmUpper", qaaBbSpmUpper);
        qaaParams.put("aPigLower", qaaAPigLower);
        qaaParams.put("aPigUpper", qaaAPigUpper);
        qaaParams.put("divideByPI", qaaDivideByPI);
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

    private boolean isL2RSourceProduct(Product sourceProduct) {
        return sourceProduct.containsBand("l2r_flags");
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L2WOp.class);
            AuxdataInstaller.installAuxdata(ResourceInstaller.getSourceUrl(L2WOp.class));
        }
    }
}
