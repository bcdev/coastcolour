package org.esa.beam.owt;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;


public class OWTClassificationTest {

    private Auxdata auxdata;

    @Before
    public void setup() throws Exception {
        auxdata = new CoastalAuxdataFactory("/auxdata/coastal/owt16_meris_stats_101119_5band.hdf").createAuxdata();
    }

    @Test
    public void testFuzzyResults() {
        final double[] reflectances = {0.0307, 0.0414, 0.0500, 0.0507, 0.0454};
        final OWTClassification owtClassification = new OWTClassification(auxdata.getSpectralMeans(),
                                                                                auxdata.getInvertedCovarianceMatrices());
        final double[] classMembershipProbability = owtClassification.computeClassMemberships(reflectances);

        // these values are validated by algorithm provider Timothy Moore
        final double[] expectedValues = new double[]{
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.024374, 0.083183, 0.199592
        };
        assertEquals(expectedValues.length, classMembershipProbability.length);
        final double normalizationFactor = 1.0 / (0.024374 + 0.083183 + 0.19959);
        for (int i = 0; i < classMembershipProbability.length; i++) {
            assertEquals(expectedValues[i], classMembershipProbability[i], 1.0e-5);
            final double normalizedMembership = OWTClassificationOp.normalizeClassMemberships(classMembershipProbability)[i];
            assertEquals(normalizationFactor * expectedValues[i], normalizedMembership, 1.0e-5);
        }

    }
}
