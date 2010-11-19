package org.esa.beam.coastcolour.fuzzy;

import Jama.Matrix;

public class FuzzyClassification {

    private FuzzyClassification() {
    }

    public static double[] fuzzyFunc(double[] reflectances, double[][] uReflecMeans, double[][][] invCovMatrix,
                                     int classCount, int bandCount) {

        double[] y = new double[bandCount];
        double[][] yInvers = new double[bandCount][bandCount];   // yinv

        double[] alphaChi = new double[classCount];
        for (int i = 0; i < classCount; i++) {
            for (int j = 0; j < bandCount; j++) {
                y[j] = reflectances[j] - uReflecMeans[j][i];
                System.arraycopy(invCovMatrix[i][j], 0, yInvers[j], 0, bandCount);
            }
            final Matrix yInvMatrix = new Matrix(yInvers);
            final Matrix matrixB = yInvMatrix.times(new Matrix(y, y.length));  // b
            double zSquare = 0;
            final double[] b = new double[bandCount];
            b[0] = matrixB.getArray()[0][0];
            b[1] = matrixB.getArray()[1][0];
            b[2] = matrixB.getArray()[2][0];
            b[3] = matrixB.getArray()[3][0];
            b[4] = matrixB.getArray()[4][0];
            for (int j = 0; j < bandCount; j++) {
                zSquare += y[j] * b[j];
            }
            double x = zSquare / 2.0;   // no idea why this is needed. Even Tim doesn't have
            double chiSquare = bandCount / 2.0;  // chiz
            if (x <= (chiSquare + 1.0)) {
                double gamma = getIncompleteGammaFunction(chiSquare, x); // gser
                alphaChi[i] = 1.0 - gamma;
            } else {
                double gamma = gcf(chiSquare, x);
                alphaChi[i] = gamma;
            }
        }

        return alphaChi;

    }

    private static double gcf(double a, double x) {
        final double FPMIN = 1.0e-30;
        final int maxIteration = 100;
        final double eps = 3.0e-7;

        double gln = gammln(a);
        double b = x + 1.0 - a;
        double c = 1.0 / FPMIN;
        double d = 1.0 / b;
        double h = d;
        for (int i = 1; i <= maxIteration; i++) {
            double an = -i * (i - a);
            b += 2.0;
            d = an * d + b;
            if (Math.abs(d) < FPMIN) {
                d = FPMIN;
            }
            c = b + an / c;
            if (Math.abs(c) < FPMIN) {
                c = FPMIN;
            }
            d = 1.0 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1.0) < eps) {
                break;
            }
            if (i > maxIteration) {
                throw new IllegalArgumentException("a too large, ITMAX too small in gcf");
            }
        }
        return Math.exp(-x + a * Math.log(x) - (gln)) * h;
    }

    private static double getIncompleteGammaFunction(double a, double x) {
        if (x <= 0.0) {
            if (x < 0.0) {
                throw new IllegalArgumentException("x less than 0 in routine gser");
            }
            return 0.0;
        } else {
            double ap = a;
            double sum = 1.0 / a;
            double del = sum;

            final int maxIteration = 100;
            final double eps = 3.0e-7;
            for (int i = 1; i <= maxIteration; i++) {
                ++ap;
                del *= x / ap;
                sum += del;
                if (Math.abs(del) < Math.abs(sum) * eps) {
                    final double gammaLog = gammln(a);
                    return sum * Math.exp(-x + a * Math.log(x) - (gammaLog));
                }
            }
            throw new IllegalArgumentException("a too large, ITMAX too small in routine gser");
        }
    }

    private static double gammln(double a) {
        final double[] cof = {
                76.18009172947146, -86.50532032941677,
                24.01409824083091, -1.231739572450155,
                0.1208650973866179e-2, -0.5395239384953e-5
        };
        double y = a;

        double tmp = a + 5.5;
        tmp -= (a + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (int j = 0; j <= 5; j++) {
            ser += cof[j] / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / a);
    }

}
