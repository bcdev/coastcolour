package org.esa.beam.owt;

import ucar.ma2.Array;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.util.List;

/**
 * @author Marco Peters
 */
public class CoastalAuxdataFactory extends AuxdataFactory {

    private final String resourcePath;

    public CoastalAuxdataFactory(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    public Auxdata createAuxdata() throws Exception {
        NetcdfFile netcdfFile;
        try {
            netcdfFile = loadFile(resourcePath);
            try {
                final Group rootGroup = netcdfFile.getRootGroup();
                final List<Variable> variableList = rootGroup.getVariables();

                double[][] spectralMeans = null;
                double[][][] invCovarianceMatrix = null;
                for (Variable variable : variableList) {
                    if ("class_means".equals(variable.getFullName())) {
                        final Array arrayDouble = getDoubleArray(variable);
                        spectralMeans = (double[][]) arrayDouble.copyToNDJavaArray();
                    }
                    if ("class_covariance".equals(variable.getFullName()) || "Yinv".equals(variable.getFullName())) {
                        final Array arrayDouble = getDoubleArray(variable);
                        invCovarianceMatrix = invertMatrix((double[][][]) arrayDouble.copyToNDJavaArray());
                    }
                    if (spectralMeans != null && invCovarianceMatrix != null) {
                        break;
                    }
                }
                return new Auxdata(spectralMeans, invCovarianceMatrix);
            } finally {
                netcdfFile.close();
            }
        } catch (java.lang.Exception e) {
            throw new Exception("Could not load auxiliary data", e);
        }
    }

}
