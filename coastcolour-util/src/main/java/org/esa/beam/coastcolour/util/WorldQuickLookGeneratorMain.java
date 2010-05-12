package org.esa.beam.coastcolour.util;

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
            final String productDirPath = args[1];
            final String quickLookImagePath = args[2];

            final File worldImageFile = new File(worldImagePath);
            final File productDir = new File(productDirPath);
            final File quickLookImageFile = new File(quickLookImagePath);


            execute(worldImageFile, productDir, quickLookImageFile, new ErrorHandler() {
                @Override
                public void warning(Throwable t) {
                    t.printStackTrace();
                }

                @Override
                public void error(Throwable t) {
                    t.printStackTrace();
                    System.exit(1);
                }
            });
        } else {
            printUsage();
        }
    }

    private static void execute(File worldImageFile, File productDir, File quickLookImageFile, ErrorHandler handler) {
        try {
            final BufferedImage worldImage = ImageIO.read(worldImageFile);

            final WorldQuickLookGenerator generator = new WorldQuickLookGenerator();
            for (final File file : productDir.listFiles()) {
                Product product = null;
                try {
                    product = ProductIO.readProduct(file);
                    if (product != null) {
                        generator.addProduct(product);
                    }
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
        }
    }

    private static void printUsage() {
        System.out.println("COASTCOLOUR product directory quick-look tool, version 1.0");
        System.out.println("May 11, 2010");
        System.out.println();
        System.out.println("usage : quicklook.sh WORLD SOURCE TARGET");
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

    private interface ErrorHandler {

        void warning(final Throwable t);

        void error(final Throwable t);
    }
}
