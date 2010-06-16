package org.esa.beam.coastcolour.util;

import com.bc.ceres.core.PrintWriterProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class WorldQuickLookGeneratorMain {

    public static void main(String[] args) {
        if (args.length == 3) {
            final String worldImagePath = args[0];
            final String sourceDirPath = args[1];
            final String quickLookImagePath = args[2];

            final File worldImageFile = new File(worldImagePath);
            final File sourceDir = new File(sourceDirPath);
            final File quickLookImageFile = new File(quickLookImagePath);


            execute(worldImageFile, sourceDir, quickLookImageFile, new DefaultErrorHandler(),
                    new PrintWriterProgressMonitor(System.out));
        } else {
            printUsage();
        }
    }

    private static void execute(File worldImageFile, File sourceDir, File quickLookImageFile, ErrorHandler handler,
                                ProgressMonitor pm) {
        final File[] sourceFiles = sourceDir.listFiles();
        try {
            pm.beginTask("Generating world quick-look image...", sourceFiles.length);
            final BufferedImage worldImage = ImageIO.read(worldImageFile);

            final WorldQuickLookGenerator generator = new WorldQuickLookGenerator();
            for (final File file : sourceFiles) {
                Product product = null;
                try {
                    product = ProductIO.readProduct(file);
                    if (product != null) {
                        generator.addProduct(product);
                    }
                    pm.worked(1);
                } catch (IOException e) {
                    handler.warning(e);
                } finally {
                    if (product != null) {
                        product.dispose();
                    }
                }
            }

            final BufferedImage quickLookImage = generator.createQuickLookImage(worldImage);
            ImageIO.write(quickLookImage, "jpg", quickLookImageFile);
        } catch (IOException e) {
            handler.error(e);
        } finally {
            pm.done();
        }
    }

    private static void printUsage() {
        System.out.println("COASTCOLOUR product directory quick-look tool, version 1.0");
        System.out.println("May 11, 2010");
        System.out.println();
        System.out.println("usage : wql.sh WORLD SOURCE TARGET");
        System.out.println();
        System.out.println();
        System.out.println("WORLD\n" +
                           "\n" +
                           "    The path of the backgound world image.");
        System.out.println();
        System.out.println();
        System.out.println("SOURCE\n" +
                           "\n" +
                           "    The path of the source product directory.");
        System.out.println();
        System.out.println();
        System.out.println("TARGET\n" +
                           "\n" +
                           "    The path of the file where the quick-look image shall be stored.");
        System.out.println();
        System.out.println();
    }
}
