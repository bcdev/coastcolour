package org.esa.beam.coastcolour.fuzzy;

import Jama.Matrix;
import ucar.ma2.InvalidRangeException;

import java.io.IOException;
import java.net.URL;

public class FuzzyMain {

    private static final int BAND_COUNT = 5;        // NBANDS
    private static final int CLASS_COUNT = 16;  // NCLASSES

    private FuzzyMain() {
    }

    public static void main(String[] args) throws IOException, InvalidRangeException {

        double[] reflectances = new double[BAND_COUNT]; // rrs
        initializeSeaWifs(reflectances);

        final URL resourceUrl = FuzzyClassification.class.getResource("owt16_meris_stats_101119_5band_double.hdf");
        final String filePath = resourceUrl.getFile();
        final Auxdata auxdata = new Auxdata(filePath);
        double[][] reflecMeans = auxdata.getSpectralMeans();        // rrs_means
        double[][][] reflecCovMatrix = auxdata.getCovarianceMatrices(); // rrs_cov
        double[][][] invCovMatrix = covarianceInversion(reflecCovMatrix); // y3inv

        // end of initialisation; for each pixel, do:
        double[] outdata = FuzzyClassification.fuzzyFunc(reflectances, reflecMeans, invCovMatrix,
                                                         CLASS_COUNT, BAND_COUNT);

        for (int i = 0, outdataLength = outdata.length; i < outdataLength; i++) {
            double value = outdata[i];
            System.out.printf("value[%d] = %s%n", i, value);
        }
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

    private static void initializeSeaWifs(double[] reflectances) {
        reflectances[0] = 0.0307;
        reflectances[1] = 0.0414;
        reflectances[2] = 0.0500;
        reflectances[3] = 0.0507;
        reflectances[4] = 0.0454;
    }

}
