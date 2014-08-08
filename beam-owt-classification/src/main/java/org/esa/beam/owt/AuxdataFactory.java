package org.esa.beam.owt;

import Jama.Matrix;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.Variable;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Marco Peters
 */
public abstract class AuxdataFactory {

    protected static int[] findWavelengthIndices(float[] useWavelengths, float[] allWavelengths, float maxDistance) {

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
