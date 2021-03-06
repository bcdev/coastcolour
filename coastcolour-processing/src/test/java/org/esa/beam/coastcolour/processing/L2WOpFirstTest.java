package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.*;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class L2WOpFirstTest {

    private Product target;
    private static Product l1bProduct;

    @BeforeClass
    public static void start() throws ParseException {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
        l1bProduct = L1POpTest.createL1bProduct();
    }

    @AfterClass
    public static void afterClass() throws ParseException {
        l1bProduct.dispose();
        l1bProduct = null;
    }

    @After
    public void after() {
        if (target != null) {
            target.dispose();
            target = null;
        }
        System.gc();
    }

    @Test
    public void testCreateProductFromL1B() throws OperatorException, ParseException {
        HashMap<String, Object> l1pParams = new HashMap<>();
        l1pParams.put("doEqualization", false);
        final String[] expectedBandNames = {"iop_a_ys_443", "conc_tsm", "conc_chl_nn", "turbidity"};
        target = L2WOpTestHelper.testTargetProduct(l1bProduct, "MER_FR__CCL2W", expectedBandNames, l1pParams);

        String[] notExpectedBandNames = new String[]{"toa_reflec_1", "toa_reflec_2", "toa_reflec_13"};
        for (String notExpectedBandName : notExpectedBandNames) {
            assertFalse("Product should not contain " + notExpectedBandName, target.containsBand(notExpectedBandName));
        }

    }

    @Test
    public void testCreateProductFromL1P() throws OperatorException, ParseException {
        HashMap<String, Object> l1pParams = new HashMap<>();
        l1pParams.put("doEqualization", false);
        Product source = getL1pProduct(l1bProduct);
        final String[] expectedBandNames = {"iop_a_ys_443", "conc_tsm", "conc_chl_nn", "turbidity"};
        target = L2WOpTestHelper.testTargetProduct(source, "MER_FR__CCL2W", expectedBandNames, l1pParams);

        String[] notExpectedBandNames = new String[]{"toa_reflec_1", "toa_reflec_2", "toa_reflec_13"};
        for (String notExpectedBandName : notExpectedBandNames) {
            assertFalse("Product should not contain " + notExpectedBandName, target.containsBand(notExpectedBandName));
        }

    }

    @Test
    public void testCreateProductFromL2R() throws OperatorException, ParseException {
        Product source = getL1pProduct(l1bProduct);
        source = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, source);
        final String[] expectedBandNames = {
                "iop_a_ys_443", "iop_a_total_443", "iop_bb_spm_443",
                "conc_tsm", "conc_chl_nn", "turbidity"
        };
        target = L2WOpTestHelper.testTargetProduct(source, "MER_FR__CCL2W", expectedBandNames, GPF.NO_PARAMS);
        source.dispose();
    }

    @Test
    public void testCreateProductWithReflectances() throws OperatorException, ParseException {
        Product source = getL1pProduct(l1bProduct);
        Product l2rProduct = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, source);
        final Map<String, Object> params = new HashMap<String, Object>();
        params.put("outputReflec", true);
        String[] expectedBandNames = {
                "reflec_1", "reflec_2", "reflec_13", "iop_a_ys_443",
                "conc_tsm", "conc_chl_nn", "turbidity"
        };
        target = L2WOpTestHelper.testTargetProduct(l2rProduct, "MER_FR__CCL2W", expectedBandNames, params);
        l2rProduct.dispose();
        source.dispose();
    }

    private Product getL1pProduct(Product source) {
        HashMap<String, Object> l1pParams = new HashMap<>();
        l1pParams.put("doEqualization", false);
        return GPF.createProduct("CoastColour.L1P", l1pParams, source);
    }

}
