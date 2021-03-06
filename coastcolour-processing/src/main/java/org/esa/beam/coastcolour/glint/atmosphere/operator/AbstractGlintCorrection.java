package org.esa.beam.coastcolour.glint.atmosphere.operator;

import org.esa.beam.coastcolour.glint.PixelData;
import org.esa.beam.meris.radiometry.smilecorr.SmileCorrectionAuxdata;
import org.esa.beam.coastcolour.glint.nn.NNffbpAlphaTabFast;

import java.util.Arrays;
import java.util.Collections;

/**
 * Class providing the AGC Glint correction.
 */
abstract class AbstractGlintCorrection {

    static final double[] MERIS_WAVELENGTHS = {
            412.3, 442.3, 489.7,
            509.6, 559.5, 619.4,
            664.3, 680.6, 708.1,
            753.1, 778.2, 864.6
    };

    static final int LAND = 0x01;
    static final int CLOUD_ICE = 0x02;
    static final int AOT560_OOR = 0x04;
    static final int TOA_OOR = 0x08;
    static final int TOSA_OOR = 0x10;
    static final int TOSA_OOS = 0x20;
    static final int SOLZEN = 0x40;
    static final int ANCIL = 0x80;
    static final int SUNGLINT = 0x100;
    static final int INPUT_INVALID = 0x800;  // LAND || CLOUD_ICE || l1_flags.INVALID
    static final int L2R_INVALID = 0x1000;  // quality indicator > 3 || 100% clouds
    static final int L2R_SUSPECT = 0x2000;  // quality indicator > 1 || cloud/buffer/shadow || mixed pixel

    static final int L1_INVALID_FLAG = 0x80;

    static final double MAX_TAU_FACTOR = 0.84;

//    static final double[] H2O_COR_POLY = new double[]{
//            0.3832989, 1.6527957, -1.5635101, 0.5311913
//    }; // polynom coefficients for band708 correction


    NNffbpAlphaTabFast atmosphereNet;
    NNffbpAlphaTabFast invAotAngNet;
    SmileCorrectionAuxdata smileAuxdata;
    NNffbpAlphaTabFast normalizationNet;
    NNffbpAlphaTabFast autoAssocNet;
    ReflectanceEnum outputReflecAs;

    /**
     * This method performa the Glint correction, using new AC net (March 2012).
     *
     * @param pixel            the pixel input data
     * @param deriveRwFromPath whether to derive the water leaving reflectance from path or not
     * @param temperature      the water temperature
     * @param salinity         the water salinity
     *
     * @return GlintResult
     */
    abstract GlintResult perform(PixelData pixel, boolean deriveRwFromPath, double temperature, double salinity, double tosaOosThresh);

    public static double correctViewAngle(double teta_view_deg, int pixelX, int centerPixel, boolean isFullResolution) {
        final double ang_coef_1 = -0.004793;
        final double ang_coef_2 = isFullResolution ? 0.0093247 / 4 : 0.0093247;
        teta_view_deg = teta_view_deg + Math.abs(pixelX - centerPixel) * ang_coef_2 + ang_coef_1;
        return teta_view_deg;
    }

    public static double deriveReflecFromPath(double rwPath, double transd, double rlTosa, double cosTetaViewSurfRad,
                                              double cosTetaSunSurfRad, double radiance2IrradianceFactor) {

        double transu = Math.exp(Math.log(transd) * (cosTetaSunSurfRad / cosTetaViewSurfRad));
        transu *= radiance2IrradianceFactor;
        double edBoa = transd * cosTetaSunSurfRad;
        return (rlTosa - rwPath) / (transu * edBoa) * cosTetaSunSurfRad;

        // simplest solution but gives nearly the same results
//        return (rlTosa - rwPath) / Math.pow(transd,2);

    }

    static double[] computeXYZCoordinates(double tetaViewSurfRad, double aziDiffSurfRad) {
        double[] xyz = new double[3];

        xyz[0] = Math.sin(tetaViewSurfRad) * Math.cos(aziDiffSurfRad);
        xyz[1] = Math.sin(tetaViewSurfRad) * Math.sin(aziDiffSurfRad);
        xyz[2] = Math.cos(tetaViewSurfRad);
        return xyz;
    }

//    void computeTosaQuality(double[] rlTosa, double[] aaNNOutnet, GlintResult glintResult) {
//        double[] autoRTosa = aaNNOutnet.clone();
//        double[] autoRlTosa = new double[autoRTosa.length];
//        for (int i = 0; i < autoRTosa.length; i++) {
//            autoRlTosa[i] = autoRTosa[i]/Math.PI;
//        }
//        glintResult.setAutoTosaReflec(autoRlTosa);
//        double chi_sum = 0.0;
//        for (int i = 0; i < rlTosa.length; i++) {
//            final double logRlTosa = Math.log(rlTosa[i]);
//            final double logAutoRlTosa = Math.log(autoRlTosa[i]);
//            chi_sum += Math.pow(((logRlTosa - logAutoRlTosa) / logRlTosa), 2.0); //RD20110116
//        }
//        final double chi_square = chi_sum / rlTosa.length;
//        glintResult.setTosaQualityIndicator(chi_square);
//    }

    static void computeTosaQuality(double[] rlTosa, double[] autoRTosa, GlintResult glintResult) {
        double[] logAutoRlTosa = new double[autoRTosa.length];
        double[] logRlTosa = new double[autoRTosa.length];
        for (int i = 0; i < autoRTosa.length; i++) {
            logRlTosa[i] = Math.log(rlTosa[i]);
            logAutoRlTosa[i] = Math.log(autoRTosa[i]/Math.PI);
        }
        final double chi_square = getChiSqrFromLargestDiffs(logRlTosa, logAutoRlTosa, 4);
        glintResult.setTosaQualityIndicator(chi_square*1.E4);
    }

    /**
     * get a 'chi square' error for two arrays, considering their largest differences only
     *
     * @param arr1
     * @param arr2
     * @param numDiffs - the number of differences (sorted descending) to consider
     * @return the error
     */
    static double getChiSqrFromLargestDiffs(double[] arr1, double[] arr2, int numDiffs) {
        double chi_sum = 0.0;
        Double[] diff = new Double[arr1.length];
        for (int i = 0; i < arr1.length; i++) {
            diff[i] = arr1[i] != 0.0 ? Math.pow(Math.abs((arr1[i] - arr2[i]) / arr1[i]), 2.0) : 0.0;
        }
        Arrays.sort(diff, Collections.reverseOrder());

        for (int i = 0; i < numDiffs; i++) {
            chi_sum += diff[i];
        }
        return chi_sum / numDiffs;
    }

    static boolean isRlToaOor(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.RLTOA_OOR_FLAG_MASK) == ToaReflectanceValidationOp.RLTOA_OOR_FLAG_MASK;
    }

    static boolean isCloudIce(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.CLOUD_ICE_FLAG_MASK) == ToaReflectanceValidationOp.CLOUD_ICE_FLAG_MASK;
    }

    static boolean isLand(PixelData pixel) {
        return (pixel.validation & ToaReflectanceValidationOp.LAND_FLAG_MASK) == ToaReflectanceValidationOp.LAND_FLAG_MASK;
    }

    /*--------------------------------------------------------------------------
     **	test TOSA radiances as input to neural network for out of training range
     **  with band_nu 17/3/05 R.D.
    --------------------------------------------------------------------------*/

    static boolean isTosaReflectanceValid(double[] tosaRefl, NNffbpAlphaTabFast atmosphereNet) {
        int tosaOffset = 6;
        double[] inmax = atmosphereNet.getInmax();
        double[] inmin = atmosphereNet.getInmin();
        for (int i = 0; i < tosaRefl.length; i++) {
//            double currentRlTosa = Math.log(tosaRefl[i]);
            double currentRlTosa = Math.log(Math.PI*tosaRefl[i]);
            if (currentRlTosa > inmax[i + tosaOffset] || currentRlTosa < inmin[i + tosaOffset]) {
                return false;
            }
        }
        return true;
    }

    static boolean isAncillaryDataValid(PixelData pixel) {
        final boolean ozoneValid = pixel.ozone >= 200 && pixel.ozone <= 500;
        final boolean pressureValid = pixel.pressure >= 500 && pixel.pressure <= 1100;
        return ozoneValid && pressureValid;
    }


    static double getAzimuthDifference(PixelData pixel) {
//        delta_azimuth=fabs(view_azimuth-sun_azimuth);
//       	if(delta_azimuth>180.0) delta_azimuth=180.0-delta_azimuth;

        double aziViewSurfRad = Math.toRadians(pixel.satazi);
        double aziSunSurfRad = Math.toRadians(pixel.solazi);
        double aziDiffSurfRad = Math.acos(Math.cos(aziViewSurfRad - aziSunSurfRad));
        return Math.toDegrees(aziDiffSurfRad);
    }

    // currently not needed
//    double correctRlTosa9forWaterVapour(PixelData pixel, double rlTosa9) {
//        double rho_885 = pixel.toa_radiance[13] / pixel.solar_flux[13];
//        double rho_900 = pixel.toa_radiance[14] / pixel.solar_flux[14];
//        double x2 = rho_900 / rho_885;
//        double trans708 = H2O_COR_POLY[0] + H2O_COR_POLY[1] * x2 + H2O_COR_POLY[2] * x2 * x2 + H2O_COR_POLY[3] * x2 * x2 * x2;
//        return rlTosa9 / trans708;
//    }

}
