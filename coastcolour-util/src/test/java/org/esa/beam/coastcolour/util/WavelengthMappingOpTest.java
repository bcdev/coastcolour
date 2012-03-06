package org.esa.beam.coastcolour.util;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;


public class WavelengthMappingOpTest {

    @Before
    public void setUp() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new WavelengthMappingOp.Spi());
    }

    @Test
    public void testWavelengthMapping() throws Exception {
        Product product = new Product("testProduct", "testType", 2, 3);
        product.addBand("radiance_1", ProductData.TYPE_FLOAT32);
        product.addBand("radiance_2", ProductData.TYPE_FLOAT32);
        Map<String, Object> parameters = new HashMap<String, Object>();
        final String testFile = getClass().getResource("mapping_test.properties").getFile();
        parameters.put("wavelengthMappingFile", new File(testFile));
        final Product targetProduct = GPF.createProduct("CoastColour.WavelengthMapping", parameters, product);
        assertSame(targetProduct, product);
        assertEquals(100.0f, targetProduct.getBand("radiance_1").getSpectralWavelength(), 1.E-3);
        assertEquals(200.0f, targetProduct.getBand("radiance_2").getSpectralWavelength(), 1.E-3);
        assertTrue(targetProduct.getBand("radiance_1").getSpectralBandIndex() >= 0);
        assertTrue(targetProduct.getBand("radiance_2").getSpectralBandIndex() >= 0);
    }
}
