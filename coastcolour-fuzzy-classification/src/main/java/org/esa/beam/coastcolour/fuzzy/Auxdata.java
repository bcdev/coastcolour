package org.esa.beam.coastcolour.fuzzy;

import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Auxdata {

    private double[][] spectralMeans;
    private double[][][] covarianceMatrices;

    public Auxdata(String resourcePath) throws IOException, InvalidRangeException {
        final NetcdfFile netcdfFile = NetcdfFile.open(resourcePath);
        try {
            final Group rootGroup = netcdfFile.getRootGroup();
            final List<Variable> variableList = rootGroup.getVariables();

            for (Variable variable : variableList) {
                final int[] origin = new int[variable.getRank()];
                Arrays.fill(origin, 0);
                final Array array = variable.read(new Section(origin, variable.getShape()));//            
                if ("class_means".equals(variable.getName())) {
                    spectralMeans = (double[][]) array.copyToNDJavaArray();
                }
                if ("class_covariance".equals(variable.getName()) || "Yinv".equals(variable.getName())) {
                    covarianceMatrices = (double[][][]) array.copyToNDJavaArray();
                }
            }
        } finally {
            netcdfFile.close();
        }
    }

    public double[][] getSpectralMeans() {
        return spectralMeans;
    }

    public double[][][] getCovarianceMatrices() {
        return covarianceMatrices;
    }
}
