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
            final String rgbFilePath = args[0];
            final String sourceDirPath = args[1];
            final String targetDirPath = args[2];

            final File rgbFile = new File(rgbFilePath);
            final File sourceDir = new File(sourceDirPath);
            final File targetDir = new File(targetDirPath);

            execute(rgbFile, sourceDir, targetDir, new DefaultErrorHandler());
        } else {
            printUsage();
        }
    }

    private static void execute(File rgbFile, File sourceDir, File targetDir, ErrorHandler handler) {
        try {
            final RgbQuickLookGenerator generator = new RgbQuickLookGenerator(rgbFile);
            for (final File file : sourceDir.listFiles()) {
                Product product = null;
                try {
                    product = ProductIO.readProduct(file);
                    if (product != null && generator.isApplicableTo(product)) {
                        final BufferedImage image = generator.createQuickLookImage(product);
                        ImageIO.write(image, "jpg", createImageFile(targetDir, product));
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

    private static File createImageFile(File targetDir, Product product) {
        return new File(targetDir, FileUtils.exchangeExtension(product.getFileLocation().getName(), ".jpg"));
    }

    private static void printUsage() {
        System.out.println("COASTCOLOUR product directory RGB quick-look tool, version 1.0");
        System.out.println("June 16, 2010");
        System.out.println();
        System.out.println("usage : rgbql.sh RGB SOURCE TARGET");
        System.out.println();
        System.out.println();
        System.out.println("RGB\n" +
                           "\n" +
                           "    The path of the file containing the RGB image profile.");
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
