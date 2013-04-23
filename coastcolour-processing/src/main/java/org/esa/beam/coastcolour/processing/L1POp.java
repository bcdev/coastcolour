package org.esa.beam.coastcolour.processing;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.jai.tilecache.DefaultSwapSpace;
import com.bc.ceres.jai.tilecache.SwappingTileCache;
import org.esa.beam.atmosphere.operator.GlintCorrectionOperator;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.internal.OperatorImage;
import org.esa.beam.idepix.operators.CloudScreeningSelector;
import org.esa.beam.idepix.operators.CoastColourCloudClassificationOp;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.meris.MerisOp;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.OpImage;
import javax.media.jai.TileCache;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L1P", version = "1.6.3",
                  authors = "Marco Peters, Norman Fomferra",
                  copyright = "(c) 2011 Brockmann Consult",
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

    private static final String IDEPIX_OPERATOR_ALIAS = "idepix.ComputeChain";
    private static final String RADIOMETRY_OPERATOR_ALIAS = "Meris.CorrectRadiometry";
    private static final String CLOUD_FLAG_BAND_NAME = "cloud_classif_flags";

    @SourceProduct(alias = "l1b", description = "MERIS L1b (N1) product")
    private Product sourceProduct;

    @Parameter(defaultValue = "false",
               label = "Perform ICOL correction",
               description = "Whether to perform ICOL correction.")
    private boolean doIcol;

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

    @Parameter(defaultValue = "true",
               description = "Performs pixel classification if enabled.")
    private boolean useIdepix;

    @Parameter(defaultValue = "CoastColour", valueSet = {"GlobAlbedo", "QWG", "CoastColour"},
               description = "Specifies the name of the cloud screening algorithm used by the pixel classification.")
    private CloudScreeningSelector algorithm;

    @Parameter(label = "Bright Test Threshold ", defaultValue = "0.03",
               description = "Threshold used by the brightness test in the CoastColour cloud screening.")
    private double brightTestThreshold;
    @Parameter(label = "Bright Test Reference Wavelength [nm]", defaultValue = "865",
               description = "Wavelength of the band used by the brightness test in the CoastColour cloud screening.",
               valueSet = {
                       "412", "442", "490", "510", "560",
                       "620", "665", "681", "705", "753",
                       "760", "775", "865", "890", "900"
               })
    private int brightTestWavelength;

    private Band cloudFlagBand;
    private Product idepixProduct;


    @Override
    public void initialize() throws OperatorException {

        final Map<String, Object> rcParams = createRadiometryParameterMap();
        Product radiometryProduct = GPF.createProduct(RADIOMETRY_OPERATOR_ALIAS, rcParams, sourceProduct);

        Product l1pProduct = createL1PProduct(radiometryProduct);

        // todo: it is likely not a good idea to put the 'heavy' ICOL on top of the L1P process.
        // maybe better do ICOL in a separate processing step, invoke it from an empty wrapper operator, e.g. L1PIcolOp
        if (doIcol) {
            l1pProduct = createIcolProduct(l1pProduct);
            attachFileTileCache(l1pProduct);
        }

        if (useIdepix) {
            HashMap<String, Object> idepixParams = createIdepixParameterMap();
            idepixProduct = GPF.createProduct(IDEPIX_OPERATOR_ALIAS, idepixParams, radiometryProduct);

            checkForExistingFlagBand(idepixProduct, CLOUD_FLAG_BAND_NAME);
            cloudFlagBand = idepixProduct.getBand(CLOUD_FLAG_BAND_NAME);

            attachFlagBandL1P(l1pProduct);
            sortFlagCodings(l1pProduct);
        }

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
        idepixParams.put("algorithm", algorithm);
        idepixParams.put("ipfQWGUserDefinedRhoToa442Threshold", brightTestThreshold);
        idepixParams.put("rhoAgReferenceWavelength", brightTestWavelength);
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
        l1pFC.addFlag(CC_MIXEDPIXEL_FLAG_NAME, BitSetter.setFlag(0, MIXEDPIXEL_BIT_INDEX), "Potential land pixel");
        l1pFC.addFlag(CC_GLINTRISK_FLAG_NAME, BitSetter.setFlag(0, GLINTRISK_BIT_INDEX),
                      "Risk that pixel is under glint");

        l1pProduct.getFlagCodingGroup().add(l1pFC);
        final Band l1pBand = l1pProduct.addBand(L1P_FLAG_BAND_NAME, ProductData.TYPE_INT16);
        l1pBand.setDescription("CC L1P pixel classification");
        l1pBand.setSampleCoding(l1pFC);
        ProductNodeGroup<Mask> maskGroup = l1pProduct.getMaskGroup();

        addMask(maskGroup, CC_LAND_FLAG_NAME, "Land flag", new Color(238,223,145), 0.0f);
        addMask(maskGroup, CC_COASTLINE_FLAG_NAME, "Coastline flag", Color.GREEN, 0.5f);
        addMask(maskGroup, CC_CLOUD_FLAG_NAME, "Cloud flag", Color.white, 0.0f);
        addMask(maskGroup, CC_CLOUD_AMBIGUOUS_FLAG_NAME, "Ambiguous Cloud flag", Color.YELLOW, 0.5f);
        addMask(maskGroup, CC_CLOUD_BUFFER_FLAG_NAME, "Cloud buffer flag", Color.RED, 0.5f);
        addMask(maskGroup, CC_CLOUD_SHADOW_FLAG_NAME, "Cloud shadow flag", Color.BLUE, 0.5f);
        addMask(maskGroup, CC_SNOW_ICE_FLAG_NAME, "Snow/Ice flag", Color.CYAN, 0.5f);
        addMask(maskGroup, CC_MIXEDPIXEL_FLAG_NAME, "Potential land pixel", Color.GREEN.darker().darker(), 0.5f);
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
                                     cloudTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_LAND));
                targetTile.setSample(x, y, COASTLINE_BIT_INDEX,
                                     cloudTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_COASTLINE));
                targetTile.setSample(x, y, CLOUD_BIT_INDEX,
                                     cloudTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_CLOUD));
                targetTile.setSample(x, y, CLOUD_AMBIGUOUS_BIT_INDEX,
                                     cloudTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_CLOUD_AMBIGUOUS));
                targetTile.setSample(x, y, CLOUD_BUFFER_BIT_INDEX,
                                     cloudTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_CLOUD_BUFFER));
                targetTile.setSample(x, y, CLOUD_SHADOW_BIT_INDEX,
                                     cloudTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_CLOUD_SHADOW));
                targetTile.setSample(x, y, SNOW_ICE_BIT_INDEX,
                                     cloudTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_SNOW_ICE));
                targetTile.setSample(x, y, MIXEDPIXEL_BIT_INDEX,
                                     cloudTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_MIXED_PIXEL));
                targetTile.setSample(x, y, GLINTRISK_BIT_INDEX,
                                     cloudTile.getSampleBit(x, y, CoastColourCloudClassificationOp.F_GLINTRISK));
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
