package org.esa.beam.owt;

public enum OWT_TYPE {
    COASTAL {
        private float[] wavelength = new float[]{410, 443, 490, 510, 555};

        @Override
        AuxdataFactory getAuxdataFactory() {
            return new CoastalAuxdataFactory("/auxdata/coastal/owt16_meris_stats_101119_5band.hdf");
        }

        @Override
        int getClassCount() {
            return 9;
        }

        @Override
        double[] mapMembershipsToClasses(double[] memberships) {
            double[] classes = new double[getClassCount()];
            System.arraycopy(memberships, 0, classes, 0, 8);
            // setting the value for the 9th class to the sum of the last 8 classes
            for (int i = 8; i < memberships.length; i++) {
                classes[8] += memberships[i];
            }
            return classes;
        }

        @Override
        float[] getWavelengths() {
            return wavelength;
        }
    },
    // exclude this (CB, 20140613):
//    COASTAL_6BAND {
//        private float[] wavelength = new float[]{410, 443, 490, 510, 555, 670};
//
//        @Override
//        AuxdataFactory getAuxdataFactory() {
//            return new CoastalAuxdataFactory("/auxdata/coastal/owt16_meris_stats_101119_6band.hdf");
//        }
//
//        @Override
//        int getClassCount() {
//            return 9;
//        }
//
//        @Override
//        double[] mapMembershipsToClasses(double[] memberships) {
//            double[] classes = new double[getClassCount()];
//            System.arraycopy(memberships, 0, classes, 0, 8);
//            // setting the value for the 9th class to the sum of the last 8 classes
//            for (int i = 8; i < memberships.length; i++) {
//                classes[8] += memberships[i];
//            }
//            return classes;
//        }
//
//        @Override
//        float[] getWavelengths() {
//            return wavelength;
//        }
//    },
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

        @Override
        int getClassCount() {
            return 7;
        }

        @Override
        double[] mapMembershipsToClasses(double[] memberships) {
            return memberships;
        }
    },
    INLAND_NO_BLUE_BAND {

        private float[] wavelength = new float[]{443, 490, 510, 560, 620, 665, 680, 709, 754};

        @Override
        AuxdataFactory getAuxdataFactory() {
            return new InlandAuxdataFactory(wavelength);
        }

        @Override
        float[] getWavelengths() {
            return wavelength;

        }

        @Override
        int getClassCount() {
            return 7;
        }

        @Override
        double[] mapMembershipsToClasses(double[] memberships) {
            return memberships;
        }
    },
    // exclude this (CB, 20140613):
//    INLAND_WITHOUT_443 {
//
//        private float[] wavelength = new float[]{412, 490, 510, 560, 620, 665, 680, 709, 754};
//
//        @Override
//        AuxdataFactory getAuxdataFactory() {
//            return new InlandAuxdataFactory(wavelength);
//        }
//
//        @Override
//        float[] getWavelengths() {
//            return wavelength;
//
//        }
//
//        @Override
//        int getClassCount() {
//            return 7;
//        }
//
//        @Override
//        double[] mapMembershipsToClasses(double[] memberships) {
//            return memberships;
//        }
//    }
    ;

    abstract AuxdataFactory getAuxdataFactory();

    abstract float[] getWavelengths();

    abstract int getClassCount();

    abstract double[] mapMembershipsToClasses(double[] memberships);
}
