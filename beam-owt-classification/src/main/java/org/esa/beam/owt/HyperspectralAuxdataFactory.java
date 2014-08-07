package org.esa.beam.owt;

import ucar.ma2.Array;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author Marco Peters
 */
public class HyperspectralAuxdataFactory extends AuxdataFactory {

    private static final float[] ALL_WAVELENGTHS = new float[]{
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
    private static final int MAX_DISTANCE = 10;
    private String covarianceMatrixResource;
    private String covarianceVarName;
    private String spectralMeansResource;
    private String spectralMeansVarName;
    private int[] wlIndices;

    public HyperspectralAuxdataFactory(float[] useWavelengths, String covarianceMatrixResource, String covarianceVarName,
                                       String spectralMeansResource, String spectralMeansVarName) {
        this.spectralMeansVarName = spectralMeansVarName;
        this.wlIndices = findWavelengthIndices(useWavelengths, ALL_WAVELENGTHS, MAX_DISTANCE);
        this.covarianceMatrixResource = covarianceMatrixResource;
        this.covarianceVarName = covarianceVarName;
        this.spectralMeansResource = spectralMeansResource;
    }

    @Override
    public Auxdata createAuxdata() throws Exception {
        double[][] spectralMeans = loadSpectralMeans();
        double[][][] invCovarianceMatrix = loadInvCovarianceMatrix();
        if (spectralMeans == null || invCovarianceMatrix == null) {
            throw new Exception("Could not load auxiliary data");
        }
        return new Auxdata(spectralMeans, invCovarianceMatrix);
    }


    private double[][][] loadInvCovarianceMatrix() throws Exception {
        double[][][] invCovarianceMatrix = null;
        try {
            NetcdfFile covMatrixFile = loadFile(covarianceMatrixResource);
            try {
                final Group rootGroup = covMatrixFile.getRootGroup();
                final List<Variable> variableList = rootGroup.getVariables();

                for (Variable variable : variableList) {
                    if (covarianceVarName.equals(variable.getFullName())) {
                        final Array arrayDouble = getDoubleArray(variable);
                        double[][][] matrix = (double[][][]) arrayDouble.copyToNDJavaArray();
                        // important first reduce to the wavelength and invert afterwards
                        double[][][] redMatrix = reduceCovarianceMatrixToWLs(matrix, wlIndices);
                        invCovarianceMatrix = invertMatrix(redMatrix);
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

    static double[][][] reduceCovarianceMatrixToWLs(double[][][] covarianceMatrix, int[] useIndices) {
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

    private double[][] loadSpectralMeans() throws Exception {
        double[][] spectralMeans = null;
        try {
            NetcdfFile specMeansFile = loadFile(spectralMeansResource);
            try {
                final Group rootGroup = specMeansFile.getRootGroup();
                final List<Variable> variableList = rootGroup.getVariables();

                for (Variable variable : variableList) {
                    if (spectralMeansVarName.equals(variable.getFullName())) {
                        final Array arrayDouble = getDoubleArray(variable);
                        double[][] allSpectralMeans = (double[][]) arrayDouble.copyToNDJavaArray();
                        spectralMeans = reduceSpectralMeansToWLs(allSpectralMeans, wlIndices);
                    }
                }
            } finally {
                specMeansFile.close();
            }
        } catch (java.lang.Exception e) {
            throw new Exception("Could not load auxiliary data", e);
        }
        return spectralMeans;
    }

    static int[] findWavelengthIndices(float[] useWavelengths) {
        return AuxdataFactory.findWavelengthIndices(useWavelengths, ALL_WAVELENGTHS, 1.5f);
    }

    static double[][] reduceSpectralMeansToWLs(double[][] spectralMeans, int[] useIndices) {
        double[][] reducedSpectralMeans = new double[useIndices.length][];
        for (int i = 0; i < useIndices.length; i++) {
            reducedSpectralMeans[i] = spectralMeans[useIndices[i]];
        }
        return reducedSpectralMeans;
    }

    private NetcdfFile loadFile(String resourcePath) throws URISyntaxException, IOException {
        final URI resourceUri = getClass().getResource(resourcePath).toURI();
        return NetcdfFile.openInMemory(resourceUri);
    }
}
