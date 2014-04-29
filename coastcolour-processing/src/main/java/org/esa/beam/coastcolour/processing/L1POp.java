package org.esa.beam.coastcolour.processing;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.jai.tilecache.DefaultSwapSpace;
import com.bc.ceres.jai.tilecache.SwappingTileCache;
import org.esa.beam.coastcolour.glint.atmosphere.operator.GlintCorrectionOperator;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.*;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.internal.OperatorImage;
import org.esa.beam.idepix.algorithms.coastcolour.CoastColourClassificationOp;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.meris.MerisOp;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.OpImage;
import javax.media.jai.TileCache;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L1P",
        version = "1.8",
        authors = "C. Brockmann, M. Bouvet, R. Santer, H. Schiller, M. Peters, O. Danne",
        copyright = "(c) 2011-2013 Brockmann Consult",
        description = "Computes a refinement of top of atmosphere radiance and " +
                "pixel characterization information.")
public class L1POp extends Operator {

    public static final String CC_LAND_FLAG_NAME = "CC_LAND";
    public static final String CC_COASTLINE_FLAG_NAME = "CC_COASTLINE";
    public static final String CC_CLOUD_FLAG_NAME = "CC_CLOUD";
    public static final String CC_CLOUD_AMBIGUOUS_FLAG_NAME = "CC_CLOUD_AMBIGUOUS";
    public static final String CC_CLOUD_BUFFER_FLAG_NAME = "CC_CLOUD_BUFFER";
    public static final String CC_CLOUD_SHADOW_FLAG_NAME = "CC_CLOUD_SHADOW";
    public static final String CC_SNOW_ICE_FLAG_NAME = "CC_SNOW_ICE";
    public static final String CC_MIXEDPIXEL_FLAG_NAME = "CC_MIXEDPIXEL";
    public static final String CC_GLINTRISK_FLAG_NAME = "CC_GLINTRISK";

    public static final String L1P_FLAG_BAND_NAME = "l1p_flags";

    public static final int LAND_BIT_INDEX = 0;

    public static final int COASTLINE_BIT_INDEX = GlintCorrectionOperator.COASTLINE_BIT_INDEX;
    public static final int CLOUD_BIT_INDEX = GlintCorrectionOperator.CLOUD_BIT_INDEX;
    public static final int CLOUD_AMBIGUOUS_BIT_INDEX = GlintCorrectionOperator.CLOUD_AMBIGUOUS_BIT_INDEX;
    public static final int CLOUD_BUFFER_BIT_INDEX = GlintCorrectionOperator.CLOUD_BUFFER_BIT_INDEX;
    public static final int CLOUD_SHADOW_BIT_INDEX = GlintCorrectionOperator.CLOUD_SHADOW_BIT_INDEX;
    public static final int SNOW_ICE_BIT_INDEX = GlintCorrectionOperator.SNOW_ICE_BIT_INDEX;
    public static final int MIXEDPIXEL_BIT_INDEX = GlintCorrectionOperator.MIXEDPIXEL_BIT_INDEX;
    public static final int GLINTRISK_BIT_INDEX = GlintCorrectionOperator.GLINTRISK_BIT_INDEX;

    private static final long MEGABYTE = 1024L * 1024L;

    private static final String IDEPIX_OPERATOR_ALIAS = "Idepix.Water";
    private static final String RADIOMETRY_OPERATOR_ALIAS = "Meris.CorrectRadiometry";
    private static final String CLOUD_FLAG_BAND_NAME = "cloud_classif_flags";

    @SourceProduct(alias = "merisL1B",
            label = "MERIS L1B product",
            description = "The MERIS L1B input product")
    private Product sourceProduct;

    // CoastColour L1P parameters
    @Parameter(defaultValue = "false",
            label = " Perform ICOL correction",
            description = "Whether to perform ICOL correction (NOTE: This step can be very time- and memory-consuming! Please see help documentation for more details).")
    private boolean doIcol;

    @Parameter(defaultValue = "false",
            label = " Perform re-calibration",
            description = "Applies correction from MERIS 2nd to 3rd reprocessing quality.")
    private boolean doCalibration;

    @Parameter(defaultValue = "true",
            label = " Perform Smile-effect correction",
            description = "Whether to perform MERIS Smile-effect correction.")
    private boolean doSmile;

    @Parameter(defaultValue = "true",
            label = " Perform equalization",
            description = "Perform removal of detector-to-detector systematic radiometric differences in MERIS L1b data products.")
    private boolean doEqualization;

    // IdePix parameters
    @Parameter(defaultValue = "2", interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = " Width of cloud buffer (# of pixels)")
    private int ccCloudBufferWidth;

    @Parameter(defaultValue = "1.4",
            description = "Threshold of Cloud Probability Feature Value above which cloud is regarded as still " +
                    "ambiguous (i.e. a higher value results in fewer ambiguous clouds).",
            label = " Cloud screening 'ambiguous' threshold")
    private double ccCloudScreeningAmbiguous = 1.4;      // Schiller

    @Parameter(defaultValue = "1.8",
            description = "Threshold of Cloud Probability Feature Value above which cloud is regarded as " +
                    "sure (i.e. a higher value results in fewer sure clouds).",
            label = " Cloud screening 'sure' threshold")
    private double ccCloudScreeningSure = 1.8;       // Schiller

    @Parameter(defaultValue = "false",
            description = "Write Cloud Probability Feature Value to the  CC L1P target product.",
            label = " Write Cloud Probability Feature Value to the target product")
    private boolean ccOutputCloudProbabilityFeatureValue = false;


    private Band cloudFlagBand;
    private Product idepixProduct;


    @Override
    public void initialize() throws OperatorException {

        if (!ProductValidator.isValidL1PInputProduct(sourceProduct)) {
            final String message = String.format("Input product '%s' is not a valid source for L1P processing",
                    sourceProduct.getName());
            throw new OperatorException(message);
        }

        Product l1pInputProduct;
        if (doIcol) {
            // this is time and memory consuming, but was required...
            l1pInputProduct = createIcolProduct(sourceProduct);
            attachFileTileCache(l1pInputProduct);
        } else {
            l1pInputProduct = sourceProduct;
        }

        final Map<String, Object> rcParams = createRadiometryParameterMap();
        Product radiometryProduct = GPF.createProduct(RADIOMETRY_OPERATOR_ALIAS, rcParams, l1pInputProduct);

        Product l1pProduct = createL1PProduct(radiometryProduct);

        HashMap<String, Object> idepixParams = createIdepixParameterMap();
        idepixProduct = GPF.createProduct(IDEPIX_OPERATOR_ALIAS, idepixParams, radiometryProduct);

        if (ccOutputCloudProbabilityFeatureValue) {
            ProductUtils.copyBand(CoastColourClassificationOp.CLOUD_PROBABILITY_VALUE, idepixProduct, l1pProduct, true);
        }

        checkForExistingFlagBand(idepixProduct, CLOUD_FLAG_BAND_NAME);
        cloudFlagBand = idepixProduct.getBand(CLOUD_FLAG_BAND_NAME);

        attachFlagBandL1P(l1pProduct);
        sortFlagCodings(l1pProduct);

        updateL1BMasks(l1pProduct);
        reorderBands(l1pProduct);

        setTargetProduct(l1pProduct);
    }

    private void attachFileTileCache(Product product) {
        String productName = sourceProduct.getName();
        File tmpDir = new File(System.getProperty("java.io.tmpdir"), productName + "_" + System.currentTimeMillis());
        if (!tmpDir.mkdirs()) {
            throw new OperatorException("Failed to create tmp dir for SwappingTileCache: " + tmpDir.getAbsolutePath());
        }
        tmpDir.deleteOnExit();
        TileCache tileCache = new SwappingTileCache(16L * MEGABYTE, new DefaultSwapSpace(tmpDir));
        Band[] bands = product.getBands();
        for (Band band : bands) {
            RenderedImage image = band.getSourceImage().getImage(0);
            if (image instanceof OperatorImage) {// OperatorImage is subclass of OpImage
                OpImage opImage = (OpImage) image;
                opImage.setTileCache(tileCache);
            }
        }
    }

    private HashMap<String, Object> createIdepixParameterMap() {
        HashMap<String, Object> idepixParams = new HashMap<String, Object>();
        idepixParams.put("ccCloudBufferWidth", ccCloudBufferWidth);
        idepixParams.put("ccCloudScreeningAmbiguous", ccCloudScreeningAmbiguous);
        idepixParams.put("ccCloudScreeningSure", ccCloudScreeningSure);
        idepixParams.put("ccOutputCloudProbabilityFeatureValue", ccOutputCloudProbabilityFeatureValue);
        return idepixParams;
    }

    private Map<String, Object> createRadiometryParameterMap() {
        final Map<String, Object> rcParams = new HashMap<String, Object>();
        rcParams.put("doCalibration", doCalibration);
        rcParams.put("doSmile", doSmile);
        rcParams.put("doEqualization", doEqualization);
        rcParams.put("doRadToRefl", false);
        return rcParams;
    }

    private Product createL1PProduct(Product radiometryProduct) {
        String l1pProductType = sourceProduct.getProductType().substring(0, 8) + "CCL1P";
        final int sceneWidth = radiometryProduct.getSceneRasterWidth();
        final int sceneHeight = radiometryProduct.getSceneRasterHeight();
        final Product l1pProduct = new Product(radiometryProduct.getName(), l1pProductType, sceneWidth, sceneHeight);
        l1pProduct.setDescription("MERIS CoastColour L1P");
        l1pProduct.setStartTime(radiometryProduct.getStartTime());
        l1pProduct.setEndTime(radiometryProduct.getEndTime());
        ProductUtils.copyMetadata(radiometryProduct, l1pProduct);
        ProductUtils.copyMasks(radiometryProduct, l1pProduct);
        copyBands(radiometryProduct, l1pProduct);
        ProductUtils.copyFlagBands(radiometryProduct, l1pProduct, true);
        ProductUtils.copyTiePointGrids(radiometryProduct, l1pProduct);
        ProductUtils.copyGeoCoding(radiometryProduct, l1pProduct);

        return l1pProduct;
    }

    private Product createIcolProduct(Product l1pProduct) {
        HashMap<String, Object> icolParams = new HashMap<String, Object>();
        icolParams.put("icolAerosolCase2", true);
        icolParams.put("productType", 0);
        icolParams.put("aeArea", AeArea.COASTAL_OCEAN);
        icolParams.put("useAdvancedLandWaterMask", true);
        Map<String, Product> sourceProducts = new HashMap<String, Product>(1);
        sourceProducts.put("sourceProduct", l1pProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(MerisOp.class), icolParams, sourceProducts);
    }

    private void copyBands(Product radiometryProduct, Product l1pProduct) {
        final Band[] radiometryBands = radiometryProduct.getBands();
        for (Band band : radiometryBands) {
            if (!band.isFlagBand()) {
                ProductUtils.copyBand(band.getName(), radiometryProduct, l1pProduct, true);
            }
        }
    }

    @Override
    public void dispose() {
        if (idepixProduct != null) {
            idepixProduct.dispose();
            idepixProduct = null;
        }
        // it is ok to dispose the idepixProduct because if this product is created the the L1POp will compute a band.
        // if idepix is disabled the L1POp does not compute a band and will therefore be removed from the chain.
        // In this case, if the radiometryProduct would be disposed here it would break the processing chain, because it
        // is lost as source product for the ICOL operator
        super.dispose();
    }

    private void updateL1BMasks(Product l1pProduct) {
        ProductNodeGroup<Mask> maskGroup = l1pProduct.getMaskGroup();
        renameMask(maskGroup, "coastline");
        renameMask(maskGroup, "land");
        renameMask(maskGroup, "water");
        renameMask(maskGroup, "cosmetic");
        renameMask(maskGroup, "duplicated");
        renameMask(maskGroup, "glint_risk");
        renameMask(maskGroup, "suspect");
        renameMask(maskGroup, "bright");
        renameMask(maskGroup, "invalid");
        Mask l1bWater = maskGroup.get("l1b_water");
        if (l1bWater != null) {
            l1bWater.setDescription("Pixel is over ocean, not land");
        }
    }

    private void renameMask(ProductNodeGroup<Mask> maskGroup, String maskName) {
        Mask mask = maskGroup.get(maskName);
        if (mask != null) {
            mask.setName("l1b_" + maskName);
        }
    }

    private void sortFlagCodings(Product l1pProduct) {
        ProductNodeGroup<FlagCoding> flagCodingGroup = l1pProduct.getFlagCodingGroup();
        FlagCoding l1pFlagCoding = flagCodingGroup.get(L1P_FLAG_BAND_NAME);
        FlagCoding l1bFlagCoding = flagCodingGroup.get(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        flagCodingGroup.remove(l1pFlagCoding);
        flagCodingGroup.remove(l1bFlagCoding);
        flagCodingGroup.add(l1pFlagCoding);
        flagCodingGroup.add(l1bFlagCoding);
    }

    private void reorderBands(Product l1pProduct) {
        Band detectorBand = l1pProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME);
        l1pProduct.removeBand(detectorBand);
        l1pProduct.addBand(detectorBand);
        Band l1bFlagBand = l1pProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME);
        l1pProduct.removeBand(l1bFlagBand);
        l1pProduct.addBand(l1bFlagBand);
        Band l1pFlagBand = l1pProduct.getBand(L1P_FLAG_BAND_NAME);
        if (l1pFlagBand != null) {
            l1pProduct.removeBand(l1pFlagBand);
            l1pProduct.addBand(l1pFlagBand);
        }

    }

    private void attachFlagBandL1P(Product l1pProduct) {
        FlagCoding l1pFC = new FlagCoding(L1P_FLAG_BAND_NAME);
        l1pFC.addFlag(CC_LAND_FLAG_NAME, BitSetter.setFlag(0, LAND_BIT_INDEX), "Pixel masked as land");
        l1pFC.addFlag(CC_COASTLINE_FLAG_NAME, BitSetter.setFlag(0, COASTLINE_BIT_INDEX), "Pixel masked as coastline");
        l1pFC.addFlag(CC_CLOUD_FLAG_NAME, BitSetter.setFlag(0, CLOUD_BIT_INDEX), "Pixel masked as cloud");
        l1pFC.addFlag(CC_CLOUD_AMBIGUOUS_FLAG_NAME, BitSetter.setFlag(0, CLOUD_AMBIGUOUS_BIT_INDEX),
                "Pixel masked as ambiguous cloud");
        l1pFC.addFlag(CC_CLOUD_BUFFER_FLAG_NAME, BitSetter.setFlag(0, CLOUD_BUFFER_BIT_INDEX),
                "Pixel masked as cloud buffer");
        l1pFC.addFlag(CC_CLOUD_SHADOW_FLAG_NAME, BitSetter.setFlag(0, CLOUD_SHADOW_BIT_INDEX),
                "Pixel masked as cloud shadow");
        l1pFC.addFlag(CC_SNOW_ICE_FLAG_NAME, BitSetter.setFlag(0, SNOW_ICE_BIT_INDEX), "Pixel masked as snow/ice");
        l1pFC.addFlag(CC_MIXEDPIXEL_FLAG_NAME, BitSetter.setFlag(0, MIXEDPIXEL_BIT_INDEX), "Mixture of water and land/floating vegetation");
        l1pFC.addFlag(CC_GLINTRISK_FLAG_NAME, BitSetter.setFlag(0, GLINTRISK_BIT_INDEX),
                "Risk that pixel is under glint");

        l1pProduct.getFlagCodingGroup().add(l1pFC);
        final Band l1pBand = l1pProduct.addBand(L1P_FLAG_BAND_NAME, ProductData.TYPE_INT16);
        l1pBand.setDescription("CC L1P pixel classification");
        l1pBand.setSampleCoding(l1pFC);
        ProductNodeGroup<Mask> maskGroup = l1pProduct.getMaskGroup();

        addMask(maskGroup, CC_LAND_FLAG_NAME, "Land flag", new Color(238, 223, 145), 0.0f);
        addMask(maskGroup, CC_COASTLINE_FLAG_NAME, "Coastline flag", Color.GREEN, 0.5f);
        addMask(maskGroup, CC_CLOUD_FLAG_NAME, "Cloud flag", Color.white, 0.0f);
        addMask(maskGroup, CC_CLOUD_AMBIGUOUS_FLAG_NAME, "Ambiguous Cloud flag", Color.YELLOW, 0.5f);
        addMask(maskGroup, CC_CLOUD_BUFFER_FLAG_NAME, "Cloud buffer flag", Color.RED, 0.5f);
        addMask(maskGroup, CC_CLOUD_SHADOW_FLAG_NAME, "Cloud shadow flag", Color.BLUE, 0.5f);
        addMask(maskGroup, CC_SNOW_ICE_FLAG_NAME, "Snow/Ice flag", Color.CYAN, 0.5f);
        addMask(maskGroup, CC_MIXEDPIXEL_FLAG_NAME, "Mixture of water and land/floating vegetation", Color.GREEN.darker().darker(), 0.5f);
        addMask(maskGroup, CC_GLINTRISK_FLAG_NAME, "Risk that pixel is under glint", Color.pink, 0.5f);

    }

    private void addMask(ProductNodeGroup<Mask> maskGroup, String flagName, String description, Color color,
                         float transparency) {
        int width = sourceProduct.getSceneRasterWidth();
        int height = sourceProduct.getSceneRasterHeight();
        String maskPrefix = "l1p_";
        Mask mask = Mask.BandMathsType.create(maskPrefix + flagName.toLowerCase(),
                description, width, height,
                L1P_FLAG_BAND_NAME + "." + flagName,
                color, transparency);
        maskGroup.add(mask);
    }

    private void checkForExistingFlagBand(Product idepixProduct, String flagBandName) {
        if (!idepixProduct.containsBand(flagBandName)) {
            String msg = String.format("Flag band '%1$s' is not generated by operator '%2$s' ",
                    flagBandName, IDEPIX_OPERATOR_ALIAS);
            throw new OperatorException(msg);
        }
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle rectangle = targetTile.getRectangle();
        final Tile cloudTile = getSourceTile(cloudFlagBand, rectangle);

        for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
            checkForCancellation();
            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                targetTile.setSample(x, y, LAND_BIT_INDEX,
                        cloudTile.getSampleBit(x, y, CoastColourClassificationOp.F_LAND));
                targetTile.setSample(x, y, COASTLINE_BIT_INDEX,
                        cloudTile.getSampleBit(x, y, CoastColourClassificationOp.F_COASTLINE));
                targetTile.setSample(x, y, CLOUD_BIT_INDEX,
                        cloudTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD));
                targetTile.setSample(x, y, CLOUD_AMBIGUOUS_BIT_INDEX,
                        cloudTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD_AMBIGUOUS));
                targetTile.setSample(x, y, CLOUD_BUFFER_BIT_INDEX,
                        cloudTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD_BUFFER));
                targetTile.setSample(x, y, CLOUD_SHADOW_BIT_INDEX,
                        cloudTile.getSampleBit(x, y, CoastColourClassificationOp.F_CLOUD_SHADOW));
                targetTile.setSample(x, y, SNOW_ICE_BIT_INDEX,
                        cloudTile.getSampleBit(x, y, CoastColourClassificationOp.F_SNOW_ICE));
                targetTile.setSample(x, y, MIXEDPIXEL_BIT_INDEX,
                        cloudTile.getSampleBit(x, y, CoastColourClassificationOp.F_MIXED_PIXEL));
                targetTile.setSample(x, y, GLINTRISK_BIT_INDEX,
                        cloudTile.getSampleBit(x, y, CoastColourClassificationOp.F_GLINTRISK));
            }
        }

    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L1POp.class);
        }
    }
}
