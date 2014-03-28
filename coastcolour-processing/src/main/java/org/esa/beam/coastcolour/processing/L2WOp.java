package org.esa.beam.coastcolour.processing;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.atmosphere.operator.GlintCorrection;
import org.esa.beam.atmosphere.operator.GlintCorrectionOperator;
import org.esa.beam.atmosphere.operator.MerisFlightDirection;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.VirtualBandOpImage;
import org.esa.beam.meris.case2.RegionalWaterOp;
import org.esa.beam.meris.case2.water.WaterAlgorithm;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;

import java.awt.Rectangle;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L2W",
                  version = "1.6.7",
                  authors = "Marco Peters, Norman Fomferra",
                  copyright = "(c) 2011 Brockmann Consult",
                  description = "Computes information about water properties such as IOPs, concentrations and " +
                                "other variables")
public class L2WOp extends Operator {

    private static final int[] FLH_INPUT_BAND_NUMBERS = new int[]{6, 8, 10};
    private static final int[] REFLEC_BAND_NUMBERS = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    private static final double TURBIDITY_RLW620_MAX = 0.03823;
    private static final double TURBIDITY_AT = 174.41;
    private static final double TURBIDITY_BT = 0.39;
    private static final double TURBIDITY_C = 0.1533;

    // compile time switch (RD)
    static final boolean ENABLE_OWT_CONC_BANDS = false;

    @SourceProduct(description = "MERIS L1B, L1P or L2R product")
    private Product sourceProduct;

    //@SourceProduct(description = "Class membership product from Fuzzy classification (FuzzyOp)", optional = true)
    private Product classMembershipProduct;

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

    @Parameter(label = "MERIS net (full path required for other than default)",
               defaultValue = GlintCorrectionOperator.MERIS_ATMOSPHERIC_EXTREME_NET_NAME,
               description = "The file of the atmospheric net to be used instead of the default neural net.",
               notNull = false)
    private File atmoNetMerisFile;

    @Parameter(label = "Autoassociatve net (full path required for other than default)",
               defaultValue = GlintCorrectionOperator.ATMO_AANN_EXTREME_NET_NAME,
               description = "The file of the autoassociative net used for error computed instead of the default neural net.",
               notNull = false)
    private File autoassociativeNetFile;

    @Parameter(label = "Alternative inverse IOP neural net (optional)",
               description = "The file of the inverse IOP neural net to be used instead of the default.")
    private File inverseIopNnFile;
    @Parameter(label = "Alternative inverse Kd neural net (optional)",
               description = "The file of the inverse Kd neural net to be used instead of the default.")
    private File inverseKdNnFile;
    @Parameter(label = "Alternative forward IOP neural net (optional)",
               description = "The file of the forward IOP neural net to be used instead of the default.")
    private File forwardIopNnFile;

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

    @Parameter(defaultValue = "l2r_flags.INPUT_INVALID",
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
    private float qaaAYsUpper;
    @Parameter(defaultValue = "false", label = "Divide source Rrs by PI(3.14)",
               description = "If selected the source remote reflectances are divided by PI.")
    private boolean qaaDivideByPI;

    @Parameter(defaultValue = "0.8", label = "Membership class sum threshold",
               description = "Threshold for the sum of the class memberships.")
    private double membershipClassSumThresh;

    private int nadirColumnIndex;
    private FLHAlgorithm flhAlgorithm;
    private Product l2rProduct;
    private Product qaaProduct;
    private Product case2rProduct;
    private VirtualBandOpImage invalidOpImage;
    private VirtualBandOpImage invalidL2wImage;

    private static final String[] iopForwardNets =
            new String[]{
                    "m1/for_iop_meris_b12/17x27x17_33.8.net",
                    "m2/for_iop_meris_b12/17x27x17_15.8.net",
                    "m3/for_iop_meris_b12/17x27x17_20.5.net",
                    "m4/for_iop_meris_b12/17x27x17_20.7.net",
                    "m5/for_iop_meris_b12/17x27x17_91.3.net",
                    "m6/for_iop_meris_b12/17x27x17_50.0.net",
                    "m7/for_iop_meris_b12/17x27x17_30.5.net",
                    "m8/for_iop_meris_b12/17x27x17_30.1.net",
                    "m9/for_iop_meris_b12/17x27x17_180.1.net"
            };
    private static final String[] iopInverseNets =
            new String[]{
                    "m1/inv_iop_meris_b9/27x41x27_1483.8.net",
                    "m2/inv_iop_meris_b9/27x41x27_263.7.net",
                    "m3/inv_iop_meris_b9/27x41x27_228.8.net",
                    "m4/inv_iop_meris_b10/27x41x27_121.7.net",
                    "m5/inv_iop_meris_b10/27x41x27_4667.9.net",
                    "m6/inv_iop_meris_b10/27x41x27_200.6.net",
                    "m7/inv_iop_meris_b10/27x41x27_164.8.net",
                    "m8/inv_iop_meris_b10/27x41x27_159.1.net",
                    "m9/inv_iop_meris_b10/27x41x27_6696.1.net"
            };
    private static final String[] kdInverseNets =
            new String[]{
                    "m1/inv_kd_meris_b8/27x41x27_51.3.net",
                    "m2/inv_kd_meris_b8/27x41x27_15.2.net",
                    "m3/inv_kd_meris_b8/27x41x27_15.1.net",
                    "m4/inv_kd_meris_b9/27x41x27_8.3.net",
                    "m5/inv_kd_meris_b9/27x41x27_68.4.net",
                    "m6/inv_kd_meris_b9/27x41x27_4.1.net",
                    "m7/inv_kd_meris_b9/27x41x27_3.5.net",
                    "m8/inv_kd_meris_b9/27x41x27_7.6.net",
                    "m9/inv_kd_meris_b9/27x41x27_432.7.net"
            };
    private static final int NUMBER_OF_WATER_NETS = iopForwardNets.length;

    private Product[] c2rSingleProducts;
    public static final int NUMBER_OF_MEMBERSHIPS = 11;  // 9 classes + sum + dominant class
    private Oc4Algorithm oc4Algorithm;
    private Band concChlOc4Band;
    private Band conChlMergedBand;

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

        Operator case2Op = new RegionalWaterOp.Spi().createOperator();
        setCase2rParameters(case2Op);
        case2rProduct = case2Op.getTargetProduct();

        invalidOpImage = VirtualBandOpImage.createMask(invalidPixelExpression,
                                                       l2rProduct,
                                                       ResolutionLevel.MAXRES);

        String invalidL2wExpression = "l1p_flags.CC_LAND || l1p_flags.CC_CLOUD || l1p_flags.CC_MIXEDPIXEL";
        if (invalidPixelExpression != null && !invalidPixelExpression.isEmpty()) {
            invalidL2wExpression = invalidPixelExpression + " || " + invalidL2wExpression;
        }
        System.out.println("invalidL2wExpression = " + invalidL2wExpression);
        invalidL2wImage = VirtualBandOpImage.createMask(invalidL2wExpression,
                                                       l2rProduct,
                                                       ResolutionLevel.MAXRES);

        final L2WProductFactory l2wProductFactory;
        final L2WProductFactory l2wQaaIopProductFactory;   // we want to add the iop bands from the QAA...
        HashMap<String, Object> qaaParams = createQaaParameterMap();
        qaaProduct = GPF.createProduct("Meris.QaaIOP", qaaParams, l2rProduct);
        l2wQaaIopProductFactory = new QaaL2WProductFactory(l2rProduct, qaaProduct);
        l2wProductFactory = new Case2rL2WProductFactory(l2rProduct, case2rProduct);

        l2wProductFactory.setInvalidPixelExpression(invalidPixelExpression);
        l2wProductFactory.setOutputFLH(outputFLH);
        l2wProductFactory.setOutputKdSpectrum(outputKdSpectrum);
        l2wProductFactory.setOutputReflectance(outputReflec);

        ((QaaL2WProductFactory) l2wQaaIopProductFactory).setIopBandsOnly(true);  // we only need to have the iop bands in this product...
        l2wQaaIopProductFactory.setInvalidPixelExpression(invalidPixelExpression);
        l2wQaaIopProductFactory.setOutputFLH(false);
        l2wQaaIopProductFactory.setOutputKdSpectrum(false);
        l2wQaaIopProductFactory.setOutputReflectance(false);

        final Product l2WProduct = l2wProductFactory.createL2WProduct();
        final Product l2WQaaIopProduct = l2wQaaIopProductFactory.createL2WProduct();
        if (classMembershipProduct == null) {
            classMembershipProduct = GPF.createProduct("OWTClassification", GPF.NO_PARAMS, l2rProduct);
        }
        // NEW: call this for all 9 water inverse/forward nets,
        // (set each net pair as parameters in RegionalWaterOp)
        // --> therefore get 9 case2rProducts[k]
        // then compute k-weighted mean for Chl (and TSM?? todo: clarify)
        // with m[k] from classMembershipProduct
        // compute Chl_mean only if sum(m[k] > thresh := 0.8 todo: clarify
        if (classMembershipProduct != null) {
            File auxDataDir = new File(SystemUtils.getApplicationDataDir(), "coastcolour/auxdata/owt_nets");
            URL sourceUrl = ResourceInstaller.getSourceUrl(this.getClass());
            ResourceInstaller installer = new ResourceInstaller(sourceUrl, "auxdata/owt_nets", auxDataDir);
            try {
                installer.install(".*", ProgressMonitor.NULL);
            } catch (IOException e) {
                throw new RuntimeException("Unable to install auxdata of the costcolour module");
            }
            if (ENABLE_OWT_CONC_BANDS) {
                computeSingleCase2RProductsFromFuzzyApproach(auxDataDir, l2WProduct);
            }

            Band concTsmNnBand = ProductUtils.copyBand("tsm", case2rProduct, "conc_tsm", l2WProduct, true);
            concTsmNnBand.setValidPixelExpression(L2WProductFactory.L2W_VALID_EXPRESSION);
            Band concChlNnBand = ProductUtils.copyBand("chl_conc", case2rProduct, "conc_chl_nn", l2WProduct, true);
            concChlNnBand.setValidPixelExpression(L2WProductFactory.L2W_VALID_EXPRESSION);

            for (Band band : classMembershipProduct.getBands()) {
                final String bandName = band.getName();
                if (bandName.startsWith("class_")) {
                    Band b = ProductUtils.copyBand(bandName, classMembershipProduct, "owt_" + bandName, l2WProduct, true);
                    b.setValidPixelExpression("owt_" + b.getValidPixelExpression());
                }
            }
            ProductUtils.copyBand("dominant_class", classMembershipProduct, "owt_dominant_class", l2WProduct, true);
            l2wProductFactory.addPatternToAutoGrouping(l2WProduct, "owt");
        }

        // add oc4v6 chl band
        concChlOc4Band = l2WProduct.addBand("conc_chl_oc4", ProductData.TYPE_FLOAT32);
        conChlMergedBand = l2WProduct.addBand("conc_chl_merged", ProductData.TYPE_FLOAT32);

        Band concChlNnBand = case2rProduct.getBand("chl_conc");
        ProductUtils.copyRasterDataNodeProperties(concChlNnBand, concChlOc4Band);
        ProductUtils.copyRasterDataNodeProperties(concChlNnBand, conChlMergedBand);

        oc4Algorithm = new Oc4Algorithm(Oc4Algorithm.CHLOC4_COEF_MERIS);

        l2WProduct.addBand("conc_chl_weight", ProductData.TYPE_FLOAT32);

        // add the IOP bands from the QAA product
        for (Band band : l2WQaaIopProduct.getBands()) {
            final String bandName = band.getName();
            if (bandName.startsWith(L2WProductFactory.QAA_PREFIX_TARGET_BAND_NAME)) {
                ProductUtils.copyBand(bandName, l2WQaaIopProduct, l2WProduct, true);
            }
        }
        final String groupPattern = L2WProductFactory.QAA_PREFIX_TARGET_BAND_NAME.substring(0, 3);
        l2wProductFactory.addPatternToAutoGrouping(l2WProduct, groupPattern);

        // copy AMORGOS lat/lon bands from L1P/L2R if available
        if (sourceProduct.getBand("corr_longitude") != null && sourceProduct.getBand("corr_latitude") != null) {
            if (!l2WProduct.containsBand("corr_longitude")) {
                ProductUtils.copyBand("corr_longitude", sourceProduct, l2WProduct, true);
            }
            if (!l2WProduct.containsBand("corr_latitude")) {
                ProductUtils.copyBand("corr_latitude", sourceProduct, l2WProduct, true);
            }
        }

        for (Band b : l2WProduct.getBands()) {
            String bandName = b.getName();
            if (bandName.startsWith("iop_") ||
                bandName.startsWith("Kd_") ||
                bandName.startsWith("conc_") ||
                bandName.startsWith("qaa_") ||
                bandName.equals("Z90_max") ||
                bandName.equals("turbidity")) {
                b.setValidPixelExpression(L2WProductFactory.L2W_VALID_EXPRESSION);
            }
        }

        setTargetProduct(l2WProduct);
    }

    private void computeSingleCase2RProductsFromFuzzyApproach(File auxDataDir, Product l2WProduct) {
        c2rSingleProducts = new Product[NUMBER_OF_WATER_NETS];
        for (int i = 0; i < NUMBER_OF_WATER_NETS; i++) {
            Operator case2Op = new RegionalWaterOp.Spi().createOperator();
            forwardIopNnFile = new File(auxDataDir, iopForwardNets[i]);
            inverseIopNnFile = new File(auxDataDir, iopInverseNets[i]);
            inverseKdNnFile = new File(auxDataDir, kdInverseNets[i]);
            setCase2rParameters(case2Op);
            c2rSingleProducts[i] = case2Op.getTargetProduct();
//            Band band = ProductUtils.copyBand("tsm", c2rSingleProducts[i], "conc_tsm_m" + (i + 1), l2WProduct, true);
//            band.setValidPixelExpression(L2WProductFactory.L2W_VALID_EXPRESSION);
//            band = ProductUtils.copyBand("chl_conc", c2rSingleProducts[i], "conc_chl_m" + (i+1), l2WProduct, true);
//            band.setValidPixelExpression(L2WProductFactory.L2W_VALID_EXPRESSION);
        }
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
        if (c2rSingleProducts != null) {
            for (Product product : c2rSingleProducts) {
                if (product != null) {
                    product.dispose();
                }
            }
            c2rSingleProducts = null;
        }

        super.dispose();
    }


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws
                                                                                                             OperatorException {
        Tile satzen = null;
        Tile solzen = null;
        Tile[] flhReflecTiles = null;
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

            flhReflecTiles = getTiles(targetRectangle, FLH_INPUT_BAND_NUMBERS, "reflec_");
            tosaReflecTiles = getTiles(targetRectangle, FLH_INPUT_BAND_NUMBERS, "tosa_reflec_");
            pathTiles = getTiles(targetRectangle, FLH_INPUT_BAND_NUMBERS, "path_");
            transTiles = getTiles(targetRectangle, FLH_INPUT_BAND_NUMBERS, "trans_");
            flhTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.EXP_FLH_681_NAME));
            flhOldNormTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.EXP_FLH_NORM_OLD_681_NAME));
            flhOldAltTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.EXP_FLH_ALT_OLD_681_NAME));
            flhAltTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.EXP_FLH_681_ALT_NAME));
            flhNormTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.EXP_FLH_681_NORM_NAME));
        }

        Tile l2wFlagTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.L2W_FLAGS_NAME));
        Tile oc4Tile = targetTiles.get(concChlOc4Band);
        Tile chlWeightTile = targetTiles.get(targetProduct.getBand("conc_chl_weight"));
        Tile chlMergedTile = targetTiles.get(conChlMergedBand);
        Tile qaaFlags = null;
        if (qaaProduct != null) {
            qaaFlags = getSourceTile(qaaProduct.getRasterDataNode("analytical_flags"), targetRectangle);
        }

        Tile chlNNTile = getSourceTile(case2rProduct.getRasterDataNode("chl_conc"), targetRectangle);
        Tile tsmNNTile = getSourceTile(case2rProduct.getRasterDataNode("tsm"), targetRectangle);
        Tile c2rFlags = getSourceTile(case2rProduct.getRasterDataNode("case2_flags"), targetRectangle);
        Tile[] reflecTiles = getTiles(targetRectangle, REFLEC_BAND_NUMBERS, "reflec_");

        double[] membershipTileValues;
        double[] chlSingleTileValues;
        double[] tsmSingleTileValues;
        Tile chlTile = null;
        Tile tsmTile = null;
        Tile c2rChlTile = null;
        Tile c2rTsmTile = null;
        Tile[] chlSingleTiles;
        Tile[] tsmSingleTiles;
        Tile[] membershipTiles;

        if (ENABLE_OWT_CONC_BANDS) {
//            chlTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.OWT_CONC_CHL_NAME));
//            tsmTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.OWT_CONC_TSM_NAME));

            chlSingleTiles = new Tile[NUMBER_OF_WATER_NETS];
            tsmSingleTiles = new Tile[NUMBER_OF_WATER_NETS];
            membershipTiles = new Tile[NUMBER_OF_MEMBERSHIPS - 2];
            membershipTileValues = new double[membershipTiles.length];
            chlSingleTileValues = new double[NUMBER_OF_WATER_NETS];
            tsmSingleTileValues = new double[NUMBER_OF_WATER_NETS];
            for (int i = 0; i < NUMBER_OF_WATER_NETS; i++) {
                chlSingleTiles[i] = getSourceTile(c2rSingleProducts[i].getBand("chl_conc"), targetRectangle);
                tsmSingleTiles[i] = getSourceTile(c2rSingleProducts[i].getBand("tsm"), targetRectangle);
            }
            for (int i = 0; i < NUMBER_OF_MEMBERSHIPS - 2; i++) {
                membershipTiles[i] = getSourceTile(classMembershipProduct.getBand("norm_class_" + (i + 1)), targetRectangle);
            }
        }


//        if (useQaaForIops) {
//            final Tile rho620Tile = getSourceTile(l2rProduct.getBand("reflec_6"), targetRectangle);
//            // Maybe problematic: get a source tile from the target product. Weird.
//            final Tile bbSPM443Tile = getSourceTile(targetProduct.getBand(L2WProductFactory.IOP_BB_SPM_443_NAME),
//                                                    targetRectangle);
//            final Tile aPig443Tile = getSourceTile(targetProduct.getBand(L2WProductFactory.IOP_A_PIG_443_NAME),
//                                                   targetRectangle);
//            final Tile aYs443Tile = getSourceTile(targetProduct.getBand(L2WProductFactory.IOP_A_YS_443_NAME),
//                                                  targetRectangle);
//
//            final Tile kMinTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.K_MIN_NAME));
//            final Tile z90Tile = targetTiles.get(targetProduct.getBand(L2WProductFactory.Z90_MAX_NAME));
//            final Tile turbidityTile = targetTiles.get(targetProduct.getBand(L2WProductFactory.TURBIDITY_NAME));
//            Tile[] kdTiles = new Tile[L2WProductFactory.KD_LAMBDAS.length];
//            for (int i = 0; i < L2WProductFactory.KD_LAMBDAS.length; i++) {
//                kdTiles[i] = targetTiles.get(targetProduct.getBand("Kd_" + L2WProductFactory.KD_LAMBDAS[i]));
//            }
//        }
//

        Raster invalidL2wRaster = invalidL2wImage.getData(targetRectangle);
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                final boolean isSampleInvalid = isSampleInvalid(x, y);
                if (outputFLH && !isSampleInvalid) {
                    computeFLHValues(x, y, satzen, solzen, flhReflecTiles, tosaReflecTiles, pathTiles, transTiles, flhTile,
                                     flhOldNormTile, flhOldAltTile, flhAltTile, flhNormTile, isSampleInvalid);
                }

//                if (useQaaForIops) {
//                    final double bTsm443 = bbSPM443Tile.getSampleDouble(x, y) / WaterAlgorithm.BTSM_TO_SPM_FACTOR;
//                    final double aPig443 = aPig443Tile.getSampleDouble(x, y);
//                    final double aYs443 = aYs443Tile.getSampleDouble(x, y);
//                    KMin kMin = new KMin(bTsm443, aPig443, aYs443);
//                    final double kMinValue = kMin.computeKMinValue();
//                    kMinTile.setSample(x, y, isSampleInvalid ? Double.NaN : kMinValue);
//                    if (outputKdSpectrum) {
//                        double[] kds = new double[kdTiles.length];
//                        if (isSampleInvalid) {
//                            Arrays.fill(kds, Double.NaN);
//                        } else {
//                            kds = kMin.computeKdSpectrum();
//                        }
//                        for (int i = 0; i < kds.length; i++) {
//                            kdTiles[i].setSample(x, y, kds[i]);
//                        }
//                    } else {
//                        kdTiles[2].setSample(x, y, isSampleInvalid ? Double.NaN : kMin.computeKd490());
//                    }
//                    z90Tile.setSample(x, y, isSampleInvalid ? Double.NaN : -1 / kMinValue);
//                    final double turbidityValue = computeTurbidity(rho620Tile.getSampleDouble(x, y));
//                    turbidityTile.setSample(x, y, isSampleInvalid ? Double.NaN : turbidityValue);
//                }

                double[] reflec = new double[REFLEC_BAND_NUMBERS.length];
                for (int i = 0; i < reflec.length; i++) {
                    reflec[i] = reflecTiles[i].getSampleDouble(x, y);
                }

                double conc_tsm = tsmNNTile.getSampleDouble(x, y);
                double conc_chl_nn = chlNNTile.getSampleDouble(x, y);

                double conc_chl_oc4 = computeOC4(reflec);
                double chlWeightInternal = Math.min(Math.max(((conc_tsm - 5) / 5), 0), 1);
                double chl_merge;
                boolean useOc4 = conc_chl_nn < 0.1 || conc_tsm < 5;
                boolean useNN = conc_chl_oc4 > 20;
                if (useOc4) {
                    chl_merge = conc_chl_oc4;
                } else if (useNN) {
                    chl_merge = conc_chl_nn;
                } else {
                    chl_merge = chlWeightInternal * conc_chl_nn + (1 - chlWeightInternal) * conc_chl_oc4;
                }
                double chlWeight;
                if (useOc4) {
                    chlWeight = 0;
                } else if (useNN) {
                    chlWeight = 1;
                } else {
                    chlWeight = chlWeightInternal;
                }

                oc4Tile.setSample(x, y, conc_chl_oc4);
                chlWeightTile.setSample(x, y, chlWeight);
                chlMergedTile.setSample(x, y, chl_merge);

                final double slope = computeReflSlope(reflec);
                double maxRefl = computeMaxRefle(reflec);
                double MCIrel = computeMCIRrel(reflec);
                boolean invalidSpectra = (slope > 0 && maxRefl < 0.01 && !(MCIrel > 10 && slope < 7)) || slope>=8;

                final boolean invalidFlagValue = invalidSpectra || (invalidL2wRaster.getSample(x, y, 0) != 0);
                setL2wFlags(x, y, l2wFlagTile, c2rFlags, qaaFlags, invalidFlagValue);

                if (ENABLE_OWT_CONC_BANDS) {
                    for (int k = 0; k < membershipTiles.length; k++) {
                        membershipTileValues[k] = membershipTiles[k].getSampleDouble(x, y);
                    }
                    for (int k = 0; k < NUMBER_OF_WATER_NETS; k++) {
                        chlSingleTileValues[k] = chlSingleTiles[k].getSampleDouble(x, y);
                        tsmSingleTileValues[k] = tsmSingleTiles[k].getSampleDouble(x, y);
                    }
//                    double[] relevantMemberships = getRelevantMembershipClasses(membershipTileValues, membershipClassSumThresh);
                    // get weighted CHL and TSM
//                    double weightedChl = getWeightedConc(relevantMemberships, chlSingleTileValues);
//                    chlTile.setSample(x, y, weightedChl);
//                    double weightedTsm = getWeightedConc(relevantMemberships, tsmSingleTileValues);
//                    tsmTile.setSample(x, y, weightedTsm);
//                } else {
//                    chlTile.setSample(x, y, c2rChlTile.getSampleDouble(x, y));
//                    tsmTile.setSample(x, y, c2rTsmTile.getSampleDouble(x, y));
                }
            }
        }
    }

    private double computeMCIRrel(double[] reflec) {
        double baseline = reflec[7] + (reflec[7] - reflec[9]) * (709.0 - 681.0) / (779.0 - 681.0);
        double MCI_abs = reflec[8] - baseline;
        double MCI_rel = 100 * MCI_abs / baseline;
        return MCI_rel;
    }

    private double computeMaxRefle(double[] reflec) {
        double max = reflec[0];
        for (int j = 1; j < reflec.length; j++) {
            if (Double.isNaN(reflec[j])) {
                return Double.NaN;
            }
            if (reflec[j] > max) {
                max = reflec[j];
            }
        }
        return max;
    }

    private int computeReflSlope(double[] reflec) {
        int slope = 0;
        for (int i = 0; i < reflec.length - 1; i++) {
            slope += Math.signum(reflec[i + 1] - reflec[i]);
        }
        return slope;
    }

    private double computeOC4(double[] reflec) {
        double rrs443 = reflec[1];
        double rrs490 = reflec[2];
        double rrs510 = reflec[3];
        double rrs555 = reflec[4];
        return oc4Algorithm.compute(rrs443, rrs490, rrs510, rrs555);
    }

    static double getWeightedConc(double[] relevantMembershipClasses, double[] concValues) {
        double weightedConc = 0.0;
        double sumWeights = 0.0;

        for (int i = 0; i < relevantMembershipClasses.length; i++) {
            double relevantMembership = relevantMembershipClasses[i];
            if (relevantMembership > 0.0) {
                sumWeights += relevantMembership;
                double concSingle = concValues[i];
                weightedConc += relevantMembership * concSingle;
            }
        }
        return weightedConc / sumWeights;
    }

    static double[] getRelevantMembershipClasses(double[] membershipValues, double membershipClassSumThresh) {
        Membership[] members = new Membership[membershipValues.length];
        for (int i = 0; i < membershipValues.length; i++) {
            members[i] = new Membership(i, membershipValues[i]);
        }

        Arrays.sort(members, new Comparator<Membership>() {
            @Override
            public int compare(Membership o1, Membership o2) {
                return Double.compare(o2.weight, o1.weight);
            }
        });
        double[] result = new double[membershipValues.length];
        double weightSum = 0;
        for (Membership member : members) {
            final double weight = member.weight;
            result[member.index] = weight;
            weightSum += weight;
            if (weightSum > membershipClassSumThresh) {
                break;
            }
        }
        return result;
    }

    private static class Membership {

        final int index;
        final double weight;

        private Membership(int index, double weight) {
            this.index = index;
            this.weight = weight;
        }
    }

    private void setL2wFlags(int x, int y, Tile l2wFlags, Tile c2rFlags, Tile qaaFlags, boolean l2wInvalid) {

        l2wFlags.setSample(x, y, 0);
        boolean isInvalid = l2wInvalid;

        if (c2rFlags != null) {
            final boolean _nn_wlr_ootr = c2rFlags.getSampleBit(x, y, WaterAlgorithm.WLR_OOR_BIT_INDEX);
            l2wFlags.setSample(x, y, 0, _nn_wlr_ootr);
            final boolean _nn_conc_ootr = c2rFlags.getSampleBit(x, y, WaterAlgorithm.CONC_OOR_BIT_INDEX);
            l2wFlags.setSample(x, y, 1, _nn_conc_ootr);
            final boolean _nn_ootr = c2rFlags.getSampleBit(x, y, WaterAlgorithm.OOTR_BIT_INDEX);
            l2wFlags.setSample(x, y, 2, _nn_ootr);
            isInvalid = isInvalid || _nn_ootr;
            final boolean _nn_whitecaps = c2rFlags.getSampleBit(x, y, WaterAlgorithm.WHITECAPS_BIT_INDEX);
            l2wFlags.setSample(x, y, 3, _nn_whitecaps);
        }

        if (qaaFlags != null) {
            final boolean _qaa_imaginary = qaaFlags.getSampleBit(x, y, 2);
            l2wFlags.setSample(x, y, 4, _qaa_imaginary);
            final boolean _qaa_negative_ays = qaaFlags.getSampleBit(x, y, 1);
            l2wFlags.setSample(x, y, 5, _qaa_negative_ays);
            //isInvalid = isInvalid || _qaa_imaginary || _qaa_negative_ays;
        }

        l2wFlags.setSample(x, y, 6, isInvalid);
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

    private Tile[] getTiles(Rectangle rectangle, int[] bandNumbers, String bandNamePrefix) {
        Tile[] tiles = new Tile[bandNumbers.length];
        for (int i = 0; i < bandNumbers.length; i++) {
            int bandIndex = bandNumbers[i];
            tiles[i] = getSourceTile(l2rProduct.getBand(bandNamePrefix + bandIndex), rectangle);
        }
        return tiles;
    }

    private void setCase2rParameters(Operator regionalWaterOp) {
        if (!useExtremeCaseMode) {
            inverseIopNnFile = new File(RegionalWaterOp.DEFAULT_INVERSE_IOP_NET);
        }
        regionalWaterOp.setParameter("inverseIopNnFile", inverseIopNnFile);
        regionalWaterOp.setParameter("inverseKdNnFile", inverseKdNnFile);
        regionalWaterOp.setParameter("forwardIopNnFile", forwardIopNnFile);
        regionalWaterOp.setParameter("useSnTMap", useSnTMap);
        regionalWaterOp.setParameter("averageSalinity", averageSalinity);
        regionalWaterOp.setParameter("averageTemperature", averageTemperature);
        regionalWaterOp.setParameter("outputKdSpectrum", outputKdSpectrum);
        regionalWaterOp.setParameter("outputAPoc", outputAPoc);
        regionalWaterOp.setParameter("inputReflecAre", "IRRADIANCE_REFLECTANCES");
        regionalWaterOp.setParameter("invalidPixelExpression", invalidPixelExpression);
        regionalWaterOp.setSourceProduct("acProduct", l2rProduct);
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
        l2rParams.put("brightTestThreshold", brightTestThreshold);
        l2rParams.put("brightTestWavelength", brightTestWavelength);
        l2rParams.put("useSnTMap", useSnTMap);
        l2rParams.put("averageSalinity", averageSalinity);
        l2rParams.put("averageTemperature", averageTemperature);
        if (!useExtremeCaseMode) {
            atmoNetMerisFile = new File(GlintCorrectionOperator.MERIS_ATMOSPHERIC_NET_NAME);
            autoassociativeNetFile = new File(GlintCorrectionOperator.ATMO_AANN_NET_NAME);
        }
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
