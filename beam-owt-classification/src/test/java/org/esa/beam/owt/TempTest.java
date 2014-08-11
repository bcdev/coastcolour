package org.esa.beam.owt;

import org.junit.Test;

import static org.junit.Assert.*;

public class TempTest {

    @Test
    public void testGlass() throws Exception {
        double[][][] matrix = TempGlass5C.loadInvCovarianceMatrix();

        double[] timGlassValues = {
                687175.231134958,
                -1335855.38968399,
                713860.185133806,
                317120.902151734,
                -1619201.81830567,
                2415910.20286054,
                -654291.322596574,
                -148146.956167948,
                -114210.464633796
        };

        assertArrayEquals(timGlassValues, matrix[0][0], 1.0e-6);
    }

    @Test
    public void testInland() throws Exception {
        double[][][] matrix = TempInland.loadInvCovarianceMatrix();

        double[] timGlassValues = {
                687175.231134958,
                -1335855.38968399,
                713860.185133806,
                317120.902151734,
                -1619201.81830567,
                2415910.20286054,
                -654291.322596574,
                -148146.956167948,
                -114210.464633796
        };

        assertArrayEquals(timGlassValues, matrix[0][0], 1.0e-6);
    }

}