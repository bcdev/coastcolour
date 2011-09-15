package org.esa.beam.coastcolour.processing;

import com.bc.ceres.core.ProgressMonitor;
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
import org.esa.beam.idepix.operators.CloudScreeningSelector;
import org.esa.beam.idepix.operators.CoastColourCloudClassificationOp;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L1P",
                  version = "1.3")
public class L1POp extends Operator {

    public static final String CC_LAND_FLAG_NAME = "CC_LAND";
    public static final String CC_COASTLINE_FLAG_NAME = "CC_COASTLINE";
    public static final String CC_CLOUD_FLAG_NAME = "CC_CLOUD";
    public static final String CC_CLOUD_SPATIAL_FLAG_NAME = "CC_CLOUD_SPATIAL";
    public static final String CC_CLOUD_BUFFER_FLAG_NAME = "CC_CLOUD_BUFFER";
    public static final String CC_CLOUD_SHADOW_FLAG_NAME = "CC_CLOUD_SHADOW";
    public static final String CC_SNOW_ICE_FLAG_NAME = "CC_SNOW_ICE";
    public static final String CC_LANDRISK_FLAG_NAME = "CC_LANDRISK";
    public static final String CC_GLINTRISK_FLAG_NAME = "CC_GLINTRISK";

    private static final String IDEPIX_OPERATOR_ALIAS = "idepix.ComputeChain";
    private static final String RADIOMETRY_OPERATOR_ALIAS = "Meris.CorrectRadiometry";
    private static final String CLOUD_FLAG_BAND_NAME = "cloud_classif_flags";
    private static final String L1P_FLAG_BAND_NAME = "l1p_flags";
    private static final int LAND_BIT_INDEX = 0;
    private static final int COASTLINE_BIT_INDEX = 1;
    private static final int CLOUD_BIT_INDEX = 2;
    private static final int CLOUD_SPATIAL_BIT_INDEX = 3;
    private static final int CLOUD_BUFFER_BIT_INDEX = 4;
    private static final int CLOUD_SHADOW_BIT_INDEX = 5;
    private static final int SNOW_ICE_BIT_INDEX = 6;
    private static final int LANDRISK_BIT_INDEX = 7;
    private static final int GLINTRISK_BIT_INDEX = 8;

    @SourceProduct(alias = "l1b", description = "MERIS L1b (N1) product")
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
    private Product radiometryProduct;


    @Override
    public void initialize() throws OperatorException {

        final Map<String, Object> rcParams = createRadiometryParameterMap();
        radiometryProduct = GPF.createProduct(RADIOMETRY_OPERATOR_ALIAS, rcParams, sourceProduct);

        final Product l1pProduct = createL1PProduct(radiometryProduct);

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

    private HashMap<String, Object> createIdepixParameterMap() {
        HashMap<String, Object> idepixParams = new HashMap<String, Object>();
        idepixParams.put("algorithm", algorithm);
        idepixParams.put("ipfQWGUserDefinedRhoToa442Threshold", brightTestThreshold);
        idepixParams.put("rhoAgReferenceWavelength", brightTestWavelength);
        idepixParams.put("ccSpatialCloudTest", true);
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
        copyFlagBands(radiometryProduct, l1pProduct);
        ProductUtils.copyTiePointGrids(radiometryProduct, l1pProduct);
        ProductUtils.copyGeoCoding(radiometryProduct, l1pProduct);

        return l1pProduct;
    }

    private void copyFlagBands(Product radiometryProduct, Product l1pProduct) {
        ProductUtils.copyFlagBands(radiometryProduct, l1pProduct);
        final Band[] radiometryBands = radiometryProduct.getBands();
        for (Band band : radiometryBands) {
            if (band.isFlagBand()) {
                final Band targetBand = l1pProduct.getBand(band.getName());
                targetBand.setSourceImage(band.getSourceImage());
            }
        }
    }

    private void copyBands(Product radiometryProduct, Product l1pProduct) {
        final Band[] radiometryBands = radiometryProduct.getBands();
        for (Band band : radiometryBands) {
            if (!band.isFlagBand()) {
                final Band targetBand = ProductUtils.copyBand(band.getName(), radiometryProduct, l1pProduct);
                targetBand.setSourceImage(band.getSourceImage());
            }
        }
    }


    @Override
    public void dispose() {
        if (idepixProduct != null) {
            idepixProduct.dispose();
            idepixProduct = null;
        }
        if (radiometryProduct != null) {
            radiometryProduct.dispose();
            radiometryProduct = null;
        }
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
        l1pFC.addFlag(CC_CLOUD_SPATIAL_FLAG_NAME, BitSetter.setFlag(0, CLOUD_SPATIAL_BIT_INDEX),
                      "Pixel masked by spatial cloud filter");
        l1pFC.addFlag(CC_CLOUD_BUFFER_FLAG_NAME, BitSetter.setFlag(0, CLOUD_BUFFER_BIT_INDEX),
                      "Pixel masked as cloud buffer");
        l1pFC.addFlag(CC_CLOUD_SHADOW_FLAG_NAME, BitSetter.setFlag(0, CLOUD_SHADOW_BIT_INDEX),
                      "Pixel masked as cloud shadow");
        l1pFC.addFlag(CC_SNOW_ICE_FLAG_NAME, BitSetter.setFlag(0, SNOW_ICE_BIT_INDEX), "Pixel masked as snow/ice");
        l1pFC.addFlag(CC_LANDRISK_FLAG_NAME, BitSetter.setFlag(0, LANDRISK_BIT_INDEX), "Potential land pixel");
        l1pFC.addFlag(CC_GLINTRISK_FLAG_NAME, BitSetter.setFlag(0, GLINTRISK_BIT_INDEX),
                      "Risk that pixel is under glint");

        l1pProduct.getFlagCodingGroup().add(l1pFC);
        final Band l1pBand = l1pProduct.addBand(L1P_FLAG_BAND_NAME, ProductData.TYPE_INT16);
        l1pBand.setSampleCoding(l1pFC);
        int width = l1pProduct.getSceneRasterWidth();
        int height = l1pProduct.getSceneRasterHeight();
        ProductNodeGroup<Mask> maskGroup = l1pProduct.getMaskGroup();
        String maskPrefix = "l1p_";
        int maskIndex = 0;
        maskGroup.add(maskIndex++, Mask.BandMathsType.create(maskPrefix + CC_LAND_FLAG_NAME.toLowerCase(), "Land flag",
                                                             width, height,
                                                             L1P_FLAG_BAND_NAME + "." + CC_LAND_FLAG_NAME,
                                                             Color.GREEN.darker(), 0.5));
        maskGroup.add(maskIndex++, Mask.BandMathsType.create(maskPrefix + CC_COASTLINE_FLAG_NAME.toLowerCase(),
                                                             "Coastline flag", width, height,
                                                             L1P_FLAG_BAND_NAME + "." + CC_COASTLINE_FLAG_NAME,
                                                             Color.GREEN, 0.5));
        maskGroup.add(maskIndex++,
                      Mask.BandMathsType.create(maskPrefix + CC_CLOUD_FLAG_NAME.toLowerCase(), "Cloud flag",
                                                width, height, L1P_FLAG_BAND_NAME + "." + CC_CLOUD_FLAG_NAME,
                                                Color.YELLOW, 0.5));
        maskGroup.add(maskIndex++, Mask.BandMathsType.create(maskPrefix + CC_CLOUD_SPATIAL_FLAG_NAME.toLowerCase(),
                                                             "Spatial Cloud flag", width, height,
                                                             L1P_FLAG_BAND_NAME + "." + CC_CLOUD_SPATIAL_FLAG_NAME,
                                                             Color.YELLOW.darker(), 0.5));
        maskGroup.add(maskIndex++, Mask.BandMathsType.create(maskPrefix + CC_CLOUD_BUFFER_FLAG_NAME.toLowerCase(),
                                                             "Cloud buffer flag", width, height,
                                                             L1P_FLAG_BAND_NAME + "." + CC_CLOUD_BUFFER_FLAG_NAME,
                                                             Color.RED, 0.5));
        maskGroup.add(maskIndex++, Mask.BandMathsType.create(maskPrefix + CC_CLOUD_SHADOW_FLAG_NAME.toLowerCase(),
                                                             "Cloud shadow flag", width, height,
                                                             L1P_FLAG_BAND_NAME + "." + CC_CLOUD_SHADOW_FLAG_NAME,
                                                             Color.BLUE, 0.5));
        maskGroup.add(maskIndex++, Mask.BandMathsType.create(maskPrefix + CC_SNOW_ICE_FLAG_NAME.toLowerCase(),
                                                             "Snow/Ice flag", width, height,
                                                             L1P_FLAG_BAND_NAME + "." + CC_SNOW_ICE_FLAG_NAME,
                                                             Color.CYAN, 0.5));
        maskGroup.add(maskIndex++, Mask.BandMathsType.create(maskPrefix + CC_LANDRISK_FLAG_NAME.toLowerCase(),
                                                             "Potential land pixel", width, height,
                                                             L1P_FLAG_BAND_NAME + "." + CC_LANDRISK_FLAG_NAME,
                                                             Color.GREEN.darker().darker(), 0.5));
        maskGroup.add(maskIndex++, Mask.BandMathsType.create(maskPrefix + CC_GLINTRISK_FLAG_NAME.toLowerCase(),
                                                             "Risk that pixel is under glint", width, height,
                                                             L1P_FLAG_BAND_NAME + "." + CC_GLINTRISK_FLAG_NAME,
                                                             Color.pink, 0.5));

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
                targetTile.setSample(x, y, LAND_BIT_INDEX, cloudTile.getSampleBit(x, y,
                                                                                  CoastColourCloudClassificationOp.F_LAND));
                targetTile.setSample(x, y, COASTLINE_BIT_INDEX, cloudTile.getSampleBit(x, y,
                                                                                       CoastColourCloudClassificationOp.F_COASTLINE));
                targetTile.setSample(x, y, CLOUD_BIT_INDEX, cloudTile.getSampleBit(x, y,
                                                                                   CoastColourCloudClassificationOp.F_CLOUD));
                targetTile.setSample(x, y, CLOUD_SPATIAL_BIT_INDEX, cloudTile.getSampleBit(x, y,
                                                                                           CoastColourCloudClassificationOp.F_CLOUD_SPATIAL));
                targetTile.setSample(x, y, CLOUD_BUFFER_BIT_INDEX, cloudTile.getSampleBit(x, y,
                                                                                          CoastColourCloudClassificationOp.F_CLOUD_BUFFER));
                targetTile.setSample(x, y, CLOUD_SHADOW_BIT_INDEX, cloudTile.getSampleBit(x, y,
                                                                                          CoastColourCloudClassificationOp.F_CLOUD_SHADOW));
                targetTile.setSample(x, y, SNOW_ICE_BIT_INDEX, cloudTile.getSampleBit(x, y,
                                                                                      CoastColourCloudClassificationOp.F_SNOW_ICE));
                targetTile.setSample(x, y, LANDRISK_BIT_INDEX, cloudTile.getSampleBit(x, y,
                                                                                      CoastColourCloudClassificationOp.F_LANDRISK));
                targetTile.setSample(x, y, GLINTRISK_BIT_INDEX, cloudTile.getSampleBit(x, y,
                                                                                       CoastColourCloudClassificationOp.F_GLINTRISK));
            }
        }

    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L1POp.class);
        }
    }
}
