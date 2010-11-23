package org.esa.beam.coastcolour.fuzzy;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.IndexCoding;
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

@SuppressWarnings({"UnusedDeclaration"})
@OperatorMetadata(alias = "CoastColour.FuzzyClassification",
                  description = ".",
                  authors = "Timothy Moore (University of New Hampshire); Marco Peters, Thomas Storm (Brockmann Consult)",
                  copyright = "(c) 2010 by Brockmann Consult",
                  version = "1.0")
public class FuzzyOp extends PixelOperator {

    private static final String AUXDATA_PATH = "owt16_meris_stats_101119_5band_double.hdf";
    private static final float[] BAND_WAVELENGTHS = new float[]{410.0f, 443.0f, 490.0f, 510.0f, 555.0f, 670.0f};
    private static final int CLASS_COUNT = 9;

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @Parameter(defaultValue = "reflec")
    private String reflectancesPrefix;

    private FuzzyClassification fuzzyClassification;
    private int bandCount;


    @Override
    protected void configureTargetProduct(Product targetProduct) {
        for (int i = 1; i <= CLASS_COUNT; i++) {
            final Band classBand = targetProduct.addBand("class_" + i, ProductData.TYPE_FLOAT32);
            classBand.setValidPixelExpression(classBand.getName() + " > 0.0");
            classBand.setNoDataValueUsed(true);
        }
        final Band domClassBand = targetProduct.addBand("dominant_class", ProductData.TYPE_INT8);
        domClassBand.setNoDataValue(-1);
        domClassBand.setNoDataValueUsed(true);
        final IndexCoding indexCoding = new IndexCoding("Cluster_classes");
        for (int i = 1; i <= CLASS_COUNT; i++) {
            indexCoding.addIndex("class_" + i, i, "Class " + i);
        }
        targetProduct.getIndexCodingGroup().add(indexCoding);
        domClassBand.setSampleCoding(indexCoding);

        final Band sumBand = targetProduct.addBand("class_sum", ProductData.TYPE_FLOAT32);
        sumBand.setValidPixelExpression(sumBand.getName() + " > 0.0");
    }

    @Override
    protected void configureSourceSamples(PointOperator.Configurator configurator) {
        // general initialisation
        final URL resourceUrl = FuzzyClassification.class.getResource(AUXDATA_PATH);
        final String filePath = resourceUrl.getFile();
        final Auxdata auxdata;
        try {
            auxdata = new Auxdata(filePath);
        } catch (Exception e) {
            throw new OperatorException(e);
        }
        fuzzyClassification = new FuzzyClassification(auxdata.getSpectralMeans(),
                                                      auxdata.getInvertedCovarianceMatrices());
        bandCount = auxdata.getSpectralMeans().length;
        for (int i = 0; i < bandCount; i++) {
            final String bandName = getSourceBandName(reflectancesPrefix, BAND_WAVELENGTHS[i]);
            configurator.defineSample(i, bandName);
        }
    }

    private String getSourceBandName(String reflectancesPrefix, float wavelength) {
        final double maxDistance = 10.0;
        final String[] bandNames = sourceProduct.getBandNames();
        String bestBandName = null;
        double wavelengthDist = Double.MAX_VALUE;
        for (String bandName : bandNames) {
            final Band band = sourceProduct.getBand(bandName);
            final boolean isSpectralBand = band.getSpectralBandIndex() > -1;
            if (isSpectralBand && bandName.startsWith(reflectancesPrefix)) {
                final float currentWavelengthDist = Math.abs(band.getSpectralWavelength() - wavelength);
                if (currentWavelengthDist < wavelengthDist && currentWavelengthDist < maxDistance) {
                    wavelengthDist = currentWavelengthDist;
                    bestBandName = bandName;
                }
            }
        }
        if (bestBandName == null) {
            throw new OperatorException(
                    String.format("Not able to find band with prefix '%s' and wavelength '%4.3f'.",
                                  reflectancesPrefix, wavelength));
        }
        return bestBandName;
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

        if (!areSourceSamplesValid(x, y, sourceSamples)) {
            for (int i = 0; i < CLASS_COUNT; i++) {
                targetSamples[i].set(Double.NaN);
            }
            targetSamples[9].set(-1);
            targetSamples[10].set(Double.NaN);
            return;
        }

        double[] rrsBelowWater = new double[bandCount];
        for (int i = 0; i < bandCount; i++) {
            rrsBelowWater[i] = convertToSubsurfaceWaterRrs(sourceSamples[i].getDouble());
        }
        double[] membershipIndicators = fuzzyClassification.computeClassMemberships(rrsBelowWater);

        // setting the values for the first 8 classes
        for (int i = 0; i < 8; i++) {
            WritableSample targetSample = targetSamples[i];
            final double membershipIndicator = membershipIndicators[i];
            targetSample.set(membershipIndicator);
        }

        // setting the value for the 9th class to the sum of the last 8 classes
        double ninthClassValue = 0.0;
        for (int i = 8; i < membershipIndicators.length; i++) {
            ninthClassValue += membershipIndicators[i];
        }
        targetSamples[8].set(ninthClassValue);

        // setting the value for dominant class, which is the max value of all other classes
        // setting the value for class sum, which is the sum of all other classes
        int dominantClass = -1;
        double dominantClassValue = Double.MIN_VALUE;
        double classSum = 0.0;
        for (int i = 0; i < 9; i++) {
            final double currentClassValue = targetSamples[i].getDouble();
            if (currentClassValue > dominantClassValue) {
                dominantClassValue = currentClassValue;
                dominantClass = i + 1;
            }
            classSum += currentClassValue;
        }
        targetSamples[CLASS_COUNT].set(dominantClass);
        targetSamples[CLASS_COUNT + 1].set(classSum);
    }

    private boolean areSourceSamplesValid(int x, int y, Sample[] sourceSamples) {
        if (!sourceProduct.containsPixel(x, y)) {
            return false;
        }
        for (Sample sourceSample : sourceSamples) {
            if (!sourceSample.getNode().isPixelValid(x, y)) {
                return false;
            }
        }
        return true;
    }

    // todo - conversion should be configurable
    // should be discussed with CB, KS
    private double convertToSubsurfaceWaterRrs(double merisL2Reflec) {
        // convert to remote sensing reflectances
        final double rrsAboveWater = merisL2Reflec / Math.PI;
        // convert to subsurface water remote sensing reflectances
        return rrsAboveWater / (0.52 + 1.7 * rrsAboveWater);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(FuzzyOp.class);
        }
    }
}
