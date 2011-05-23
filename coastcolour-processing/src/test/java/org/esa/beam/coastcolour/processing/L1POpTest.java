package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.PixelGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.text.ParseException;
import java.util.HashMap;

import static org.junit.Assert.*;

public class L1POpTest {

    @BeforeClass
    public static void start() {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    @Test
    public void testCreateProduct() throws OperatorException, ParseException {

        Product source = getL1bProduct();

        Product target = GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, source);
        assertNotNull(target);

        // enable for debugging
//        L1POpTest.dumpBands(target);

        Band[] sourceBands = source.getBands();
        for (Band sourceBand : sourceBands) {
            assertNotNull("Target band missing: " + sourceBand.getName(), target.getBand(sourceBand.getName()));
        }

        final ProductNodeGroup<FlagCoding> flagCodingGroup = target.getFlagCodingGroup();
        assertEquals(2, flagCodingGroup.getNodeCount());

        // Tests on generated flags dataset
        testFlags(target, "l1p_flags");

        // test order of bands
        int index = target.getBandIndex("detector_index");
        assertEquals("l1_flags", target.getBandAt(index + 1).getName());
        assertEquals("l1p_flags", target.getBandAt(index + 2).getName());

        final ProductNodeGroup<Mask> maskGroup = target.getMaskGroup();

        final String msg = "Expected Mask does not exist";
        assertTrue(msg, maskGroup.contains(("F_MEGLINT")));
        assertTrue(msg, maskGroup.contains(("F_LANDCONS")));
        assertTrue(msg, maskGroup.contains(("F_CLOUD")));
        assertTrue(msg, maskGroup.contains(("F_BRIGHT")));
        assertTrue(msg, maskGroup.contains(("F_LOW_P_P1")));
        assertTrue(msg, maskGroup.contains(("F_SNOW_ICE")));

    }

    @Test
    public void testCreateProduct_WithFsgInput() throws OperatorException, ParseException {
        Product l1bProduct = getL1bProduct();
        l1bProduct.setProductType("MER_FSG_1P");
        Band corr_longitude = l1bProduct.addBand("corr_longitude", ProductData.TYPE_FLOAT64);
        Band corr_latitude = l1bProduct.addBand("corr_latitude", ProductData.TYPE_FLOAT64);
        l1bProduct.addBand("altitude", ProductData.TYPE_INT16);

        GeoCoding geoCoding = new PixelGeoCoding(corr_latitude, corr_longitude, "NOT l1_flags.INVALID", 6);
        l1bProduct.setGeoCoding(geoCoding);

        Product target = GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, l1bProduct);
        assertEquals("MER_FSG_CCL1P", target.getProductType());
        assertTrue(target.containsBand("corr_longitude"));
        assertTrue(target.containsBand("corr_latitude"));
        assertTrue(target.containsBand("altitude"));
    }

    @Test
    public void testCreateProductWithoutIdepix() throws OperatorException, ParseException {

        Product source = getL1bProduct();
        HashMap<String, Object> l1pParams = new HashMap<String, Object>();
        l1pParams.put("useIdepix", false);
        Product target = GPF.createProduct("CoastColour.L1P", l1pParams, source);
        assertNotNull(target);

        Band[] sourceBands = source.getBands();
        for (Band sourceBand : sourceBands) {
            assertNotNull("Target band missing: " + sourceBand.getName(), target.getBand(sourceBand.getName()));
        }

        // Tests l1p_flags does not exist
        assertFalse("l1p_flags is not expected in target product. Idepix is disabled.",
                    target.containsBand("l1p_flags"));
    }

    public static void testFlags(Product target, String flagsName) {
        assertNotNull("Target band missing: " + flagsName, target.getBand(flagsName));
        assertNotNull(target.getBand(flagsName).getFlagCoding());
        assertEquals(flagsName, target.getBand(flagsName).getFlagCoding().getName());
        final ProductNodeGroup<FlagCoding> flagCodingGroup = target.getFlagCodingGroup();
        assertSame(flagCodingGroup.get(flagsName), target.getBand(flagsName).getFlagCoding());
    }

    public static Product getL1bProduct() throws ParseException {
        int width = 10;
        int height = 10;
        Product product = new Product("MER_FR__1P", "MER_FR__1P", width, height);
        for (int i = 0; i < 15; i++) {
            product.addBand(String.format("radiance_%d", (i + 1)), ProductData.TYPE_UINT16).setSpectralBandIndex(i);

        }
        product.addBand("l1_flags", ProductData.TYPE_UINT8);
        product.addBand("detector_index", ProductData.TYPE_UINT16);
        float[] tiePointData = new float[width * height];
        product.addTiePointGrid(new TiePointGrid("sun_zenith", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("sun_azimuth", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("view_zenith", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("view_azimuth", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("dem_alt", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("atm_press", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("ozone", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("latitude", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("longitude", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("dem_rough", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("lat_corr", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("lon_corr", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("zonal_wind", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("merid_wind", width, height, 0, 0, 1, 1, tiePointData));
        product.addTiePointGrid(new TiePointGrid("rel_hum", width, height, 0, 0, 1, 1, tiePointData));

        FlagCoding l1_flags = new FlagCoding("l1_flags");
        l1_flags.addFlag("INVALID", 0x01, "No Description.");
        l1_flags.addFlag("LAND_OCEAN", 0x02, "No Description.");
        product.getBand("l1_flags").setSampleCoding(l1_flags);
        product.getFlagCodingGroup().add(l1_flags);
        product.setStartTime(ProductData.UTC.parse("12-Mar-2003 13:45:36"));
        product.setEndTime(ProductData.UTC.parse("12-Mar-2003 13:48:12"));
        final MetadataElement sph = new MetadataElement("SPH");
        final MetadataAttribute sphDescriptor = new MetadataAttribute("SPH_DESCRIPTOR",
                                                                      ProductData.createInstance(
                                                                              "MER_FR__1P SPECIFIC HEADER"), true);
        sph.addAttribute(sphDescriptor);
        product.getMetadataRoot().addElement(sph);

        return product;
    }

    // used for debugging
    public static void dumpBands(Product target) {
        String[] bandNames = target.getBandNames();
        for (int i = 0, bandNamesLength = bandNames.length; i < bandNamesLength; i++) {
            String bandName = bandNames[i];
            System.out.println(target.getName() + ".bands[" + i + "] = " + bandName);
        }
    }

}
