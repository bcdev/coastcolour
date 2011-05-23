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
import org.esa.beam.util.ProductUtils;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L1P")
public class L1POp extends Operator {

    private static final String IDEPIX_OPERATOR_ALIAS = "idepix.ComputeChain";
    private static final String RADIOMETRY_OPERATOR_ALIAS = "Meris.CorrectRadiometry";
    private static final String CLOUD_FLAG_BAND_NAME = "cloud_classif_flags";
    private static final String LAND_FLAG_BAND_NAME = "land_classif_flags";
    private static final String L1P_FLAG_BAND_NAME = "l1p_flags";

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

    @Parameter(defaultValue = "false",
               label = "Perform equalization",
               description = "Perform removal of detector-to-detector systematic radiometric differences in MERIS L1b data products.")
    private boolean doEqualization;

    @Parameter(defaultValue = "true")
    private boolean useIdepix;

    @Parameter(defaultValue = "CoastColour", valueSet = {"GlobAlbedo", "QWG", "CoastColour"})
    private CloudScreeningSelector algorithm;

    @Parameter(label = "Bright Test Threshold ", defaultValue = "0.03")
    private double brightTestThreshold;
    @Parameter(label = "Bright Test Reference Wavelength [nm]", defaultValue = "865",
               valueSet = {
                       "412", "442", "490", "510", "560",
                       "620", "665", "681", "705", "753",
                       "760", "775", "865", "890", "900"
               })
    private int brightTestWavelength;

    private Band landFlagBand;
    private Band cloudFlagBand;
    private int shiftCloudFlags;


    @Override
    public void initialize() throws OperatorException {

        final Map<String, Object> rcParams = new HashMap<String, Object>();
        rcParams.put("doCalibration", doCalibration);
        rcParams.put("doSmile", doSmile);
        rcParams.put("doEqualization", doEqualization);
        rcParams.put("doRadToRefl", false);
        final Product l1pProduct = GPF.createProduct(RADIOMETRY_OPERATOR_ALIAS, rcParams, sourceProduct);
        // TODO - cause of a bug in MerisRadiometryCorrectionOp (BEAM-1305) we need to
        // copy the masks manually
        ProductUtils.copyMasks(sourceProduct, l1pProduct);

        if (useIdepix) {
            HashMap<String, Object> idepixParams = new HashMap<String, Object>();
            idepixParams.put("algorithm", algorithm);
            idepixParams.put("ipfQWGUserDefinedRhoToa442Threshold", brightTestThreshold);
            idepixParams.put("rhoAgReferenceWavelength", brightTestWavelength);
            idepixParams.put("ipfOutputLandWater", true);
            Product idepixProduct = GPF.createProduct(IDEPIX_OPERATOR_ALIAS, idepixParams, l1pProduct);

            checkForExistingFlagBand(idepixProduct, CLOUD_FLAG_BAND_NAME);
            checkForExistingFlagBand(idepixProduct, LAND_FLAG_BAND_NAME);
            landFlagBand = idepixProduct.getBand(LAND_FLAG_BAND_NAME);
            cloudFlagBand = idepixProduct.getBand(CLOUD_FLAG_BAND_NAME);

            attachFlagBandL1P(l1pProduct, idepixProduct);
        }

        reorderBands(l1pProduct);

        String l1pProductType = sourceProduct.getProductType().substring(0, 8) + "CCL1P";
        l1pProduct.setProductType(l1pProductType);
        setTargetProduct(l1pProduct);
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

    private void attachFlagBandL1P(Product l1pProduct, Product idepixProduct) {
        FlagCoding l1pFC = new FlagCoding(L1P_FLAG_BAND_NAME);
        final FlagCoding landFC = idepixProduct.getFlagCodingGroup().get(LAND_FLAG_BAND_NAME);
        final FlagCoding cloudFC = idepixProduct.getFlagCodingGroup().get(CLOUD_FLAG_BAND_NAME);

        copyFlags(landFC, l1pFC, 0);
        shiftCloudFlags = landFC.getNumAttributes();
        copyFlags(cloudFC, l1pFC, shiftCloudFlags);

        l1pProduct.getFlagCodingGroup().add(l1pFC);
        final Band l1pBand = l1pProduct.addBand(L1P_FLAG_BAND_NAME, ProductData.TYPE_INT32);
        l1pBand.setSampleCoding(l1pFC);

        Mask[] landMasks = createLandMasks(landFC);
        for (Mask mask : landMasks) {
            l1pProduct.getMaskGroup().add(mask);
        }
        Mask[] cloudMasks = createCloudMasks(cloudFC);
        for (Mask mask : cloudMasks) {
            l1pProduct.getMaskGroup().add(mask);
        }
    }

    private Mask[] createCloudMasks(FlagCoding cloudFC) {
        final String[] flagNames = cloudFC.getFlagNames();
        final Mask[] masks = new Mask[flagNames.length];
        final Product sourceProduct = cloudFC.getProduct();
        final int width = sourceProduct.getSceneRasterWidth();
        final int height = sourceProduct.getSceneRasterHeight();
        final ProductNodeGroup<Mask> maskGroup = sourceProduct.getMaskGroup();
        for (int i = 0; i < flagNames.length; i++) {
            String flagName = flagNames[i];
            final String maskName = flagName.toLowerCase();
            final Mask sourceMask = maskGroup.get(maskName);
            masks[i] = Mask.BandMathsType.create(maskName, sourceMask.getDescription(),
                                                 width, height, L1P_FLAG_BAND_NAME + "." + flagName,
                                                 sourceMask.getImageColor(), sourceMask.getImageTransparency());
        }
        return masks;
    }

    private Mask[] createLandMasks(FlagCoding landFC) {
        final String[] flagNames = landFC.getFlagNames();
        final Mask[] masks = new Mask[flagNames.length];
        final int width = landFC.getProduct().getSceneRasterWidth();
        final int height = landFC.getProduct().getSceneRasterHeight();
        Color green = Color.GREEN.brighter();
        for (int i = 0; i < flagNames.length; i++) {
            String flagName = flagNames[i];
            final String maskName = flagName.toLowerCase();
            masks[i] = Mask.BandMathsType.create(maskName, "", width, height, L1P_FLAG_BAND_NAME + "." + flagName,
                                                 green, 0.5);
            green = green.darker();
        }
        return masks;
    }

    private void copyFlags(FlagCoding sourceFC, FlagCoding targetFC, int shiftMaskValue) {
        final String[] flagNames = sourceFC.getFlagNames();
        for (String flagName : flagNames) {
            final String description = sourceFC.getFlag(flagName).getDescription();
            final int flagMask = sourceFC.getFlagMask(flagName);
            final int newMaskValue = flagMask << shiftMaskValue;
            targetFC.addFlag(flagName, newMaskValue, description);
        }
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
        final Tile landTile = getSourceTile(landFlagBand, rectangle);
        final Tile cloudTile = getSourceTile(cloudFlagBand, rectangle);

        final int[] targetBuffer = targetTile.getDataBufferInt();
        final byte[] landBuffer = landTile.getDataBufferByte();
        final short[] cloudBuffer = cloudTile.getDataBufferShort();
        for (int i = 0; i < targetBuffer.length; i++) {
            targetBuffer[i] = landBuffer[i] | (cloudBuffer[i] << shiftCloudFlags);
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L1POp.class);
        }
    }
}
