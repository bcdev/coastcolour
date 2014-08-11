package org.esa.beam.owt;

import Jama.Matrix;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Marco Peters
 */
public class TempInland {

    private final static float[] ALL_WAVELENGTHS = new float[]{412, 443, 490, 510, 531, 547, 555, 560, 620, 665, 667, 670, 678, 680, 709, 748, 754};
    private final static String covarianceMatrixResource = "/auxdata/inland/rrs_owt_cov_inland.hdf";

    private TempInland() {
    }

    public static double[][][] loadInvCovarianceMatrix() throws Exception {
        float[] wavelength = new float[]{443, 490, 510, 560, 620, 665, 680, 709, 754};

        int[] wlIndices = findWavelengthIndices(wavelength, ALL_WAVELENGTHS, 1.5f);
//DEBUG: wlIndices = 1, 2, 3, 7, 8, 9, 13, 14, 16
        double[][][] invCovarianceMatrix = null;
        try {
            NetcdfFile covMatrixFile = loadFile(covarianceMatrixResource);
            try {
                final Group rootGroup = covMatrixFile.getRootGroup();
                final List<Variable> variableList = rootGroup.getVariables();

                for (Variable variable : variableList) {
                    if ("rrs_cov".equals(variable.getFullName())) {
                        final Array arrayDouble = getDoubleArray(variable);
                        double[][][] matrix = (double[][][]) arrayDouble.copyToNDJavaArray();
                        // important first reduce to the wavelength and invert afterwards
// DEBUG: matrix dimension sizes [7][17][17]
// DEBUG: value at [0][1][1]: 1.570290399678475E-5
// DEBUG: value at [0][2][2]: 1.6523544449954915E-5
// DEBUG: value at [0][3][3]: 1.591149735819017E-5
                        double[][][] reducedMatrix = reduceCovarianceMatrixToWLs(matrix, wlIndices);
// DEBUG: reducedMatrix dimension sizes [7][9][9]
// DEBUG: value at [0][0][0]: 1.570290399678475E-5
// DEBUG: value at [0][1][1]: 1.6523544449954915E-5
// DEBUG: value at [0][2][2]: 1.591149735819017E-5
                        invCovarianceMatrix = invertMatrix(reducedMatrix);
// DEBUG: reducedMatrix dimension sizes [7][9][9]
// DEBUG: value at [0][0][0]: 687175.2311349595
// DEBUG: value at [0][1][1]: 3111493.048019427
// DEBUG: value at [0][2][2]: 1672867.7517036211
                    }
                }
            } finally {
                covMatrixFile.close();
            }
        } catch (Exception e) {
            throw new Exception("Could not load auxiliary data", e);
        }
        return invCovarianceMatrix;
    }

    private static int[] findWavelengthIndices(float[] useWavelengths, float[] allWavelengths, float maxDistance) {

        ArrayList<Integer> wavelengthIdxList = new ArrayList<>();
        for (float useWavelength : useWavelengths) {
            int bestIndex = -1;
            double lastDelta = Double.MAX_VALUE;
            for (int i = 0; i < allWavelengths.length; i++) {
                float delta = Math.abs(useWavelength - allWavelengths[i]);
                if (delta <= maxDistance && delta <= lastDelta) {
                    bestIndex = i;
                } else if (delta > lastDelta) {
                    // assuming that ALL_WAVELENGTHS is sorted we can break the loop if delta increases
                    break;
                }
                lastDelta = delta;
            }
            if (bestIndex != -1) {
                wavelengthIdxList.add(bestIndex);
            } else {
                String msg = String.format("Could not find appropriate wavelength (%.3f) in auxiliary data", useWavelength);
                throw new IllegalStateException(msg);
            }
        }

        int[] result = new int[wavelengthIdxList.size()];
        for (int i = 0; i < wavelengthIdxList.size(); i++) {
            result[i] = wavelengthIdxList.get(i);
        }
        return result;
    }


    private static double[][][] reduceCovarianceMatrixToWLs(double[][][] covarianceMatrix, int[] useIndices) {
        double[][][] reducedMatrix = new double[covarianceMatrix.length][useIndices.length][useIndices.length];
        for (int i = 0; i < covarianceMatrix.length; i++) {
            double[][] innerCovarianceMatrix = covarianceMatrix[i];
            for (int j = 0; j < useIndices.length; j++) {
                double[] innerArray = innerCovarianceMatrix[useIndices[j]];
                for (int k = 0; k < useIndices.length; k++) {
                    reducedMatrix[i][j][k] = innerArray[useIndices[k]];
                }
            }

        }
        return reducedMatrix;
    }

    private static double[][][] invertMatrix(double[][][] matrix) {
        double[][][] invMatrix = new double[matrix.length][][];
        for (int i = 0; i < matrix.length; i++) {
            final Matrix tempMatrix = new Matrix(matrix[i]);
            final Matrix tempInvMatrix = tempMatrix.inverse();
            invMatrix[i] = tempInvMatrix.getArray();

        }
        return invMatrix;
    }

    private static Array getDoubleArray(Variable variable) throws IOException, InvalidRangeException {
        final int[] origin = new int[variable.getRank()];
        final int[] shape = variable.getShape();
        final Array array = variable.read(new Section(origin, shape));
        return Array.factory(DataType.DOUBLE, shape, array.get1DJavaArray(Double.class));
    }

    private static NetcdfFile loadFile(String resourcePath) throws URISyntaxException, IOException {
        final URI resourceUri = TempInland.class.getResource(resourcePath).toURI();
        return NetcdfFile.openInMemory(resourceUri);
    }


}
