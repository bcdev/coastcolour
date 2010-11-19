package org.esa.beam.coastcolour.fuzzy;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.experimental.PixelOperator;
import org.esa.beam.framework.gpf.experimental.PointOperator;

import java.net.URL;

@OperatorMetadata(alias = "CoastColour.FuzzyClassification",
                  description = ".",
                  authors = "Timothy Moore (University of New Hampshire); Marco Peters, Thomas Storm (Brockmann Consult)",
                  copyright = "(c) 2010 by Brockmann Consult",
                  version = "1.0")
public class FuzzyOp extends PixelOperator {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @Parameter(defaultValue = "5",
               label = "The number of bands to consider.")
    private int bandCount;        // NBANDS

    @Parameter(defaultValue = "16",
               label = "The number of classes output by the algorithm")
    private int classCount;  // NCLASSES

    private FuzzyClassification fuzzyClassification;

    @Override
    protected void configureTargetProduct(Product targetProduct) {
        for (int i = 0; i < classCount; i++) {
            targetProduct.addBand("Class " + i, ProductData.TYPE_FLOAT64);
        }
    }

    @Override
    protected void configureSourceSamples(PointOperator.Configurator configurator) {
        // general initialisation
        final URL resourceUrl = FuzzyClassification.class.getResource("owt16_meris_stats_101119_5band_double.hdf");
        final String filePath = resourceUrl.getFile();
        final Auxdata auxdata;
        try {
            auxdata = new Auxdata(filePath);
        } catch (Exception e) {
            throw new OperatorException(e);
        }
        fuzzyClassification = new FuzzyClassification(auxdata.getSpectralMeans(),
                                                      auxdata.getInvertedCovarianceMatrices());

        // actual sample configuration
        configurator.defineSample(0, "reflec_1");
        configurator.defineSample(1, "reflec_2");
        configurator.defineSample(2, "reflec_3");
        configurator.defineSample(3, "reflec_4");
        configurator.defineSample(4, "reflec_5");
//        configurator.defineSample( 5, "reflec_7" );
    }

    @Override
    protected void configureTargetSamples(PointOperator.Configurator configurator) {
        Band[] bands = getTargetProduct().getBands();
        for (int i = 0; i < bands.length; i++) {
            Band band = bands[i];
            configurator.defineSample(i, band.getName());
        }
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        if (sourceSamples.length != bandCount) {
            throw new OperatorException("Wrong number of source samples: Expected: " + bandCount +
                                        ", Actual: " + sourceSamples.length);
        }
        double[] rrs = new double[bandCount];
        for (int i = 0; i < bandCount; i++) {
            Sample sourceSample = sourceSamples[i];
            final double merisL2Reflec = sourceSample.getDouble();
            final double rrsAboveWater = merisL2Reflec / Math.PI;
            rrs[i] = rrsAboveWater / (0.52 + 1.7 * rrsAboveWater);
        }
        double[] membershipIndicator = fuzzyClassification.fuzzyFunc(rrs); // outdata

        for (int i = 0; i < targetSamples.length; i++) {
            WritableSample targetSample = targetSamples[i];
            targetSample.set(membershipIndicator[i]);
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(FuzzyOp.class);
        }
    }
}
