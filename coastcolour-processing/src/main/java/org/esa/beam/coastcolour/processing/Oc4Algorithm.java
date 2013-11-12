package org.esa.beam.coastcolour.processing;

/**
 * Computes con_chl using the OC4 algorithm.
 * Based on the implementation in l2gen of seadas.
 *
 * References:
 *
 * Chlorophyll a algorithms for oligotrophic oceans:
 * A novel approach based on three-band reflectance differenc
 * http://oceancolor.gsfc.nasa.gov/staff/franz/papers/hu_et_al_2012_jgr.pdf
 *
 * Evaluation of ocean color algorithms in the southeastern Beaufort Sea, Canadian Arctic:
 * New parameterization using SeaWiFS, MODIS, and MERIS spectral bands
 * http://www.quebec-ocean.ulaval.ca/pdf_xls_files/recentpub/Ben-Mustapha,_B%C3%A9langer,_Larouche_2012.pdf
 *
 * Ocean Color Chlorophyll (OC) v6
 * http://oceancolor.gsfc.nasa.gov/REPROCESSING/R2009/ocv6/
 *
 * @author MarcoZ
 */
public class Oc4Algorithm {

    public static final double[] CHLOC4_COEF_SEAWIFS = {0.3272, -2.9940, 2.7218, -1.2259, -0.5683};
    public static final double[] CHLOC4_COEF_MERIS   = {0.3255, -2.7677, 2.4409, -1.1288, -0.4990};
    public static final double[] CHLOC4_COEF_CC   = {0.366, -3.067, 1.930, 0.649, -1.532}; // by carsten via daniel

    private static final double MIN_RAT = 0.21f;
    private static final double MAX_RAT = 30.0f;
    private static final double CHL_MIN = 0.0;
    private static final double CHL_MAX = 1000.0;

    private final double[] chloc4Coef;

    public Oc4Algorithm(double[] chloc4Coef) {
        this.chloc4Coef = chloc4Coef;
    }

    public double compute(double rrs443, double rrs490, double rrs510, double rrs555) {

        double rat, minRrs;
        double chl = Double.NaN;

        double rrs1 = rrs443;
        double rrs2 = rrs490;
        double rrs3 = rrs510;
        double rrs4 = rrs555;

        minRrs = Math.min(rrs1, rrs2);

        if (rrs4 > 0.0 && rrs3 > 0.0 && (rrs2 > 0.0 || rrs1 * rrs2 > 0.0) && minRrs > -0.001) {
            rat = Math.max(Math.max(rrs1, rrs2), rrs3) / rrs4;
            if (rat > MIN_RAT && rat < MAX_RAT) {
                rat = Math.log10(rat);
                chl = Math.pow(10.0, (chloc4Coef[0] + rat * (chloc4Coef[1] + rat * (chloc4Coef[2] + rat * (chloc4Coef[3] + rat * chloc4Coef[4])))));
                chl = (chl > CHL_MIN ? chl : CHL_MIN);
                chl = (chl < CHL_MAX ? chl : CHL_MAX);
            }
        }
        return chl;
    }

}
