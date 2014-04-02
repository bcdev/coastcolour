package org.esa.beam.owt;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.IndexCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class OWTClassificationOpTest {

    private static final float[] MERIS_WAVELENGTHS = new float[]{412, 442, 490, 510, 560, 620, 665, 681, 709, 754, 761, 779, 865, 885, 900};

    @Test
    public void testTheOpWithDefaults() throws Exception {
        Operator fuzzyOp = new OWTClassificationOp();
        fuzzyOp.setParameterDefaultValues();
        fuzzyOp.setSourceProduct(createSourceProduct());
        Product targetProduct = fuzzyOp.getTargetProduct();

        assertEquals(21, targetProduct.getNumBands());

        // some band names
        List<String> bandNames = Arrays.asList(targetProduct.getBandNames());
        assertTrue(bandNames.contains("class_4"));
        assertTrue(bandNames.contains("class_9"));
        assertTrue(bandNames.contains("norm_class_2"));
        assertTrue(bandNames.contains("norm_class_7"));
        assertTrue(bandNames.contains("dominant_class"));
        assertTrue(bandNames.contains("class_sum"));
        assertTrue(bandNames.contains("norm_class_sum"));

        Band dominant_class = targetProduct.getBand("dominant_class");
        assertTrue(dominant_class.isIndexBand());
        IndexCoding indexCoding = dominant_class.getIndexCoding();
        assertEquals("Dominant_Classes", indexCoding.getName());
        assertEquals(9, indexCoding.getIndexNames().length);
    }

    @Test
    public void testTheOpWithInputReflectances() throws Exception {
        Operator fuzzyOp = new OWTClassificationOp();
        fuzzyOp.setParameterDefaultValues();
        fuzzyOp.setSourceProduct(createSourceProduct());
        fuzzyOp.setParameter("writeInputReflectances", true);
        Product targetProduct = fuzzyOp.getTargetProduct();

        assertEquals(26, targetProduct.getNumBands());

        // test some band names
        List<String> bandNames = Arrays.asList(targetProduct.getBandNames());
        assertTrue(bandNames.contains("class_4"));
        assertTrue(bandNames.contains("class_9"));
        assertTrue(bandNames.contains("norm_class_2"));
        assertTrue(bandNames.contains("norm_class_7"));
        assertTrue(bandNames.contains("dominant_class"));
        assertTrue(bandNames.contains("class_sum"));
        assertTrue(bandNames.contains("norm_class_sum"));
        assertTrue(bandNames.contains("reflec_1"));
        assertTrue(bandNames.contains("reflec_3"));
        assertTrue(bandNames.contains("reflec_5"));

        Band dominant_class = targetProduct.getBand("dominant_class");
        assertTrue(dominant_class.isIndexBand());
        IndexCoding indexCoding = dominant_class.getIndexCoding();
        assertEquals("Dominant_Classes", indexCoding.getName());
        assertEquals(9, indexCoding.getIndexNames().length);
    }

    @Test
    public void testTheOpWithInlandAuxdata() throws Exception {
        Operator fuzzyOp = new OWTClassificationOp();
        fuzzyOp.setParameterDefaultValues();
        fuzzyOp.setSourceProduct(createSourceProduct());
        fuzzyOp.setParameter("owtType", "INLAND");
        Product targetProduct = fuzzyOp.getTargetProduct();

        assertEquals(17, targetProduct.getNumBands());

        // test some band names
        List<String> bandNames = Arrays.asList(targetProduct.getBandNames());
        assertTrue(bandNames.contains("class_4"));
        assertTrue(bandNames.contains("class_6"));
        assertTrue(bandNames.contains("norm_class_2"));
        assertTrue(bandNames.contains("norm_class_7"));
        assertTrue(bandNames.contains("dominant_class"));
        assertTrue(bandNames.contains("class_sum"));
        assertTrue(bandNames.contains("norm_class_sum"));

        Band dominant_class = targetProduct.getBand("dominant_class");
        assertTrue(dominant_class.isIndexBand());
        IndexCoding indexCoding = dominant_class.getIndexCoding();
        assertEquals("Dominant_Classes", indexCoding.getName());
        assertEquals(7, indexCoding.getIndexNames().length);
    }

    @Test
    public void testGetBestBandName() throws Exception {
        final Band band1 = new Band("reflec_10", ProductData.TYPE_FLOAT32, 10, 10);
        band1.setSpectralBandIndex(1);
        band1.setSpectralWavelength(195.0f);
        final Band band2 = new Band("reflec_20", ProductData.TYPE_FLOAT32, 10, 10);
        band2.setSpectralBandIndex(2);
        band2.setSpectralWavelength(204.0f);

        final String bestBandName1 = OWTClassificationOp.getBestBandName("reflec", 198, new Band[]{band1, band2});
        final String bestBandName2 = OWTClassificationOp.getBestBandName("reflec", 201, new Band[]{band1, band2});

        assertEquals("reflec_10", bestBandName1);
        assertEquals("reflec_20", bestBandName2);
    }

    private Product createSourceProduct() {
        Product product = new Product("OWT_Input", "REFLEC", 10, 10);
        for (int i = 0; i < MERIS_WAVELENGTHS.length; i++) {
            Band reflecBand = product.addBand("reflec_" + (i + 1), ProductData.TYPE_FLOAT32);
            reflecBand.setSpectralWavelength(MERIS_WAVELENGTHS[i]);
            reflecBand.setSpectralBandIndex(i);
            reflecBand.setSpectralBandwidth(10);
        }
        return product;
    }

}
