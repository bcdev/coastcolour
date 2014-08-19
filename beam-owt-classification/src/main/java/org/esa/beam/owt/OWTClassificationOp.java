package org.esa.beam.owt;

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

// todo 1 - (mp;28.02.2014) discuss with CB,KS,AR if sum bands can be removed. Have no additional use to the user. At least the norm_class_sum band
// todo 2 - (mp;22.11.2010) convertToSubsurfaceWaterRrs should be configurable; should be discussed with CB,KS,AR
// todo 3 - (mp;28.02.2014) description should be checked by CB,KS,AR
// todo 4 - (mp;10.03.2014) help is missing
// todo 5 - (cb;15.03.2014) provide a text field to enter a "valid pixel expression".
// todo   -                 Currently the OWT is calculated everywhere, including land and clouds.
// todo   -                 If a valid pixel expression would be provided the calculation would be restricted to those pixels

@OperatorMetadata(alias = "OWTClassification",
                  description = "Performs an optical water type classification based on atmospherically corrected reflectances.",
                  authors = "Timothy Moore (University of New Hampshire); Marco Peters, Thomas Storm (Brockmann Consult)",
                  copyright = "(c) 2014 by Timothy Moore (University of New Hampshire) and Brockmann Consult",
                  version = "1.3",
                  internal = true)
public class OWTClassificationOp extends PixelOperator {

    private static final int DOMINANT_CLASS_NO_DATA_VALUE = -1;
    private static final int CLASS_SUM_NO_DATA_VALUE = -1;

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @Parameter(defaultValue = "COASTAL")
    private OWT_TYPE owtType;

    @Parameter(defaultValue = "reflec")
    private String reflectancesPrefix;

    @Parameter(defaultValue = "false")
    private boolean writeInputReflectances;

    @Parameter(defaultValue = "RADIANCE_REFLECTANCES")
    private ReflectanceEnum inputReflectanceIs;

    private OWTClassification owtClassification;
    private Auxdata auxdata;


    @Override
    protected void prepareInputs() throws OperatorException {
        super.prepareInputs();

        if (sourceProduct.getDescription() != null &&
            sourceProduct.getDescription().contains("IRRADIANCE_REFLECTANCES")) {
            // overwrite user option (only for CC L2R case so far)
            inputReflectanceIs = ReflectanceEnum.IRRADIANCE_REFLECTANCES;
        }
    }

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

        addClassBands("class_", targetProduct);
        addClassBands("norm_class_", targetProduct);

        final Band domClassBand = targetProduct.addBand("dominant_class", ProductData.TYPE_INT8);
        domClassBand.setNoDataValue(DOMINANT_CLASS_NO_DATA_VALUE);
        domClassBand.setNoDataValueUsed(true);
        final IndexCoding indexCoding = new IndexCoding("Dominant_Classes");
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
            if (owtType.mustNormalizeSpectra()) {
                for (float wavelength : wavelengths) {
                    final String sourceBandName = getSourceBandName(reflectancesPrefix, wavelength);
                    final String targetBandName = "norm_" + sourceBandName;
                    ProductUtils.copyBand(sourceBandName, sourceProduct, targetBandName, targetProduct, false);
                }
            }
        }

        targetProduct.setAutoGrouping("reflec:norm_reflec:class:norm_class");
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
    }

    @Override
    protected void configureSourceSamples(SampleConfigurer sampleConfigurer) throws OperatorException {
        owtClassification = new OWTClassification(auxdata.getSpectralMeans(),
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

        int numClassSamples = owtType.getClassCount() * 2; // classes and norm_classes
        if (!areSourceSamplesValid(x, y, sourceSamples)) {
            setTargetSamplesToInvalid(targetSamples, numClassSamples);
            return;
        }

        double[] rrsBelowWater = new double[numWLs];
        for (int i = 0; i < numWLs; i++) {
            rrsBelowWater[i] = convertToSubsurfaceWaterRrs(sourceSamples[i].getDouble());
            if (inputReflectanceIs == ReflectanceEnum.IRRADIANCE_REFLECTANCES) {
                // if input comes as IRRADIANCE_REFLECTANCES, convert to remote sensing reflectances,
                // which is the same as 'RADIANCE REFLECTANCES'. Remember: IRRAD_REFL = RAD_REFL * PI
                rrsBelowWater[i] /= Math.PI;
            }
        }

        if (owtType.mustNormalizeSpectra()) {
            normalizeSpectra(rrsBelowWater);
        }

        double[] classMemberships;
        try {
            classMemberships = owtClassification.computeClassMemberships(rrsBelowWater);
        } catch (OWTException e) {
            setTargetSamplesToInvalid(targetSamples, numClassSamples);
            return;
        }
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
        int dominantClass = DOMINANT_CLASS_NO_DATA_VALUE;
        double dominantClassValue = Double.MIN_VALUE;
        double classSum = 0.0;
        double normalizedClassSum = 0.0;
        for (int i = 0; i < owtType.getClassCount(); i++) {
            final double currentClassValue = targetSamples[i].getDouble();
            if (currentClassValue > dominantClassValue) {
                dominantClassValue = currentClassValue;
                dominantClass = i + 1;
            }
            classSum += currentClassValue;
            normalizedClassSum += targetSamples[owtType.getClassCount() + i].getDouble();
        }
        targetSamples[numClassSamples].set(dominantClass);
        targetSamples[numClassSamples + 1].set(classSum);
        targetSamples[numClassSamples + 2].set(normalizedClassSum);

        if (writeInputReflectances && owtType.mustNormalizeSpectra()) {
            for (int i = 0; i < rrsBelowWater.length; i++) {
                targetSamples[numClassSamples + 3 + i].set(rrsBelowWater[i]);
            }
        }

    }

    private void setTargetSamplesToInvalid(WritableSample[] targetSamples, int numClassSamples) {
        for (int i = 0; i < numClassSamples; i++) {
            targetSamples[i].set(Double.NaN);  // classes and norm_classes
        }
        targetSamples[numClassSamples].set(DOMINANT_CLASS_NO_DATA_VALUE); // dominant_class
        targetSamples[numClassSamples + 1].set(CLASS_SUM_NO_DATA_VALUE); // class_sum
        targetSamples[numClassSamples + 2].set(Double.NaN); // norm_class_sum
    }

    static double trapz(double[] x, double[] y) {
        double sum = 0.0;
        for (int i = 1; i < x.length; i++) {
            sum += 0.5 * (x[i] - x[i - 1]) * (y[i] + y[i - 1]);
        }
        return sum;
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

    private void normalizeSpectra(double[] rrsBelowWater) {
        double wls[] = new double[owtType.getWavelengths().length];
        for (int i = 0; i < wls.length; i++) {
            wls[i] = owtType.getWavelengths()[i];
        }

        double integral = trapz(wls, rrsBelowWater);

        for (int i = 0; i < rrsBelowWater.length; i++) {
            rrsBelowWater[i] /= integral;
        }
    }

    private void addClassBands(String bandNamePrefix, Product targetProduct) {
        for (int i = 1; i <= owtType.getClassCount(); i++) {
            final Band classBand = targetProduct.addBand(bandNamePrefix + i, ProductData.TYPE_FLOAT32);
            classBand.setValidPixelExpression(classBand.getName() + " > 0.0");
        }
    }

    private String getSourceBandName(String reflectancesPrefix, float wavelength) {
        final Band[] bands = sourceProduct.getBands();
        String bestBandName = getBestBandName(reflectancesPrefix, wavelength, bands);
        if (bestBandName == null) {
            throw new OperatorException(
                    String.format("Not able to find band with prefix '%s' and wavelength '%4.3f'.",
                                  reflectancesPrefix, wavelength)
            );
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
                return false;
            }
            if (Double.isNaN(sourceSample.getDouble())) {
                return false;
            }
        }
        return true;
    }

    // todo 2
    private double convertToSubsurfaceWaterRrs(double merisL2Reflec) {
        // convert to remote sensing reflectances
        final double rrsAboveWater = merisL2Reflec / Math.PI;
        // convert to subsurface water remote sensing reflectances
        return rrsAboveWater / (0.52 + 1.7 * rrsAboveWater);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(OWTClassificationOp.class);
        }
    }
}
