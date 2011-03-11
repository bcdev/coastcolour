package org.esa.beam.coastcolour.processing;

import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
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
import org.esa.beam.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L1P")
public class L1POp extends Operator {

    static final String IDEPIX_OPERATOR_ALIAS = "idepix.ComputeChain";
    static final String RADIOMETRY_OPERATOR_ALIAS = "Meris.CorrectRadiometry";
    static final String CLOUD_FLAG_BAND_NAME = "cloud_classif_flags";
    private static final String L1P_FLAG_BAND_NAME = "l1p_flags";
    private static final String L1_FLAG_BAND_NAME = "l1_flags";
    @SourceProduct(alias = "l1b", description = "MERIS L1b (N1) product")
    private Product sourceProduct;

    @Parameter(defaultValue = "true",
               label = "Perform calibration",
               description = "Whether to perform the calibration.")
    private boolean doCalibration;

    @Parameter(defaultValue = "true",
               label = "Perform SMILE correction",
               description = "Whether to perform SMILE correction.")
    private boolean doSmile;

    @Parameter(defaultValue = "true",
               label = "Perform equalization",
               description = "Perform removal of detector-to-detector systematic radiometric differences in MERIS L1b data products.")
    private boolean doEqualization;

    @Parameter(defaultValue = "true")
    private boolean useIdepix;

    @Parameter(defaultValue = "GlobAlbedo", valueSet = {"GlobAlbedo", "QWG", "CoastColour"})
    private CloudScreeningSelector algorithm;

    @Override
    public void initialize() throws OperatorException {

        final Map<String, Object> rcParams = new HashMap<String, Object>();
        rcParams.put("doCalibration", doCalibration);
        rcParams.put("doSmile", doSmile);
        rcParams.put("doEqualization", doEqualization);
        rcParams.put("doRadToRefl", false);
        final Product rcProduct = GPF.createProduct(RADIOMETRY_OPERATOR_ALIAS, rcParams, sourceProduct);

        if (useIdepix) {
            HashMap<String, Object> idepixParams = new HashMap<String, Object>();
            idepixParams.put("algorithm", algorithm);
            final Product idepixProduct = GPF.createProduct(IDEPIX_OPERATOR_ALIAS, idepixParams, rcProduct);

            if (!idepixProduct.containsBand(CLOUD_FLAG_BAND_NAME)) {
                String msg = String.format("Flag band '%1$s' is not generated by operator '%2$s' ",
                                           CLOUD_FLAG_BAND_NAME, IDEPIX_OPERATOR_ALIAS);
                throw new OperatorException(msg);
            }
            copyIdepixFlagBand(rcProduct, idepixProduct);
        }

        final String productType = rcProduct.getProductType();
        rcProduct.setProductType(productType.replaceFirst("_1P", "L1P"));
        setTargetProduct(rcProduct);
    }

    private void copyIdepixFlagBand(Product rcProduct, Product idepixProduct) {
        final Band[] idepixBands = idepixProduct.getBands();
        for (Band idepixBand : idepixBands) {
            // remove all flag bands except the CLOUD_FLAG_BAND_NAME
            // this is done, so ProductUtils.copyFlagBands(...) can be used
            if (idepixBand.isFlagBand() && !idepixBand.getName().equals(CLOUD_FLAG_BAND_NAME)) {
                idepixProduct.removeBand(idepixBand);
            }
        }
        final ProductNodeGroup<FlagCoding> idepixFlagCodingGroup = idepixProduct.getFlagCodingGroup();
        idepixFlagCodingGroup.remove(idepixFlagCodingGroup.get(L1_FLAG_BAND_NAME));

        ProductUtils.copyFlagBands(idepixProduct, rcProduct);
        final MultiLevelImage idepixFlagsSourceImage = idepixProduct.getBand(CLOUD_FLAG_BAND_NAME).getSourceImage();
        rcProduct.getBand(CLOUD_FLAG_BAND_NAME).setSourceImage(idepixFlagsSourceImage);
        rcProduct.getBand(CLOUD_FLAG_BAND_NAME).setName(L1P_FLAG_BAND_NAME);
        rcProduct.getFlagCodingGroup().get(CLOUD_FLAG_BAND_NAME).setName(L1P_FLAG_BAND_NAME);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L1POp.class);
        }
    }
}
