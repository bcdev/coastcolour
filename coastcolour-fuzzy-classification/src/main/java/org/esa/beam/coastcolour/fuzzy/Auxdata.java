package org.esa.beam.coastcolour.fuzzy;

import ucar.ma2.InvalidRangeException;

import java.io.IOException;
import java.net.URI;

public class Auxdata {

    private double[][] spectralMeans;
    private double[][][] invCovarianceMatrices;

    /**
     * @deprecated Implement a {@link org.esa.beam.coastcolour.fuzzy.AuxdataFactory} instead.
     */
    @Deprecated
    public Auxdata(URI filePath) throws IOException, InvalidRangeException {
        try {
            Auxdata auxdata = new CoastalAuxdataFactory("/auxdata/coastal/owt16_meris_stats_101119_5band.hdf").createAuxdata();
            this.spectralMeans = auxdata.spectralMeans;
            this.invCovarianceMatrices = auxdata.invCovarianceMatrices;
        } catch (AuxdataFactory.Exception e) {
            throw new IllegalStateException("Auxdata could not be initialised", e);
        }
    }

    public Auxdata(double[][] spectralMeans, double[][][] invCovarianceMatrices) {
        this.spectralMeans = spectralMeans;
        this.invCovarianceMatrices = invCovarianceMatrices;
    }

    public double[][] getSpectralMeans() {
        return spectralMeans;
    }


    public double[][][] getInvertedCovarianceMatrices() {
        return invCovarianceMatrices;
    }

}
