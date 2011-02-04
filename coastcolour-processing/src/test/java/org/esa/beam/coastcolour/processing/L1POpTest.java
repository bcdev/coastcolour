package org.esa.beam.coastcolour.processing;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.GPF;
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
    public void testInitialize() throws Exception {

        Product source = getL1bProduct();
        HashMap<String, Product> sources = new HashMap<String, Product>();
        sources.put("source", source);

        Product target = GPF.createProduct("CoastColour.L1P", GPF.NO_PARAMS, sources);
        assertNotNull(target);
    }

    private Product getL1bProduct() throws ParseException {
        int width = 10;
        int height = 10;
        Product product = new Product("MER_FR__1P", "MER_FR__1P", width, height);
        product.addBand("radiance_1", ProductData.TYPE_UINT16);
        product.addBand("radiance_2", ProductData.TYPE_UINT16);
        product.addBand("radiance_3", ProductData.TYPE_UINT16);
        product.addBand("radiance_4", ProductData.TYPE_UINT16);
        product.addBand("radiance_5", ProductData.TYPE_UINT16);
        product.addBand("radiance_6", ProductData.TYPE_UINT16);
        product.addBand("radiance_7", ProductData.TYPE_UINT16);
        product.addBand("radiance_8", ProductData.TYPE_UINT16);
        product.addBand("radiance_9", ProductData.TYPE_UINT16);
        product.addBand("radiance_10", ProductData.TYPE_UINT16);
        product.addBand("radiance_11", ProductData.TYPE_UINT16);
        product.addBand("radiance_12", ProductData.TYPE_UINT16);
        product.addBand("radiance_13", ProductData.TYPE_UINT16);
        product.addBand("radiance_14", ProductData.TYPE_UINT16);
        product.addBand("radiance_15", ProductData.TYPE_UINT16);
        product.addBand("l1_flags", ProductData.TYPE_UINT8);
        product.addBand("detector_index", ProductData.TYPE_UINT16);
        float[] sunZenithData = new float[width*height];
        TiePointGrid sunZenithTpg = new TiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME,
                                                     width, height, 0, 0, 1, 1, sunZenithData);
        product.addTiePointGrid(sunZenithTpg);

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
