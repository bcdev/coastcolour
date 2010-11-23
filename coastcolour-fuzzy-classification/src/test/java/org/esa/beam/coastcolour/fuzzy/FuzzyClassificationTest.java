package org.esa.beam.coastcolour.fuzzy;

import org.junit.Before;
import org.junit.Test;
import ucar.ma2.InvalidRangeException;

import java.io.IOException;
import java.net.URL;

import static junit.framework.Assert.*;

public class FuzzyClassificationTest {

    private Auxdata auxdata;

    @Before
    public void setup() throws IOException, InvalidRangeException {
        final URL resourceUrl = FuzzyClassification.class.getResource("owt16_meris_stats_101119_5band_double.hdf");
        final String filePath = resourceUrl.getFile();
        auxdata = new Auxdata(filePath);
    }

    @Test
    public void testFuzzyResults() {
        final double[] reflectances = {0.0307, 0.0414, 0.0500, 0.0507, 0.0454};
        final FuzzyClassification fuzzyClassification = new FuzzyClassification(auxdata.getSpectralMeans(),
                                                                                auxdata.getInvertedCovarianceMatrices());
        final double[] classMembershipProbability = fuzzyClassification.computeClassMemberships(reflectances);

        // these values are validated by algorithm provider Timothy Moore
        double[] expectedValues = new double[]{
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.024374, 0.083183, 0.199592
        };
        assertEquals(expectedValues.length, classMembershipProbability.length);
        for (int i = 0; i < classMembershipProbability.length; i++) {
            assertEquals(expectedValues[i], classMembershipProbability[i], 1.0e-6);
        }

    }
}
