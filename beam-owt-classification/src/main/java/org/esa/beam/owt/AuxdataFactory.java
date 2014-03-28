package org.esa.beam.owt;

import Jama.Matrix;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.Variable;

import java.io.IOException;

/**
 * @author Marco Peters
 */
public abstract class AuxdataFactory {

    abstract Auxdata createAuxdata() throws Exception;

    protected Array getDoubleArray(Variable variable) throws IOException, InvalidRangeException {
        final int[] origin = new int[variable.getRank()];
        final int[] shape = variable.getShape();
        final Array array = variable.read(new Section(origin, shape));
        return Array.factory(DataType.DOUBLE, shape, array.get1DJavaArray(Double.class));
    }

    protected static double[][][] invertMatrix(double[][][] matrix) {
        double[][][] invMatrix = new double[matrix.length][][];
        for (int i = 0; i < matrix.length; i++) {
            final Matrix tempMatrix = new Matrix(matrix[i]);
            final Matrix tempInvMatrix = tempMatrix.inverse();
            invMatrix[i] = tempInvMatrix.getArray();

        }
        return invMatrix;
    }

    class Exception extends java.lang.Exception {

        public Exception(String message) {
            super(message);
        }

        public Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
