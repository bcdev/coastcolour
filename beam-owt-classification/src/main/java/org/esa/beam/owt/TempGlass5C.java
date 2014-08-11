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
public class TempGlass5C {

    private final static float[] ALL_WAVELENGTHS = new float[]{
            400, 403, 406, 409, 412, 415, 418, 421, 424, 427, 430, 433, 436, 439, 442,
            445, 448, 451, 454, 457, 460, 463, 466, 469, 472, 475, 478, 481, 484, 487,
            490, 493, 496, 499, 502, 505, 508, 511, 514, 517, 520, 523, 526, 529, 532,
            535, 538, 541, 544, 547, 550, 553, 556, 559, 562, 565, 568, 571, 574, 577,
            580, 583, 586, 589, 592, 595, 598, 601, 604, 607, 610, 613, 616, 619, 622,
            625, 628, 631, 634, 637, 640, 643, 646, 649, 652, 655, 658, 661, 664, 667,
            670, 673, 676, 679, 682, 685, 688, 691, 694, 697, 700, 703, 706, 709, 712,
            715, 718, 721, 724, 727, 730, 733, 736, 739, 742, 745, 748, 751, 754, 757,
            760, 763, 766, 769, 772, 775, 778, 781, 784, 787, 790, 793, 796, 799
    };

    private final static String covarianceMatrixResource = "/auxdata/glass/Rrs_Glass_5C_owt_stats_140805.hdf";

    private TempGlass5C() {
    }

    public static double[][][] loadInvCovarianceMatrix() throws Exception {
        float[] wavelength = new float[]{442.6f, 489.9f, 509.8f, 559.7f, 619.6f, 664.6f, 680.8f, 708.3f, 753.4f};

        int[] wlIndices = findWavelengthIndices(wavelength, ALL_WAVELENGTHS, 1.5f);
//DEBUG: wlIndices = 14, 30, 37, 53, 73, 88, 94, 103, 118
        double[][][] invCovarianceMatrix = null;
        try {
            NetcdfFile covMatrixFile = loadFile(covarianceMatrixResource);
            try {
                final Group rootGroup = covMatrixFile.getRootGroup();
                final List<Variable> variableList = rootGroup.getVariables();

                for (Variable variable : variableList) {
                    if ("covariance".equals(variable.getFullName())) {
                        final Array arrayDouble = getDoubleArray(variable);
                        double[][][] matrix = (double[][][]) arrayDouble.copyToNDJavaArray();
                        // important first reduce to the wavelength and invert afterwards
// DEBUG: matrix dimension sizes [5][134][134]
// DEBUG: value at [0][14][14]: 2.1073317984322958E-5
// DEBUG: value at [0][30][30]: 2.7697127023913265E-5
// DEBUG: value at [0][37][37]: 3.1796464652668316E-5
                        double[][][] reducedMatrix = reduceCovarianceMatrixToWLs(matrix, wlIndices);
// DEBUG: reducedMatrix dimension sizes [5][9][9]
// DEBUG: value at [0][0][0]: 2.1073317984322958E-5
// DEBUG: value at [0][1][1]: 2.7697127023913265E-5
// DEBUG: value at [0][2][2]: 3.1796464652668316E-5
                        invCovarianceMatrix = invertMatrix(reducedMatrix);
// DEBUG: reducedMatrix dimension sizes [5][9][9]
// DEBUG: value at [0][0][0]: 5016377.915428448
// DEBUG: value at [0][1][1]: 2.677215253946288E7
// DEBUG: value at [0][2][2]: 567612.2471918743
                    }
                }
            } finally {
                covMatrixFile.close();
            }
        } catch (java.lang.Exception e) {
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
        final URI resourceUri = TempGlass5C.class.getResource(resourcePath).toURI();
        return NetcdfFile.openInMemory(resourceUri);
    }


}
