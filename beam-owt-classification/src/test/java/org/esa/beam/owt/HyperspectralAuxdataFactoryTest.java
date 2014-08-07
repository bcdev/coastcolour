package org.esa.beam.owt;

import org.junit.Test;

import static org.junit.Assert.*;

public class HyperspectralAuxdataFactoryTest {

    @Test
    public void testFindBestMatchingWavelength_exactValues() {
        int[] actualIndices = HyperspectralAuxdataFactory.findWavelengthIndices(new float[]{400, 427, 490, 556, 667, 799});
        int[] expectedIndices = new int[]{0, 9, 30, 52, 89, 133};

        assertArrayEquals(expectedIndices, actualIndices);
    }

    @Test
    public void testFindBestMatchingWavelength_inbetweenValues() {
        int[] actualIndices = HyperspectralAuxdataFactory.findWavelengthIndices(new float[]{400.3f, 428.1f, 488.6f, 557.49f, 667, 799});
        int[] expectedIndices = new int[]{0, 9, 30, 52, 89, 133};

        assertArrayEquals(expectedIndices, actualIndices);
    }

    @Test(expected = IllegalStateException.class)
    public void testFindBestMatchingWavelength_BelowMinimum() {
        HyperspectralAuxdataFactory.findWavelengthIndices(new float[]{360});
    }

    @Test(expected = IllegalStateException.class)
    public void testFindBestMatchingWavelength_AboveMaximum() {
        HyperspectralAuxdataFactory.findWavelengthIndices(new float[]{850});
    }

}