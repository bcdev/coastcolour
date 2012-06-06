package org.esa.beam.coastcolour.util;

import com.bc.ceres.core.PrintWriterProgressMonitor;
import org.esa.beam.util.logging.BeamLogManager;
import ucar.nc2.NetcdfFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class for stitching coastcolour output products.
 *
 * @author olafd
 */
public class ProductStitcherMain {

    public static void main(String[] args) {
        if (args.length == 3) {
            // 1. the config file listing the products to stitch
            final String configFilePath = args[0];
            // 2. the directory containing the source products
            final String sourceProductDirPath = args[1];
            // 3. the path of the result product
            final String stitchProductFilePath = args[2];

            final File configFile = new File(configFilePath);
            final File sourceProductDir = new File(sourceProductDirPath);
            final File stitchProductFile = new File(stitchProductFilePath);

            execute(configFile, sourceProductDir, stitchProductFile);
        } else {
            printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("COASTCOLOUR product stitching tool, version 1.0");
        System.out.println("June 06, 2012");
        System.out.println();
        System.out.println("usage : stitch.sh CONFIG SOURCE TARGET");
        System.out.println();
        System.out.println("CONFIG\n" +
                                   "    The path of the config file which lists the source products to stitch.");
        System.out.println("SOURCE\n" +
                                   "    The path of the source product directory.");
        System.out.println("TARGET\n" +
                                   "    The path of the stitched product target file.");
        System.out.println();
    }

    private static void execute(File configFile,
                                File sourceProductDir,
                                File stitchProductFile) {
        Logger.getAnonymousLogger().log(Level.INFO, "'\n" + "configFile: '" + configFile.getName() + "'\n" +
                "sourceProductDir: '" + sourceProductDir.getAbsolutePath() + "'\n" +
                "stitchProductFile: '" + stitchProductFile.getAbsolutePath() + "'\n");

        // todo: check products for unique orbit number
        // todo: sort products by their start and end times
        List<NetcdfFile> ncFileList = ProductStitcherNetcdfUtils.getSortedAndValidatedInputProducts(configFile, sourceProductDir);

        try {
            final long t1 = System.currentTimeMillis();
            ProductStitcher stitcher = new ProductStitcher(ncFileList);
            stitcher.writeStitchedProduct(stitchProductFile);
            final long t2 = System.currentTimeMillis();
            System.out.println("Processing time: " + (t2-t1)/1000 + " seconds.");
        } finally {
            for (NetcdfFile netcdfFile : ncFileList) {
                try {
                    netcdfFile.close();
                } catch (IOException ignore) {
                }
            }
        }
    }
}
