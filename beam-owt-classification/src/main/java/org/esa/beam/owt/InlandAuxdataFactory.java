package org.esa.beam.owt;

import ucar.ma2.Array;
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
public class InlandAuxdataFactory extends AuxdataFactory {

    private static final float[] ALL_WAVELENGTHS = new float[]{412, 443, 490, 510, 531, 547, 555, 560, 620, 665, 667, 670, 678, 680, 709, 748, 754};
    private static final String COVARIANCE_MATRIX_RESOURCE = "/auxdata/inland/rrs_owt_cov_inland.hdf";
    private static final String SPECTRAL_MEANS_RESOURCE = "/auxdata/inland/rrs_owt_means_inland.hdf";

    private int[] wlIndices;

    public InlandAuxdataFactory(float[] useWavelengths) {
        wlIndices = findWavelengthIndices(useWavelengths);
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


    static int[] findWavelengthIndices(float[] useWavelengths) {
        ArrayList<Integer> wavelengthIdxList = new ArrayList<>();
        for (float useWavelength : useWavelengths) {
            for (int i = 0; i < ALL_WAVELENGTHS.length; i++) {
                float wl = ALL_WAVELENGTHS[i];
                if (useWavelength == wl) {
                    wavelengthIdxList.add(i);
                    break;
                }
            }
        }
        int[] result = new int[wavelengthIdxList.size()];
        for (int i = 0; i < wavelengthIdxList.size(); i++) {
            result[i] = wavelengthIdxList.get(i);
        }
        return result;
    }

    private double[][][] loadInvCovarianceMatrix() throws Exception {
        double[][][] invCovarianceMatrix = null;
        try {
            NetcdfFile covMatrixFile = loadFile(COVARIANCE_MATRIX_RESOURCE);
            try {
                final Group rootGroup = covMatrixFile.getRootGroup();
                final List<Variable> variableList = rootGroup.getVariables();

                for (Variable variable : variableList) {
                    if ("rrs_cov".equals(variable.getFullName())) {
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
            NetcdfFile specMeansFile = loadFile(SPECTRAL_MEANS_RESOURCE);
            try {
                final Group rootGroup = specMeansFile.getRootGroup();
                final List<Variable> variableList = rootGroup.getVariables();

                for (Variable variable : variableList) {
                    if ("class_means".equals(variable.getFullName())) {
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
