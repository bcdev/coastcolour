package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.PixelGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class L2WOpSecondTest {

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
    public void testCreateProduct_WithFsgInput() throws OperatorException, ParseException {
        String origUsePixelGeoCoding = System.getProperty("beam.envisat.usePixelGeoCoding", "true");
        String origPixelGeoCodingTiling = System.getProperty("beam.pixelGeoCoding.useTiling", "true");
        String origPixelGeoCodingAccuracy = System.getProperty("beam.pixelGeoCoding.fractionAccuracy", "true");
        try {
            System.setProperty("beam.envisat.usePixelGeoCoding", "true");
            Product l1bProduct = createFsgL1bProduct();

            Product l2rProduct = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, l1bProduct);
            target = testTargetProduct(l2rProduct, "MER_FSG_CCL2W",
                                       new String[]{"corr_longitude", "corr_latitude", "altitude", "turbidity"},
                                       GPF.NO_PARAMS);

            assertTrue("Expected band 'corr_longitude'", target.containsBand("corr_longitude"));
            assertTrue("Expected band 'corr_latitude'", target.containsBand("corr_latitude"));
            assertTrue("Expected band 'altitude'", target.containsBand("altitude"));
            l2rProduct.dispose();
            l1bProduct.dispose();
        } finally {
            System.setProperty("beam.envisat.usePixelGeoCoding", origUsePixelGeoCoding);
            System.setProperty("beam.pixelGeoCoding.useTiling", origPixelGeoCodingTiling);
            System.setProperty("beam.pixelGeoCoding.fractionAccuracy", origPixelGeoCodingAccuracy);
        }
    }

    @Test
    public void testCreateProduct_WithQAA_And_FsgInput() throws OperatorException, ParseException {
        String origUsePixelGeoCoding = System.getProperty("beam.envisat.usePixelGeoCoding", "true");
        String origPixelGeoCodingTiling = System.getProperty("beam.pixelGeoCoding.useTiling", "true");
        String origPixelGeoCodingAccuracy = System.getProperty("beam.pixelGeoCoding.fractionAccuracy", "true");
        try {
            System.setProperty("beam.envisat.usePixelGeoCoding", "true");
            Product l1bProduct = createFsgL1bProduct();

            Product l2rProduct = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, l1bProduct);
            HashMap<String, Object> l2wParams = new HashMap<String, Object>();
            l2wParams.put("useQaaForIops", true);
            target = testTargetProduct(l2rProduct, "MER_FSG_CCL2W",
                                       new String[]{"corr_longitude", "corr_latitude", "altitude", "turbidity"},
                                       l2wParams);

            assertTrue("Expected band 'corr_longitude'", target.containsBand("corr_longitude"));
            assertTrue("Expected band 'corr_latitude'", target.containsBand("corr_latitude"));
            assertTrue("Expected band 'altitude'", target.containsBand("altitude"));
            l2rProduct.dispose();
            l1bProduct.dispose();
        } finally {
            System.setProperty("beam.envisat.usePixelGeoCoding", origUsePixelGeoCoding);
            System.setProperty("beam.pixelGeoCoding.useTiling", origPixelGeoCodingTiling);
            System.setProperty("beam.pixelGeoCoding.fractionAccuracy", origPixelGeoCodingAccuracy);
        }
    }

    private Product createFsgL1bProduct() throws ParseException {
        Product l1bProduct = L1POpTest.createL1bProduct();
        l1bProduct.setProductType("MER_FSG_1P");
        Band corr_longitude = l1bProduct.addBand("corr_longitude", ProductData.TYPE_FLOAT64);
        corr_longitude.setData(corr_longitude.createCompatibleRasterData());
        Band corr_latitude = l1bProduct.addBand("corr_latitude", ProductData.TYPE_FLOAT64);
        corr_latitude.setData(corr_latitude.createCompatibleRasterData());
        final Band altitude = l1bProduct.addBand("altitude", ProductData.TYPE_INT16);
        altitude.setData(altitude.createCompatibleRasterData());

        GeoCoding geoCoding = new PixelGeoCoding(corr_latitude, corr_longitude, "NOT l1_flags.INVALID", 6);
        l1bProduct.setGeoCoding(geoCoding);
        return l1bProduct;
    }


    @Test
    public void testCreateProduct_WithFLHOutput() throws ParseException {
        Product source = getL1pProduct(l1bProduct);
        final String[] expectedBandNames = {"iop_a_ys_443", "conc_tsm", "conc_chl", "exp_FLH_681", "turbidity"};
        Map<String, Object> l2wParams = new HashMap<String, Object>();
        l2wParams.put("outputFLH", true);
        target = testTargetProduct(source, "MER_FR__CCL2W", expectedBandNames, l2wParams);
        source.dispose();
    }

    @Test(expected = OperatorException.class)
    public void testCreateProduct_WithFLHOutput_FromL2RLeadsToException() throws ParseException {
        Product l1pSource = getL1pProduct(l1bProduct);
        Product l2rSource = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, l1pSource);

        Map<String, Object> l2wParams = new HashMap<String, Object>();
        l2wParams.put("outputFLH", true);
        GPF.createProduct("CoastColour.L2W", l2wParams, l2rSource);
        l1pSource.dispose();
        l2rSource.dispose();
    }

    private static Product testTargetProduct(Product source, String expectedProductType, String[] expectedBandNames,
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

    private Product getL1pProduct(Product source) {
        return GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, source);
    }

}
