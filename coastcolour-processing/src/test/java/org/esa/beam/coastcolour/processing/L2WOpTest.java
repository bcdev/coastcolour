package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.PixelGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
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
        final String[] expectedBandNames = {"a_ys_443", "tsm", "chl_conc"};
        Product target = testTargetProduct(source, expectedBandNames, GPF.NO_PARAMS);

        String[] notExpectedBandNames = new String[]{"reflec_1", "reflec_2", "reflec_13"};
        for (String notExpectedBandName : notExpectedBandNames) {
            assertFalse("Product should not contain " + notExpectedBandName, target.containsBand(notExpectedBandName));
        }

    }

    @Test
    public void testCreateProductFromL1P() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        source = getL1pProduct(source);
        final String[] expectedBandNames = {"a_ys_443", "tsm", "chl_conc"};
        Product target = testTargetProduct(source, expectedBandNames, GPF.NO_PARAMS);

        String[] notExpectedBandNames = new String[]{"reflec_1", "reflec_2", "reflec_13"};
        for (String notExpectedBandName : notExpectedBandNames) {
            assertFalse("Product should not contain " + notExpectedBandName, target.containsBand(notExpectedBandName));
        }

    }

    @Test
    public void testCreateProductFromL2R() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        source = getL1pProduct(source);
        source = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, source);
        final String[] expectedBandNames = {"a_ys_443", "tsm", "chl_conc"};
        Product target = testTargetProduct(source, expectedBandNames, GPF.NO_PARAMS);

        String[] notExpectedBandNames = new String[]{"reflec_1", "reflec_2", "reflec_13"};
        for (String notExpectedBandName : notExpectedBandNames) {
            assertFalse("Product should not contain " + notExpectedBandName, target.containsBand(notExpectedBandName));
        }

    }

    @Test
    public void testCreateProductWithoutReflectances() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        source = getL1pProduct(source);
        source = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, source);
        final Map<String, Object> params = new HashMap<String, Object>();
        params.put("outputReflec", true);
        String[] expectedBandNames = {"reflec_1", "reflec_2", "reflec_13", "a_ys_443", "tsm", "chl_conc"};
        testTargetProduct(source, expectedBandNames, params);

    }

    @Test
    public void testCreateProduct_WithFsgInput() throws OperatorException, ParseException {
        String origUsePixelGeoCoding = System.getProperty("beam.envisat.usePixelGeoCoding", "false");
        try {
            System.setProperty("beam.envisat.usePixelGeoCoding", "true");
            Product l1bProduct = L1POpTest.getL1bProduct();
            Band corr_longitude = l1bProduct.addBand("corr_longitude", ProductData.TYPE_FLOAT64);
            corr_longitude.setData(corr_longitude.createCompatibleRasterData());
            Band corr_latitude = l1bProduct.addBand("corr_latitude", ProductData.TYPE_FLOAT64);
            corr_latitude.setData(corr_latitude.createCompatibleRasterData());
            l1bProduct.addBand("altitude", ProductData.TYPE_INT16);
            Band l1_flags = l1bProduct.getBand("l1_flags");
            l1_flags.setData(l1_flags.createCompatibleRasterData());

            GeoCoding geoCoding = new PixelGeoCoding(corr_latitude, corr_longitude, "NOT l1_flags.INVALID", 6);
            l1bProduct.setGeoCoding(geoCoding);

            Product l2rProduct = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, l1bProduct);
            Product target = testTargetProduct(l2rProduct, new String[]{"corr_longitude", "corr_latitude", "altitude"},
                                               GPF.NO_PARAMS);

            assertTrue("Expected band 'corr_longitude'", target.containsBand("corr_longitude"));
            assertTrue("Expected band 'corr_latitude'", target.containsBand("corr_latitude"));
            assertTrue("Expected band 'altitude'", target.containsBand("altitude"));
        } finally {
            System.setProperty("beam.envisat.usePixelGeoCoding", origUsePixelGeoCoding);
        }
    }


    private static Product testTargetProduct(Product source, String[] expectedBandNames,
                                             Map<String, Object> l2wParams) {

        Product target = GPF.createProduct("CoastColour.L2W", l2wParams, source);
        assertNotNull(target);

        // enable for debugging
//        L1POpTest.dumpBands(target);

        for (String name : expectedBandNames) {
            assertNotNull("Target band missing: " + name, target.getBand(name));
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

    private Product getL1pProduct(Product source) {
        return GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, source);
    }

}
