package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.ParseException;
import java.util.HashMap;

import static org.junit.Assert.*;

public class L2ROpTest {

    @BeforeClass
    public static void start() {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }


    @Test
    public void testCreateProductFromL1B() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        testTargetProduct(source);
    }

    @Test
    public void testCreateProductFromL1P() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        source = GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, source);
        testTargetProduct(source);
    }

    private static void testTargetProduct(Product source) {

        Product target = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, source);
        assertNotNull(target);

        L1POpTest.dumpBands(target);

        String[] expectedBandNames = new String[]{
                "reflec_1", "reflec_2", "reflec_13",
        };
        for (String name : expectedBandNames) {
            assertNotNull("Target band missing: " + name, target.getBand(name));
        }

        // assert radiance bands are not copied to L2R
        for (int i = 1; i <= 15; i++) {
            String bandName = String.format("radiance_%1$d", i);
            assertNull("Target contains band: " + bandName, target.getBand(bandName));
        }

        // Tests on generated L1P flags dataset
        L1POpTest.testFlags(target, "l1p_flags");

        // Tests on generated L2R flags dataset
        L1POpTest.testFlags(target, "agc_flags");
    }


}
