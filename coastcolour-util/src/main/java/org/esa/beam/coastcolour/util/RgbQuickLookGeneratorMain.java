package org.esa.beam.coastcolour.util;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class RgbQuickLookGeneratorMain {

    public static void main(String[] args) {
        if (args.length == 3) {
            final String productDirPath = args[0];
            final String quickLookDirPath = args[1];

            final File productDir = new File(productDirPath);
            final File quickLookImageDir = new File(quickLookDirPath);


            execute(productDir, quickLookImageDir, new ErrorHandler() {
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

    private static void execute(File productDir, File quickLookImageDir, ErrorHandler handler) {
        try {
            final RgbQuickLookGenerator generator = new RgbQuickLookGenerator("MERIS L1b - Tristimulus");
            for (final File file : productDir.listFiles()) {
                Product product = null;
                try {
                    product = ProductIO.readProduct(file);
                    if (product != null) {
                        final BufferedImage quickLookImage = generator.createQuickLookImage(product);
                        ImageIO.write(quickLookImage, "jpg", createFile(quickLookImageDir, product));
                    }
                } catch (IOException e) {
                    handler.warning(e);
                } finally {
                    if (product != null) {
                        product.dispose();
                    }
                }
            }
        } catch (Exception e) {
            handler.error(e);
        }
    }

    private static File createFile(File quickLookImageDir, Product product) {
        return new File(quickLookImageDir, FileUtils.exchangeExtension(product.getFileLocation().getName(), ".jpg"));
    }

    private static void printUsage() {
        System.out.println("COASTCOLOUR product directory RGB quick-look tool, version 1.0");
        System.out.println("June 16, 2010");
        System.out.println();
        System.out.println("usage : rgbql.sh SOURCE TARGET");
        System.out.println();
        System.out.println();
        System.out.println("SOURCE\n" +
                           "\n" +
                           "    The path of the source product directory.");
        System.out.println();
        System.out.println();
        System.out.println("TARGET\n" +
                           "\n" +
                           "    The path of the target directory where the quick-look images shall be stored.");
        System.out.println();
        System.out.println();
    }
}
