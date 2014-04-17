package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.PixelGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class L2ROpTest {

    private static Product l1bProduct;
    private Product target;

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


    @Before
    public void setUp() throws Exception {
        System.out.println("Starting next test");
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
        HashMap<String, Object> l1pParams = new HashMap<String, Object>();
        l1pParams.put("doEqualization", false);
        target = testDefaultTargetProduct(l1bProduct, l1pParams, "MER_FR__CCL2R");
    }

    @Test
    public void testCreateProductFromL1P() throws OperatorException, ParseException {
        HashMap<String, Object> l1pParams = new HashMap<String, Object>();
        l1pParams.put("doEqualization", false);
        Product source = GPF.createProduct("CoastColour.L1P", l1pParams, l1bProduct);
        target = testDefaultTargetProduct(source, GPF.NO_PARAMS, "MER_FR__CCL2R");
        source.dispose();
    }

    @Test
    public void testCreateProduct_WithMoreOutput() throws OperatorException, ParseException {
        HashMap<String, Object> l1pParams = new HashMap<String, Object>();
        l1pParams.put("doEqualization", false);
        Product source = GPF.createProduct("CoastColour.L1P", l1pParams, l1bProduct);
        Map<String, Object> l2rParams = new HashMap<String, Object>();
        l2rParams.put("outputL2RToa", true);
        target = testDefaultTargetProduct(source, l2rParams, "MER_FR__CCL2R");
        assertProductContainsBands(target, "rho_toa_1", "rho_toa_8", "rho_toa_13");
        source.dispose();
    }

    @Test
    public void testCreateProduct_WithFsgInput() throws OperatorException, ParseException {
        String origUsePixelGeoCoding = System.getProperty("beam.envisat.usePixelGeoCoding", "false");
        try {
            System.setProperty("beam.envisat.usePixelGeoCoding", "true");
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

            HashMap<String, Object> l1pParams = new HashMap<String, Object>();
            l1pParams.put("doEqualization", false);
            Product l1pProduct = GPF.createProduct("CoastColour.L1P", l1pParams, l1bProduct);
            target = testDefaultTargetProduct(l1pProduct, GPF.NO_PARAMS, "MER_FSG_CCL2R");

            assertTrue("Expected band 'corr_longitude'", target.containsBand("corr_longitude"));
            assertTrue("Expected band 'corr_latitude'", target.containsBand("corr_latitude"));
            assertTrue("Expected band 'altitude'", target.containsBand("altitude"));
            l1pProduct.dispose();
        } finally {
            System.setProperty("beam.envisat.usePixelGeoCoding", origUsePixelGeoCoding);
        }
    }

    private static Product testDefaultTargetProduct(Product source, Map<String, Object> processingParams,
                                                    String expectedProductType) {

        Product target = GPF.createProduct("CoastColour.L2R", processingParams, source);
        assertNotNull(target);
        assertEquals(expectedProductType, target.getProductType());

        // enable for debugging
//        L1POpTest.dumpBands(target);

        String[] expectedBandNames = new String[]{
                "reflec_1", "reflec_2", "reflec_13", "norm_reflec_3", "norm_reflec_7", "norm_reflec_12",
        };
        for (String name : expectedBandNames) {
            assertNotNull("Target band missing: " + name, target.getBand(name));
        }

        // assert that valid expressions are changed according flag renaming (agc_flags --> l2r_flags)
        for (String name : expectedBandNames) {
            assertEquals("!l2r_flags.INPUT_INVALID", target.getBand(name).getValidPixelExpression());
        }

        //assert that Masks are changed according flag renaming (agc_flags --> l2r_flags)
        Mask toaOorMask = target.getMaskGroup().get("l2r_cc_toa_oor");
        assertEquals("l2r_flags.TOA_OOR", Mask.BandMathsType.getExpression(toaOorMask));
        Mask solzenMask = target.getMaskGroup().get("l2r_cc_solzen");
        assertEquals("l2r_flags.SOLZEN", Mask.BandMathsType.getExpression(solzenMask));
        Mask sunglintMask = target.getMaskGroup().get("l2r_cc_sunglint");
        assertEquals("l2r_flags.SUNGLINT", Mask.BandMathsType.getExpression(sunglintMask));

        // assert radiance bands are not copied to L2R
        for (int i = 1; i <= 15; i++) {
            String bandName = String.format("radiance_%1$d", i);
            assertNull("Target contains band: " + bandName, target.getBand(bandName));
        }
        assertNotNull(target.getBand("atm_tau_550"));

        // bands not wanted to be included in L2R product
        assertNull(target.getBand("glint_ratio"));
        assertNull(target.getBand("b_tsm"));
        assertNull(target.getBand("a_tot"));

        // Tests on copied L1 flags dataset
        L1POpTest.testFlags(target, "l1_flags");

        // Tests on generated L1P flags dataset
        L1POpTest.testFlags(target, "l1p_flags");

        // Tests on generated L2R flags dataset
        L1POpTest.testFlags(target, "l2r_flags");

        // Test correct order of flag bands
        int l1FlagsBandIndex = target.getBandIndex("l1_flags");
        assertEquals(l1FlagsBandIndex + 1, target.getBandIndex("l1p_flags"));
        assertEquals(l1FlagsBandIndex + 2, target.getBandIndex("l2r_flags"));
        return target;
    }

    private void assertProductContainsBands(Product l2rProduct, String... expectedBandNames) {
        for (String expectedBandName : expectedBandNames) {
            assertTrue("Expected band '" + expectedBandName + "'", l2rProduct.containsBand(expectedBandName));
        }
    }


}
