package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.meris.MerisOp;
import org.esa.beam.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L1P.Icol", version = "1.6.5-SNAPSHOT",
                  authors = "Olaf Danne",
                  copyright = "(c) 2012 Brockmann Consult",
                  description = "Wrapper operator to apply ICOL correction to L1P product.")
public class L1PIcolOp extends Operator {

    @SourceProduct(alias = "l1b", description = "MERIS L1P non-icolized product")
    private Product sourceProduct;

    @Override
    public void initialize() throws OperatorException {
        Product icolizedL1pProduct = createIcolL1pProduct();
        setTargetProduct(icolizedL1pProduct);
    }

    private Product createIcolL1pProduct() {
        HashMap<String, Object> icolParams = new HashMap<String, Object>();
        icolParams.put("icolAerosolCase2", true);
        icolParams.put("productType", 0);
        icolParams.put("aeArea", AeArea.COASTAL_OCEAN);
        icolParams.put("useAdvancedLandWaterMask", true);
        Map<String, Product> sourceProducts = new HashMap<String, Product>(1);
        sourceProducts.put("sourceProduct", sourceProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(MerisOp.class), icolParams, sourceProducts);
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L1PIcolOp.class);
        }
    }
}
