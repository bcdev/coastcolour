package org.esa.beam.coastcolour.processing;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.coastcolour.case2.RegionalWaterOp;
import org.esa.beam.coastcolour.case2.water.WaterAlgorithm;
import org.esa.beam.coastcolour.glint.atmosphere.operator.ReflectanceEnum;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.*;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.VirtualBandOpImage;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;

import java.awt.*;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L2W",
                  version = "1.8",
                  authors = "C. Brockmann, M. Bouvet, R. Santer, H. Schiller, M. Peters, O. Danne",
                  copyright = "(c) 2011-2013 Brockmann Consult",
                  description = "Computes information about water properties such as IOPs, concentrations and " +
                          "other variables")
public class L2WOp extends Operator {

    private static final int[] REFLEC_BAND_NUMBERS = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    // compile time switch (RD)
    static final boolean ENABLE_OWT_CONC_BANDS = false;

    @SourceProduct(alias = "ccL2R",
                   label = "CC L2R, CC L1P or MERIS L1B product",
                   description = "CC L2R, CC L1P or MERIS L1B input product")
    private Product sourceProduct;

    //@SourceProduct(description = "Class membership product from Fuzzy classification (FuzzyOp)", optional = true)
    private Product classMembershipProduct;

    @Parameter(defaultValue = "false",
               label = "[L1P] Perform re-calibration",
               description = "Applies correction from MERIS 2nd to 3rd reprocessing quality. " +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.")
    private boolean doCalibration;

    @Parameter(defaultValue = "true",
               label = "[L1P] Perform Smile-effect correction",
               description = "Whether to perform MERIS Smile-effect correction. " +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.")
    private boolean doSmile;

    @Parameter(defaultValue = "true",
               label = "[L1P] Perform equalization",
               description = "Perform removal of detector-to-detector systematic radiometric differences in MERIS L1b data products. " +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.")
    private boolean doEqualization;

    // IdePix parameters  from L1P
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
               description = "Threshold of Cloud Probability Feature Value above which cloud is regarded as " +
                       "sure (i.e. a higher value results in fewer sure clouds). " +
                       "This is a L1P option and has only effect if the source product is a MERIS L1b product.",
               label = " [L1P] Cloud screening 'sure' threshold")
    private double ccCloudScreeningSure = 1.8;       // Schiller


    @Parameter(defaultValue = "true",
               label = "[L2R] Use climatology map for salinity and temperature",
               description = "By default a climatology map is used. If set to 'false' the specified average values are used " +
                       "for the whole scene. This is a L2R option and has only effect if the source product is a MERIS L1b or CC L1P product.")
    private boolean useSnTMap;

    @Parameter(defaultValue = "35", unit = "PSU",
               label = "[L2R] Average salinity",
               description = "If no climatology is used, the average salinity of the water in the region to be processed is taken. " +
                       "This is a L2R option and has only effect if the source product is a MERIS L1b or CC L1P product.")
    private double averageSalinity;

    @Parameter(defaultValue = "15", unit = "C",
               label = "[L2R] Average temperature",
               description = "If no climatology is used, the average temperature of the water in the region to be processed is taken. " +
                       "This is a L2R option and has only effect if the source product is a MERIS L1b or CC L1P product.")
    private double averageTemperature;

    @Parameter(defaultValue = "true",
               label = "[L2R] Use NNs for maximum ranges of CoastColour IOPs",
               description = "If selected a neural network for maximum range of concentrations and IOPs is used. " +
                       "If deselected a neural net with limited ranges but less noisy for low concentration ranges is used. " +
                       "This is a L2R option and has only effect if the source product is a MERIS L1b or CC L1P product.")
    private boolean useExtremeCaseMode;

    @Parameter(defaultValue = "l1p_flags.CC_LAND",
               label = "[L2R] Land masking expression",
               description = "The arithmetic expression used for land masking. " +
                       "This is a L2R option and has only effect if the source product is a MERIS L1b or CC L1P product). ",
               notEmpty = true, notNull = true)
    private String landExpression;

    @Parameter(defaultValue = "(l1p_flags.CC_CLOUD && not l1p_flags.CC_CLOUD_AMBIGUOUS) || l1p_flags.CC_SNOW_ICE",
               label = "[L2R] Cloud/Ice masking expression",
               description = "The arithmetic expression used for cloud/ice masking. " +
                       "This is a L2R option and has only effect if the source product is a MERIS L1b or CC L1P product.",
               notEmpty = true, notNull = true)
    private String cloudIceExpression;

    @Parameter(defaultValue = "l2r_flags.INPUT_INVALID",
               description = "Expression defining pixels not considered for L2W processing")
    private String invalidPixelExpression;

    @Parameter(defaultValue = "false",
               label = "Write water leaving reflectance to the target product",
               description = "Write the water leaving reflectance to the CC L2W target product.")
    private boolean outputReflec;

    //  RADIANCE_REFLECTANCES   : x
    //  IRRADIANCE_REFLECTANCES : x * PI      (see GlintCorrection.perform)
    @Parameter(defaultValue = "RADIANCE_REFLECTANCES", valueSet = {"RADIANCE_REFLECTANCES", "IRRADIANCE_REFLECTANCES"},
               label = " Write water leaving reflectances as",
               description = "Select if water leaving reflectances shall be written as radiances or irradiances. " +
                       "The irradiances ( = radiances multiplied by PI) are compatible with the standard MERIS product.")
    private ReflectanceEnum outputL2WReflecAs;

    //    @Parameter(defaultValue = "false", label = "Write A_Poc to the target product",
//            description = "Write absorption by particulate organic matter (A_Poc) to the CC L2W target product.")
    private boolean outputAPoc = false;

    @Parameter(defaultValue = "true", label = "Write Kd spectrum to the target product",
               description = "Write the output of downwelling irradiance attenuation coefficients (Kd) to the CC L2W target product. " +
                       "If disabled only Kd_490 is added to the output.")
    private boolean outputKdSpectrum;


    private float qaaATotalLower = -0.02f;
    private float qaaATotalUpper = 5.0f;
    private float qaaBbSpmLower = -0.2f;
    private float qaaBbSpmUpper = 5.0f;
    private float qaaAPigLower = -0.02f;
    private float qaaAPigUpper = 3.0f;

    private Product l2rProduct;
    private Product qaaProduct;
    private Product case2rProduct;
    private VirtualBandOpImage invalidL2wImage;

    private File inverseIopNnFile;
    private File inverseKdNnFile;
    private File forwardIopNnFile;

    private ReflectanceEnum inputReflecIs;

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

        if (!ProductValidator.isValidL2WInputProduct(sourceProduct)) {
            final String message = String.format("Input product '%s' is not a valid source for L2W processing",
                                                 sourceProduct.getName());
            throw new OperatorException(message);
        }

        l2rProduct = sourceProduct;
        if (!isL2RSourceProduct(l2rProduct)) {
            HashMap<String, Object> l2rParams = createL2RParameterMap();
            l2rProduct = GPF.createProduct("CoastColour.L2R", l2rParams, sourceProduct);
        }

        inputReflecIs = (l2rProduct.getDescription().contains("IRRADIANCE_REFLECTANCES") ?
                ReflectanceEnum.IRRADIANCE_REFLECTANCES :
                ReflectanceEnum.RADIANCE_REFLECTANCES);

        Operator case2Op = new RegionalWaterOp.Spi().createOperator();
        setCase2rParameters(case2Op);
        case2rProduct = case2Op.getTargetProduct();

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
        l2wProductFactory.setOutputKdSpectrum(outputKdSpectrum);
        l2wProductFactory.setOutputReflectance(outputReflec);
        l2wProductFactory.setOutputReflectanceAs(outputL2WReflecAs);
        l2wProductFactory.setInputReflectanceIs(inputReflecIs);

        ((QaaL2WProductFactory) l2wQaaIopProductFactory).setIopBandsOnly(true);  // we only need to have the iop bands in this product...
        l2wQaaIopProductFactory.setInvalidPixelExpression(invalidPixelExpression);
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
                computeSingleCase2RProductsFromFuzzyApproach(auxDataDir);
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

    private void computeSingleCase2RProductsFromFuzzyApproach(File auxDataDir) {
        c2rSingleProducts = new Product[NUMBER_OF_WATER_NETS];
        for (int i = 0; i < NUMBER_OF_WATER_NETS; i++) {
            Operator case2Op = new RegionalWaterOp.Spi().createOperator();
            forwardIopNnFile = new File(auxDataDir, iopForwardNets[i]);
            inverseIopNnFile = new File(auxDataDir, iopInverseNets[i]);
            inverseKdNnFile = new File(auxDataDir, kdInverseNets[i]);
            setCase2rParameters(case2Op);
            c2rSingleProducts[i] = case2Op.getTargetProduct();
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
        final Product targetProduct = getTargetProduct();

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
        Tile[] chlSingleTiles;
        Tile[] tsmSingleTiles;
        Tile[] membershipTiles;

        if (ENABLE_OWT_CONC_BANDS) {

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

        Raster invalidL2wRaster = invalidL2wImage.getData(targetRectangle);
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {

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
                boolean invalidSpectra = (slope > 0 && maxRefl < 0.01 && !(MCIrel > 10 && slope < 7)) || slope >= 8;

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
                }
            }
        }
    }

    private double computeMCIRrel(double[] reflec) {
        double baseline = reflec[7] + (reflec[7] - reflec[9]) * (709.0 - 681.0) / (779.0 - 681.0);
        double MCI_abs = reflec[8] - baseline;
        return 100 * MCI_abs / baseline;
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
        }

        l2wFlags.setSample(x, y, 6, isInvalid);
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
        qaaParams.put("divideByPI", ReflectanceEnum.IRRADIANCE_REFLECTANCES.equals(inputReflecIs));
        return qaaParams;
    }

    private HashMap<String, Object> createL2RParameterMap() {
        return createBaseL2RParameterMap();
    }

    private HashMap<String, Object> createBaseL2RParameterMap() {
        HashMap<String, Object> l2rParams = new HashMap<String, Object>();
        l2rParams.put("doCalibration", doCalibration);
        l2rParams.put("doSmile", doSmile);
        l2rParams.put("doEqualization", doEqualization);
        l2rParams.put("useIdepix", true);
        l2rParams.put("ccCloudBufferWidth", ccCloudBufferWidth);
        l2rParams.put("ccCloudScreeningAmbiguous", ccCloudScreeningAmbiguous);
        l2rParams.put("ccCloudScreeningSure", ccCloudScreeningSure);
        l2rParams.put("useSnTMap", useSnTMap);
        l2rParams.put("averageSalinity", averageSalinity);
        l2rParams.put("averageTemperature", averageTemperature);
        l2rParams.put("landExpression", landExpression);
        l2rParams.put("cloudIceExpression", cloudIceExpression);
        l2rParams.put("outputNormReflec", true);
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
