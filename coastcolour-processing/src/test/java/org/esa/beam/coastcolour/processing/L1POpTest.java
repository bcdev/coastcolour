package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
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

    public static void dumpBands(Product target) {
        String[] bandNames = target.getBandNames();
        for (int i = 0, bandNamesLength = bandNames.length; i < bandNamesLength; i++) {
            String bandName = bandNames[i];
            System.out.println(target.getName() + ".bands[" + i + "] = " + bandName);
        }
    }


    @Test
    public void testCreateProduct() throws OperatorException, ParseException {

        Product source = getL1bProduct();
        HashMap<String, Product> sources = new HashMap<String, Product>();
        sources.put("source", source);

        Product target = GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, sources);
        assertNotNull(target);

        L1POpTest.dumpBands(target);

        Band[] sourceBands = source.getBands();
        for (Band sourceBand : sourceBands) {
            assertNotNull("Target band missing: " + sourceBand.getName(), target.getBand(sourceBand.getName()));
        }

        // Tests on generated flags dataset
        testFlags(target, "l1p_flags");
    }

    public static void testFlags(Product target, String flagsName) {
        assertNotNull("Target band missing: " + flagsName, target.getBand(flagsName));
        assertNotNull(target.getBand(flagsName).getFlagCoding());
        assertEquals(flagsName, target.getBand(flagsName).getFlagCoding().getName());
        assertSame(target.getFlagCodingGroup().get(flagsName),
                   target.getBand(flagsName).getFlagCoding());
    }

    public static Product getL1bProduct() throws ParseException {
        int width = 10;
        int height = 10;
        Product product = new Product("MER_FR__1P", "MER_FR__1P", width, height);
        for(int i = 0; i < 15; i++) {
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
        return product;
    }

}
