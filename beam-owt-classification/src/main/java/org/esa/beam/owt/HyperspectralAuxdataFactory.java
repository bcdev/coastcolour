package org.esa.beam.owt;

import ucar.ma2.Array;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.util.List;

/**
 * @author Marco Peters
 */
public class HyperspectralAuxdataFactory extends AuxdataFactory {
    // todo (mp) - configuration should be turned into a configuration object

    private String covarianceMatrixResource;
    private String covarianceVarName;
    private String spectralMeansResource;
    private String spectralMeansVarName;
    private int[] wlIndices;

    public HyperspectralAuxdataFactory(float[] useWavelengths, float[] allWavelengths, float maxDistance, String covarianceMatrixResource,
                                       String covarianceVarName,
                                       String spectralMeansResource, String spectralMeansVarName) {
        this.spectralMeansVarName = spectralMeansVarName;
        this.wlIndices = findWavelengthIndices(useWavelengths, allWavelengths, maxDistance);
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

    static double[][] reduceSpectralMeansToWLs(double[][] spectralMeans, int[] useIndices) {
        double[][] reducedSpectralMeans = new double[useIndices.length][];
        for (int i = 0; i < useIndices.length; i++) {
            reducedSpectralMeans[i] = spectralMeans[useIndices[i]];
        }
        return reducedSpectralMeans;
    }

}
