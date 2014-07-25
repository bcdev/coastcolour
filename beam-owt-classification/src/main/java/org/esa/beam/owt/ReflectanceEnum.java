package org.esa.beam.owt;

/**
 * Used for specifying the output of reflectances.
 * The output can be either radiance reflectances or irradiance reflectances.
 * This is the same as the ReflectanceEnum in the coastcolour-processing module.
 *
 * @author olafd
 */
public enum ReflectanceEnum {
    RADIANCE_REFLECTANCES,
    IRRADIANCE_REFLECTANCES {
        @Override
        public String toString() {
            return super.toString() + " (ESA compatible)";
        }
    }
}
