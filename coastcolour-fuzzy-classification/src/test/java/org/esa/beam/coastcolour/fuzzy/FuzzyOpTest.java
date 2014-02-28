package org.esa.beam.coastcolour.fuzzy;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.ProductData;
import org.junit.Test;

import static junit.framework.Assert.*;

public class FuzzyOpTest {

    @Test
    public void testTheOp() throws Exception {
        new FuzzyOp();

    }

    @Test
    public void testGetBestBandName() throws Exception {
        final Band band1 = new Band("reflec_10", ProductData.TYPE_FLOAT32, 10, 10);
        band1.setSpectralBandIndex(1);
        band1.setSpectralWavelength(195.0f);
        final Band band2 = new Band("reflec_20", ProductData.TYPE_FLOAT32, 10, 10);
        band2.setSpectralBandIndex(2);
        band2.setSpectralWavelength(204.0f);

        final String bestBandName1 = FuzzyOp.getBestBandName("reflec", 198, new Band[]{band1, band2});
        final String bestBandName2 = FuzzyOp.getBestBandName("reflec", 201, new Band[]{band1, band2});

        assertEquals("reflec_10", bestBandName1);
        assertEquals("reflec_20", bestBandName2);
    }
}
