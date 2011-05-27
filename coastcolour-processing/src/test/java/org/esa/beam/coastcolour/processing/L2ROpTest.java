package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.PixelGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.ParseException;

import static org.junit.Assert.*;

public class L2ROpTest {

    @BeforeClass
    public static void start() {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }


    @Test
    public void testCreateProductFromL1B() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        testDefaultTargetProduct(source, "MER_FR__CCL2R");
    }

    @Test
    public void testCreateProductFromL1P() throws OperatorException, ParseException {
        Product source = L1POpTest.getL1bProduct();
        source = GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, source);
        testDefaultTargetProduct(source, "MER_FR__CCL2R");
    }

    @Test
    public void testCreateProduct_WithFsgInput() throws OperatorException, ParseException {
        String origUsePixelGeoCoding = System.getProperty("beam.envisat.usePixelGeoCoding", "false");
        try {
            System.setProperty("beam.envisat.usePixelGeoCoding", "true");
            Product l1bProduct = L1POpTest.getL1bProduct();
            l1bProduct.setProductType("MER_FSG_1P");
            Band corr_longitude = l1bProduct.addBand("corr_longitude", ProductData.TYPE_FLOAT64);
            corr_longitude.setData(corr_longitude.createCompatibleRasterData());
            Band corr_latitude = l1bProduct.addBand("corr_latitude", ProductData.TYPE_FLOAT64);
            corr_latitude.setData(corr_latitude.createCompatibleRasterData());
            l1bProduct.addBand("altitude", ProductData.TYPE_INT16);
            Band l1_flags = l1bProduct.getBand("l1_flags");
            l1_flags.setData(l1_flags.createCompatibleRasterData());

            GeoCoding geoCoding = new PixelGeoCoding(corr_latitude, corr_longitude, "NOT l1_flags.INVALID", 6);
            l1bProduct.setGeoCoding(geoCoding);

            Product l1pProduct = GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, l1bProduct);
            Product target = testDefaultTargetProduct(l1pProduct, "MER_FSG_CCL2R");

            assertTrue("Expected band 'corr_longitude'", target.containsBand("corr_longitude"));
            assertTrue("Expected band 'corr_latitude'", target.containsBand("corr_latitude"));
            assertTrue("Expected band 'altitude'", target.containsBand("altitude"));
        } finally {
            System.setProperty("beam.envisat.usePixelGeoCoding", origUsePixelGeoCoding);
        }
    }

    private static Product testDefaultTargetProduct(Product source, String expectedProductType) {

        Product target = GPF.createProduct("CoastColour.L2R", GPF.NO_PARAMS, source);
        assertNotNull(target);
        assertEquals(expectedProductType, target.getProductType());

        // enable for debugging
//        L1POpTest.dumpBands(target);

        String[] expectedBandNames = new String[]{
                "reflec_1", "reflec_2", "reflec_13", "norm_refl_3", "norm_refl_7", "norm_refl_12",
        };
        for (String name : expectedBandNames) {
            assertNotNull("Target band missing: " + name, target.getBand(name));
        }

        // assert that valid expressions are changed according flag renaming (agc_flags --> l2r_flags)
        for (String name : expectedBandNames) {
            assertEquals("!l2r_flags.INVALID", target.getBand(name).getValidPixelExpression());
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
        assertNotNull(target.getBand("atm_tau_778"));
        assertNotNull(target.getBand("atm_tau_865"));

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

}
