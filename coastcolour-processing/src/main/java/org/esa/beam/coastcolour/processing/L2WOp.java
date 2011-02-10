package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;

import java.util.HashMap;

@OperatorMetadata(alias = "CoastColour.L2W")
public class L2WOp extends Operator {

    @SourceProduct(description = "MERIS L1B, L1P or L2R product")
    private Product sourceProduct;

    @Override
    public void initialize() throws OperatorException {

        Product sourceProduct = this.sourceProduct;
        if (!isL2RSourceProduct(sourceProduct)) {
            sourceProduct = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, sourceProduct);
        }

        HashMap<String, Product> sourceProducts = new HashMap<String, Product>();
        sourceProducts.put("source", sourceProduct);
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("doAtmosphericCorrection", false);
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
