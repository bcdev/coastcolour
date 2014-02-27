package org.esa.beam.coastcolour.fuzzy;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Marco Peters
 */
public class InlandAuxdataFactoryTest {

    @Test
    public void testReduceSpectralMeans() throws Exception {
        double[][] spectralMeans = new double[][]{
                {0, 0}, {0, 1}, {0, 2}, {0, 3},
                {0, 4}, {0, 5}, {0, 6}, {0, 7},
                {0, 8}, {0, 9}, {0, 10}, {0, 11},
                {0, 12}, {0, 13}, {0, 14}, {0, 15}, {0, 16},
        };
        double[][] actualArray = InlandAuxdataFactory.reduceSpectralMeansToWLs(spectralMeans, new int[]{0, 1, 2, 3, 7, 8, 9, 13, 14, 16});
        double[][] expectedArray = new double[][]{
                {0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 7},
                {0, 8}, {0, 9}, {0, 13}, {0, 14}, {0, 16},
        };
        assertArrayEquals(expectedArray, actualArray);
    }

    @Test
    public void testReduceCovarianceMatrices() throws Exception {
        double[][] inputData = new double[][]{
                {1, 2, 3, 4, 5, 6, 7, 8},
                {2, 3, 4, 5, 6, 7, 8, 9},
                {3, 4, 5, 6, 7, 8, 9, 10},
                {4, 5, 6, 7, 8, 9, 10, 11},
                {5, 6, 7, 8, 9, 10, 11, 12},
                {6, 7, 8, 9, 10, 11, 12, 13},
                {7, 8, 9, 10, 11, 12, 13, 14},
                {8, 9, 10, 11, 12, 13, 14, 15},
        };
        double[][][] matrices = new double[3][][];
        matrices[0] = inputData;
        matrices[1] = inputData;
        matrices[2] = inputData;
        double[][][] actualArray = InlandAuxdataFactory.reduceCovarianceMatrixToWLs(matrices, new int[]{0, 1, 3, 6});
        double[][] expectedData = new double[][]{
                {1, 2, 4, 7},
                {2, 3, 5, 8},
                {4, 5, 7, 10},
                {7, 8, 10, 13},
        };

        double[][][] expectedArray = new double[3][][];
        expectedArray[0] = expectedData;
        expectedArray[1] = expectedData;
        expectedArray[2] = expectedData;

        assertArrayEquals(expectedArray, actualArray);
    }

    @Test
    public void testFindWlIndices() throws Exception {
        int[] actualIndices = InlandAuxdataFactory.findWavelengthIndices(new float[]{412, 490, 555, 667});
        int[] expectedIndices = new int[]{0, 2, 6, 10};

        assertArrayEquals(expectedIndices, actualIndices);
    }
}
