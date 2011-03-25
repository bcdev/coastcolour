package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.idepix.operators.CloudScreeningSelector;

import java.util.HashMap;

@OperatorMetadata(alias = "CoastColour.L2W")
public class L2WOp extends Operator {

    @SourceProduct(description = "MERIS L1B, L1P or L2R product")
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

    @Parameter(defaultValue = "false")
    private boolean useIdepix;

    @Parameter(defaultValue = "CoastColour", valueSet = {"GlobAlbedo", "QWG", "CoastColour"})
    private CloudScreeningSelector algorithm;

    @Parameter(defaultValue = "l1p_flags.F_LANDCONS",
               label = "Land detection expression",
               description = "The arithmetic expression used for land detection.",
               notEmpty = true, notNull = true)
    private String landExpression;

    @Parameter(defaultValue = "l1p_flags.F_CLOUD || l1p_flags.F_SNOW_ICE",
               label = "Cloud/Ice detection expression",
               description = "The arithmetic expression used for cloud/ice detection.",
               notEmpty = true, notNull = true)
    private String cloudIceExpression;

    @Parameter(defaultValue = "true", label = "Output water leaving reflectance",
               description = "Toggles the output of water leaving irradiance reflectance.")
    private boolean outputReflec;


    @Override
    public void initialize() throws OperatorException {

        Product sourceProduct = this.sourceProduct;
        if (!isL2RSourceProduct(sourceProduct)) {
            HashMap<String, Object> l2rParams = new HashMap<String, Object>();
            l2rParams.put("doCalibration", doCalibration);
            l2rParams.put("doSmile", doSmile);
            l2rParams.put("doEqualization", doEqualization);
            l2rParams.put("useIdepix", useIdepix);
            l2rParams.put("algorithm", algorithm);
            l2rParams.put("landExpression", landExpression);
            l2rParams.put("cloudIceExpression", cloudIceExpression);
            sourceProduct = GPF.createProduct("CoastColour.L2R", l2rParams, sourceProduct);
        }

        HashMap<String, Product> sourceProducts = new HashMap<String, Product>();
        sourceProducts.put("source", sourceProduct);
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("doAtmosphericCorrection", false);
        parameters.put("outputReflec", outputReflec);
        Product targetProduct = GPF.createProduct("Meris.Case2IOP", parameters, sourceProducts);
        setTargetProduct(targetProduct);
    }

    private boolean isL2RSourceProduct(Product sourceProduct) {
        return sourceProduct.containsBand("agc_flags");
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L2WOp.class);
        }
    }
}
