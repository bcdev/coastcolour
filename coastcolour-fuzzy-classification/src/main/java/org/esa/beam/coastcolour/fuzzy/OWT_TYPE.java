package org.esa.beam.coastcolour.fuzzy;

/**
 * @author Marco Peters
 */
public enum OWT_TYPE {
    COASTAL {
        private float[] wavelength = new float[]{410, 443, 490, 510, 555};

        @Override
        AuxdataFactory getAuxdataFactory() {
            return new CoastalAuxdataFactory();
        }

        @Override
        float[] getWavelengths() {
            return wavelength;
        }
    },
    INLAND {

        private float[] wavelength = new float[]{412, 443, 490, 510, 560, 620, 665, 680, 709, 754};

        @Override
        AuxdataFactory getAuxdataFactory() {
            return new InlandAuxdataFactory(wavelength);
        }

        @Override
        float[] getWavelengths() {
            return wavelength;

        }
    };

    abstract AuxdataFactory getAuxdataFactory();

    abstract float[] getWavelengths();
}
