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

    @Parameter(notNull = true,
               valueSet = {
                       "owt16_meris_stats_101119_5band.hdf",
                       "owt16_meris_stats_101119_5band_double.hdf",
                       "owt16_meris_stats_101119_6band.hdf",
                       "owt16_modis_stats_101111.hdf",
                       "owt16_seawifs_stats_101111.hdf"
               })
    private String auxdataPath;

    @Parameter(defaultValue = "reflec")
    private String reflectancesPrefix;

    private FuzzyClassification fuzzyClassification;

    private static final int CLASS_COUNT = 11;

    @Override
    protected void configureTargetProduct(Product targetProduct) {
        for (int i = 0; i < CLASS_COUNT; i++) {
            targetProduct.addBand("Class " + i, ProductData.TYPE_FLOAT64);
        }
    }

    @Override
    protected void configureSourceSamples(PointOperator.Configurator configurator) {
        // general initialisation
        final URL resourceUrl = FuzzyClassification.class.getResource(auxdataPath);
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
        configurator.defineSample(0, reflectancesPrefix + "_1");
        configurator.defineSample(1, reflectancesPrefix + "_2");
        configurator.defineSample(2, reflectancesPrefix + "_3");
        configurator.defineSample(3, reflectancesPrefix + "_4");
        configurator.defineSample(4, reflectancesPrefix + "_5");
        if (getBandCount() == 6) {
            configurator.defineSample(5, reflectancesPrefix + "_7");
        }
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
        int bandCount = getBandCount();        // NBANDS
        if (sourceSamples.length != bandCount) {
            throw new OperatorException("Wrong number of source samples: Expected: " + bandCount +
                                        ", Actual: " + sourceSamples.length);
        }
        double[] rrsBelowWater = new double[bandCount];
        for (int i = 0; i < bandCount; i++) {
            Sample sourceSample = sourceSamples[i];
            final double merisL2Reflec = sourceSample.getDouble();
            final double rrsAboveWater = merisL2Reflec / Math.PI;
            rrsBelowWater[i] = rrsAboveWater / (0.52 + 1.7 * rrsAboveWater);
        }
        double[] membershipIndicators = fuzzyClassification.fuzzyFunc(rrsBelowWater);

        // setting the values for the first 8 classes
        for (int i = 0; i < 8; i++) {
            WritableSample targetSample = targetSamples[i];
            final double membershipIndicator = membershipIndicators[i];
            targetSample.set(membershipIndicator);
        }

        // setting the value for the 9th class to the sum of the last 8 classes
        double ninthClassValue = 0.0;
        for (int i = 8; i < 16; i++) {
            ninthClassValue += membershipIndicators[i];
        }
        targetSamples[8].set(ninthClassValue);

        // setting the value for the 10th class, which is the max value of all other classes
        double tenthClassValue = Double.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            final double other = membershipIndicators[i];
            if (other > tenthClassValue) {
                tenthClassValue = other;
            }
        }
        targetSamples[9].set(tenthClassValue);

        // setting the value for the 11th class, which is the sum of all other classes
        double eleventhClassValue = 0.0;
        for (int i = 0; i < 10; i++) {
            eleventhClassValue += targetSamples[i].getDouble();
        }
        targetSamples[10].set(eleventhClassValue);
    }

    private int getBandCount() {
        if (auxdataPath.startsWith("owt16_meris_stats")) {
            int bandIndex = auxdataPath.lastIndexOf("band");
            final String number = auxdataPath.substring(bandIndex - 1, bandIndex);
            return Integer.parseInt(number);
        }
        return 5;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(FuzzyOp.class);
        }
    }
}
