package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.ParseException;

import static org.junit.Assert.*;

public class L2WOpTest {

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
        source = getL1pProduct(source);
        testTargetProduct(source);
    }

    @Test
    public void testCreateProductFromL2R() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        source = getL1pProduct(source);
        source = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, source);
        testTargetProduct(source);
    }

    private static void testTargetProduct(Product source) {

        Product target = GPF.createProduct("CoastColour.L2W", GPF.NO_PARAMS, source);
        assertNotNull(target);

        L1POpTest.dumpBands(target);

        String[] expectedBandNames = new String[]{
                "reflec_1", "reflec_2", "reflec_13", "a_ys_443", "tsm", "chl_conc"
        };
        for (String name : expectedBandNames) {
            assertNotNull("Target band missing: " + name, target.getBand(name));
        }

        // Tests on generated L1P flags dataset
        L1POpTest.testFlags(target, "l1p_flags");

        // Tests on generated L2R flags dataset
        L1POpTest.testFlags(target, "agc_flags");

        // Tests on generated L2W flags dataset
        L1POpTest.testFlags(target, "case2_flags");

//            private static final String BAND_NAME_A_GELBSTOFF = "a_gelbstoff";
//    private static final String BAND_NAME_A_PIGMENT = "a_pig";
//    private static final String BAND_NAME_A_TOTAL = "a_total";
//    private static final String BAND_NAME_B_TSM = "b_tsm";
//    private static final String BAND_NAME_TSM = "tsm";
//    private static final String BAND_NAME_CHL_CONC = "chl_conc";
//    private static final String BAND_NAME_CHI_SQUARE = "chiSquare";
//    private static final String BAND_NAME_K_MIN = "K_min";
//    private static final String BAND_NAME_Z90_MAX = "Z90_max";
//    private static final String BAND_NAME_KD_490 = "Kd_490";
//    private static final String BAND_NAME_TURBIDITY_INDEX = "turbidity_index";
    }

    private Product getL1pProduct(Product source) {
        return GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, source);
    }

}
