package org.esa.beam.coastcolour.util;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * Allows to assign the bands of the input product for given wavelength
 *
 * @author Thomas Storm
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "CoastColour.WavelengthMapping",
                  description = "tbd",
                  authors = "Olaf Danne, Thomas Storm (Brockmann Consult)",
                  copyright = "(c) 2012 by Brockmann Consult",
                  version = "0.1")
public class WavelengthMappingOp extends Operator {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @Parameter()
    private File wavelengthMappingFile;


    @Override
    public void initialize() throws OperatorException {

        Properties wavelengthMapping = getWavelengthMapping();
        final Band[] bands = sourceProduct.getBands();
        for (int i = 0; i < bands.length; i++) {
            Band band = bands[i];
            final String property = wavelengthMapping.getProperty(band.getName());
            if (property == null) {
                continue;
            }
            final float wavelength = Float.parseFloat(property);
            band.setSpectralWavelength(wavelength);
            band.setSpectralBandIndex(i);
        }
        setTargetProduct(sourceProduct);
    }

    private Properties getWavelengthMapping() {

        FileReader reader = null;
        Properties configuration;
        try {
            reader = new FileReader(wavelengthMappingFile);
            configuration = new Properties();
            configuration.load(reader);
        } catch (IOException e) {
            throw new OperatorException(e.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ignore) {
            }
        }
        return configuration;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(WavelengthMappingOp.class);
        }
    }
}
