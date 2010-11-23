package org.esa.beam.coastcolour.fuzzy;

import Jama.Matrix;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.IOException;
import java.util.List;

public class Auxdata {

    private double[][] spectralMeans;
    private double[][][] invCovarianceMatrices;

    public Auxdata(String filePath) throws IOException, InvalidRangeException {
        final NetcdfFile netcdfFile = NetcdfFile.open(filePath);
        try {
            final Group rootGroup = netcdfFile.getRootGroup();
            final List<Variable> variableList = rootGroup.getVariables();

            for (Variable variable : variableList) {
                final int[] origin = new int[variable.getRank()];
                final int[] shape = variable.getShape();
                final Array array = variable.read(new Section(origin, shape));
                final Array arrayDouble = Array.factory(DataType.DOUBLE, shape, array.get1DJavaArray(Double.class));
                if ("class_means".equals(variable.getName())) {
                    spectralMeans = (double[][]) arrayDouble.copyToNDJavaArray();
                }
                if ("class_covariance".equals(variable.getName()) || "Yinv".equals(variable.getName())) {
                    invCovarianceMatrices = covarianceInversion((double[][][]) arrayDouble.copyToNDJavaArray());
                }
            }
        } finally {
            netcdfFile.close();
        }
    }

    public double[][] getSpectralMeans() {
        return spectralMeans;
    }


    public double[][][] getInvertedCovarianceMatrices() {
        return invCovarianceMatrices;
    }

    private static double[][][] covarianceInversion(double[][][] reflecCovMatrix) {
        double[][][] invReflecCovMatrix = new double[reflecCovMatrix.length][][];
        for (int i = 0; i < reflecCovMatrix.length; i++) {
            final Matrix matrix = new Matrix(reflecCovMatrix[i]);
            final Matrix invMatrix = matrix.inverse();
            invReflecCovMatrix[i] = invMatrix.getArray();

        }
        return invReflecCovMatrix;
    }

}
