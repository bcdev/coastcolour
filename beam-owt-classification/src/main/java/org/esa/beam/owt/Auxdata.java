package org.esa.beam.owt;

public class Auxdata {

    private double[][] spectralMeans;
    private double[][][] invCovarianceMatrices;

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
