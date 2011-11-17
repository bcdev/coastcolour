package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Marco Peters
 */
public class L2WOpTestHelper {

    static Product testTargetProduct(Product source, String expectedProductType, String[] expectedBandNames,
                                     Map<String, Object> l2wParams) {

        Product target = GPF.createProduct("CoastColour.L2W", l2wParams, source);
        assertNotNull(target);
        assertEquals(expectedProductType, target.getProductType());

        // enable for debugging
//        L1POpTest.dumpBands(target);

        for (String name : expectedBandNames) {
            assertNotNull("Target band missing: " + name, target.getBand(name));
        }

        String[] notExpectedBandNames = new String[]{
                "reflectance_1", "reflectance_4", "reflectance_13",
                "norm_refl_2", "norm_refl_7", "norm_refl_12",
        };
        for (String name : notExpectedBandNames) {
            assertNull("Target band not expected: " + name, target.getBand(name));
        }

        // Tests on generated L1P flags dataset
        L1POpTest.testFlags(target, "l1p_flags");

        // Tests on generated L2R flags dataset
        L1POpTest.testFlags(target, "l2r_flags");

        // Tests on generated L2W flags dataset
        L1POpTest.testFlags(target, "l2w_flags");

        assertEquals(source.getStartTime().toString(), target.getStartTime().toString());
        assertEquals(source.getEndTime().toString(), target.getEndTime().toString());

        // Test correct order of flag bands
        int l1FlagsBandIndex = target.getBandIndex("l1_flags");
        assertEquals(l1FlagsBandIndex + 1, target.getBandIndex("l1p_flags"));
        assertEquals(l1FlagsBandIndex + 2, target.getBandIndex("l2r_flags"));
        assertEquals(l1FlagsBandIndex + 3, target.getBandIndex("l2w_flags"));

        return target;
    }
}
