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
import org.esa.beam.framework.gpf.pointop.PixelOperator;
import org.esa.beam.framework.gpf.pointop.ProductConfigurer;
import org.esa.beam.framework.gpf.pointop.Sample;
import org.esa.beam.framework.gpf.pointop.SampleConfigurer;
import org.esa.beam.framework.gpf.pointop.WritableSample;
import org.esa.beam.util.ProductUtils;

@SuppressWarnings({"UnusedDeclaration"})
@OperatorMetadata(alias = "CoastColour.FuzzyClassification",
                  description = ".",
                  authors = "Timothy Moore (University of New Hampshire); Marco Peters, Thomas Storm (Brockmann Consult)",
                  copyright = "(c) 2010 by Brockmann Consult",
                  version = "1.3")
public class FuzzyOp extends PixelOperator {

    private int nanPixelCount = 0;

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @Parameter(defaultValue = "reflec")
    private String reflectancesPrefix;

    @Parameter(defaultValue = "false")
    private boolean writeInputReflectances;

    private FuzzyClassification fuzzyClassification;
    private Auxdata auxdata;
    private OWT_TYPE owtType = OWT_TYPE.COASTAL;

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        super.configureTargetProduct(productConfigurer);

        AuxdataFactory auxdataFactory = owtType.getAuxdataFactory();
        try {
            auxdata = auxdataFactory.createAuxdata();
        } catch (AuxdataFactory.Exception e) {
            throw new OperatorException("Unable to initialise auxdata", e);
        }

        Product targetProduct = productConfigurer.getTargetProduct();

        for (int i = 1; i <= owtType.getClassCount(); i++) {
            final Band classBand = targetProduct.addBand("class_" + i, ProductData.TYPE_FLOAT32);
            classBand.setValidPixelExpression(classBand.getName() + " > 0.0");
            classBand.setNoDataValueUsed(true);
        }
        for (int i = 1; i <= owtType.getClassCount(); i++) {
            final Band normalizedClassBand = targetProduct.addBand("norm_class_" + i, ProductData.TYPE_FLOAT32);
            normalizedClassBand.setValidPixelExpression(normalizedClassBand.getName() + " > 0.0");
            normalizedClassBand.setNoDataValueUsed(true);
        }
        final Band domClassBand = targetProduct.addBand("dominant_class", ProductData.TYPE_INT8);
        domClassBand.setNoDataValue(-1);
        domClassBand.setNoDataValueUsed(true);
        final IndexCoding indexCoding = new IndexCoding("Cluster_classes");
        for (int i = 1; i <= owtType.getClassCount(); i++) {
            String name = "class_" + i;
            indexCoding.addIndex(name, i, "Class " + i);
        }
        targetProduct.getIndexCodingGroup().add(indexCoding);
        domClassBand.setSampleCoding(indexCoding);


        final Band sumBand = targetProduct.addBand("class_sum", ProductData.TYPE_FLOAT32);
        sumBand.setValidPixelExpression(sumBand.getName() + " > 0.0");
        final Band normalizedSumBand = targetProduct.addBand("norm_class_sum", ProductData.TYPE_FLOAT32);
        normalizedSumBand.setValidPixelExpression(normalizedSumBand.getName() + " > 0.0");

        if (writeInputReflectances) {
            float[] wavelengths = owtType.getWavelengths();
            for (float wavelength : wavelengths) {
                final String bandName = getSourceBandName(reflectancesPrefix, wavelength);
                ProductUtils.copyBand(bandName, sourceProduct, targetProduct, true);
            }
        }
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        fuzzyClassification = new FuzzyClassification(auxdata.getSpectralMeans(),
                                                      auxdata.getInvertedCovarianceMatrices());
        float[] wavelengths = owtType.getWavelengths();
        for (int i = 0; i < wavelengths.length; i++) {
            final String bandName = getSourceBandName(reflectancesPrefix, wavelengths[i]);
            sampleConfigurer.defineSample(i, bandName);
        }
    }

    @Override
    protected void configureTargetSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        Band[] bands = getTargetProduct().getBands();
        int targetSampleIndex = 0;
        for (Band band : bands) {
            if (mustDefineTargetSample(band)) {
                sampleConfigurer.defineSample(targetSampleIndex, band.getName());
                targetSampleIndex++;
            }
        }
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        int numWLs = owtType.getWavelengths().length;
        if (sourceSamples.length != numWLs) {
            throw new OperatorException("Wrong number of source samples: Expected: " + numWLs +
                                        ", Actual: " + sourceSamples.length);
        }

        if (!areSourceSamplesValid(x, y, sourceSamples)) {
            for (int i = 0; i < owtType.getClassCount() * 2; i++) {
                targetSamples[i].set(Double.NaN);
            }
            targetSamples[owtType.getClassCount() * 2].set(-1);
            targetSamples[owtType.getClassCount() * 2 + 1].set(-1);
            targetSamples[owtType.getClassCount() * 2 + 2].set(Double.NaN);
            return;
        }

        double[] rrsBelowWater = new double[numWLs];
        for (int i = 0; i < numWLs; i++) {
            rrsBelowWater[i] = convertToSubsurfaceWaterRrs(sourceSamples[i].getDouble());
        }

        double[] classMemberships = fuzzyClassification.computeClassMemberships(rrsBelowWater);
        double[] classes = owtType.mapMembershipsToClasses(classMemberships);
        for (int i = 0; i < classes.length; i++) {
            targetSamples[i].set(classes[i]);
        }

        final double[] normClassMemberships = normalizeClassMemberships(classMemberships);
        double[] normClasses = owtType.mapMembershipsToClasses(normClassMemberships);
        for (int i = 0; i < classes.length; i++) {
            targetSamples[owtType.getClassCount() + i].set(normClasses[i]);
        }

        // setting the value for dominant class, which is the max value of all other classes
        // setting the value for class sum, which is the sum of all other classes
        int dominantClass = -1;
        double dominantClassValue = Double.MIN_VALUE;
        double classSum = 0.0;
        double normalizedClassSum = 0.0;
        for (int i = 0; i < 9; i++) {
            final double currentClassValue = targetSamples[i].getDouble();
            if (currentClassValue > dominantClassValue) {
                dominantClassValue = currentClassValue;
                dominantClass = i + 1;
            }
            classSum += currentClassValue;
            normalizedClassSum += targetSamples[owtType.getClassCount() + i].getDouble();
        }
        targetSamples[owtType.getClassCount() * 2].set(dominantClass);
        targetSamples[owtType.getClassCount() * 2 + 1].set(classSum);
        targetSamples[owtType.getClassCount() * 2 + 2].set(normalizedClassSum);

    }

    static double[] normalizeClassMemberships(double[] memberships) {
        double[] result = new double[memberships.length];

        // normalize: sum of memberships should be equal to 1.0
        double sum = 0.0;
        for (double membership : memberships) {
            sum += membership;
        }
        for (int i = 0; i < memberships.length; i++) {
            result[i] = memberships[i] / sum;
        }

        return result;
    }

    static String getBestBandName(String reflectancesPrefix, float wavelength, Band[] bands) {
        String bestBandName = null;
        final double maxDistance = 10.0;
        double wavelengthDist = Double.MAX_VALUE;
        for (Band band : bands) {
            final boolean isSpectralBand = band.getSpectralBandIndex() > -1;
            if (isSpectralBand && band.getName().startsWith(reflectancesPrefix)) {
                final float currentWavelengthDist = Math.abs(band.getSpectralWavelength() - wavelength);
                if (currentWavelengthDist < wavelengthDist && currentWavelengthDist < maxDistance) {
                    wavelengthDist = currentWavelengthDist;
                    bestBandName = band.getName();
                }
            }
        }
        return bestBandName;
    }

    private String getSourceBandName(String reflectancesPrefix, float wavelength) {
        final Band[] bands = sourceProduct.getBands();
        String bestBandName = getBestBandName(reflectancesPrefix, wavelength, bands);
        if (bestBandName == null) {
            throw new OperatorException(
                    String.format("Not able to find band with prefix '%s' and wavelength '%4.3f'.",
                                  reflectancesPrefix, wavelength));
        }
        return bestBandName;
    }


    private static boolean mustDefineTargetSample(Band band) {
        return !band.isSourceImageSet();
    }

    private boolean areSourceSamplesValid(int x, int y, Sample[] sourceSamples) {
        if (!sourceProduct.containsPixel(x, y)) {
            return false;
        }
        for (Sample sourceSample : sourceSamples) {
            if (!sourceSample.getNode().isPixelValid(x, y)) {
                //System.out.println("Rejected invalid pixel: x,y = " + x + "," + y);
                return false;
            }
            if (Double.isNaN(sourceSample.getDouble())) {
                //System.out.println("Rejected NaN pixel #" + nanPixelCount + ": x,y = " + x + "," + y);
                nanPixelCount++;
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
