package org.esa.beam.coastcolour.processing;

import org.esa.beam.atmosphere.operator.GlintCorrection;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.processor.ProcessorException;
import org.esa.beam.processor.flh_mci.BaselineAlgorithm;

// TODO (mp, 07.07.2011) - Not clear yet which algorithm to use. None of the new algos seems to work as expected.
// Only the old one gives reasonable results.

/**
 * Used to compute FLH.
 *
 * @author Marco Peters
 * @since 1.3
 */
class FLHAlgorithm {

    private final double lowBandWavelength;
    private final double signalBandWavelength;
    private final double highBandWavelength;
    private ThreadLocal<BaselineAlgorithm> gowerAlgorithmProvider;

    FLHAlgorithm(double lowBandWavelength, double signalBandWavelength, double highBandWavelength) {
        this.lowBandWavelength = lowBandWavelength;
        this.signalBandWavelength = signalBandWavelength;
        this.highBandWavelength = highBandWavelength;
        gowerAlgorithmProvider = new ThreadLocal<BaselineAlgorithm>() {
            @Override
            protected BaselineAlgorithm initialValue() {
                BaselineAlgorithm baselineAlgo = new BaselineAlgorithm();
                try {
                    baselineAlgo.setWavelengths((float) FLHAlgorithm.this.lowBandWavelength,
                                                (float) FLHAlgorithm.this.highBandWavelength,
                                                (float) FLHAlgorithm.this.signalBandWavelength);
                } catch (ProcessorException e) {
                    throw new OperatorException(e);
                }
                return baselineAlgo;
            }
        };

    }

    double[] computeFLH681(double[] reflec, double[] tosa, double[] path, double[] trans,
                           double cosTetaViewSurfRad, double cosTetaSunSurfRad) {
        double[] reflecFromPath = computeReflectanceFromPath(reflec, tosa, path, trans, cosTetaViewSurfRad,
                                                             cosTetaSunSurfRad);
        double flh681 = computeRolandsFlh(reflec, reflecFromPath);
        double flh681FromPath = computeStdFlh(reflecFromPath);
        double flh681FromNN = computeStdFlh(reflec);

        BaselineAlgorithm gowerAlgorithm = gowerAlgorithmProvider.get();
        float[] normOldFlh = gowerAlgorithm.process(new float[]{(float) reflec[0]}, new float[]{(float) reflec[2]},
                                                    new float[]{(float) reflec[1]}, new boolean[]{true}, null);
        float[] alterOldFlh = gowerAlgorithm.process(new float[]{(float) reflecFromPath[0]},
                                                     new float[]{(float) reflecFromPath[2]},
                                                     new float[]{(float) reflecFromPath[1]}, new boolean[]{true}, null);

        return new double[]{flh681, normOldFlh[0], alterOldFlh[0], flh681FromNN, flh681FromPath};
    }

    private double computeStdFlh(double[] reflecFromPath) {
        int band7Index = 0;
        int band8Index = 1;
        int band9Index = 2;
        // y = ((y2 - y1) / (x2 - x1)) * x + (x2*y1 - x1*y2) /(x2 -x1)
        double y1 = reflecFromPath[band7Index];
        double y2 = reflecFromPath[band9Index];
        double x1 = lowBandWavelength;
        double x2 = highBandWavelength;
        double x = signalBandWavelength;

        double y = ((y2 - y1) / (x2 - x1)) * x + (x2 * y1 - x1 * y2) / (x2 - x1);
        return reflecFromPath[band8Index] - y;
    }

    private double computeRolandsFlh(double[] reflec, double[] reflecFromPath) {
        int band7Index = 0;
        int band8Index = 1;
        int band9Index = 2;
        double ratioBand7 = reflecFromPath[band7Index] / reflec[band7Index];
        double ratioBand9 = reflecFromPath[band9Index] / reflec[band9Index];
        double slope = (ratioBand9 - ratioBand7) / (highBandWavelength - lowBandWavelength);
        double ratioBand8 = ratioBand7 + (signalBandWavelength - lowBandWavelength) * slope;
        double interpolReflecFromPath = reflecFromPath[band8Index] / ratioBand8;
        double flh681 = interpolReflecFromPath - reflec[band8Index];
//        flh681 = Math.max(flh681, 0.0);
        return flh681;

    }

    private double[] computeReflectanceFromPath(double[] reflec, double[] tosa, double[] path, double[] trans,
                                                double cosTetaViewSurfRad, double cosTetaSunSurfRad) {
        double[] reflecFromPath = new double[reflec.length];
        double radiance2IrradianceFactor = 1.0; // no conversion needed; values are already radiance reflectances
        for (int i = 0; i < reflecFromPath.length; i++) {
            reflecFromPath[i] = GlintCorrection.deriveReflecFromPath(path[i], trans[i], tosa[i],
                                                                     cosTetaViewSurfRad, cosTetaSunSurfRad,
                                                                     radiance2IrradianceFactor);
        }
        return reflecFromPath;
    }

}
