package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class L2WOpTest {

    @BeforeClass
    public static void start() {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }


    @Test
    public void testCreateProductFromL1B() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        final String[] expectedBandNames = {"reflec_1", "reflec_2", "reflec_13", "a_ys_443", "tsm", "chl_conc"};
        testTargetProduct(source, expectedBandNames, GPF.NO_PARAMS);
    }

    @Test
    public void testCreateProductFromL1P() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        source = getL1pProduct(source);
        final String[] expectedBandNames = {"reflec_1", "reflec_2", "reflec_13", "a_ys_443", "tsm", "chl_conc"};
        testTargetProduct(source, expectedBandNames, GPF.NO_PARAMS);
    }

    @Test
    public void testCreateProductFromL2R() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        source = getL1pProduct(source);
        source = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, source);
        final String[] expectedBandNames = {"reflec_1", "reflec_2", "reflec_13", "a_ys_443", "tsm", "chl_conc"};
        testTargetProduct(source, expectedBandNames, GPF.NO_PARAMS);
    }

    @Test
    public void testCreateProductWithoutReflectances() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        source = getL1pProduct(source);
        source = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, source);
        final Map<String, Object> params = new HashMap<String, Object>();
        params.put("outputReflec", false);
        final Product target = testTargetProduct(source, new String[]{"a_ys_443", "tsm", "chl_conc"}, params);

        String[] notExpectedBandNames = new String[]{"reflec_1", "reflec_2", "reflec_13"};
        for (String notExpectedBandName : notExpectedBandNames) {
            assertFalse("Product should not contain " + notExpectedBandName, target.containsBand(notExpectedBandName));
        }
    }

    private static Product testTargetProduct(Product source, String[] expectedBandNames,
                                             Map<String, Object> l2wParams) {

        Product target = GPF.createProduct("CoastColour.L2W", l2wParams, source);
        assertNotNull(target);

        L1POpTest.dumpBands(target);

        for (String name : expectedBandNames) {
            assertNotNull("Target band missing: " + name, target.getBand(name));
        }

        // Tests on generated L1P flags dataset
        L1POpTest.testFlags(target, "l1p_flags");

        // Tests on generated L2R flags dataset
        L1POpTest.testFlags(target, "agc_flags");

        // Tests on generated L2W flags dataset
        L1POpTest.testFlags(target, "case2_flags");

        return target;
    }

    private Product getL1pProduct(Product source) {
        return GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, source);
    }

}
