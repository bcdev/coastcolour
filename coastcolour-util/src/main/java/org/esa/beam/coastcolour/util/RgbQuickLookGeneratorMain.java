package org.esa.beam.coastcolour.util;

import com.bc.ceres.core.PrintWriterProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.runtime.RuntimeRunnable;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class RgbQuickLookGeneratorMain implements RuntimeRunnable {

    public static void main(String[] args) {
        SystemUtils.init3rdPartyLibs(RgbQuickLookGeneratorMain.class.getClassLoader());

        if (args.length == 3) {
            final String rgbProfile = args[0];
            final String sourceDirPath = args[1];
            final String targetDirPath = args[2];

            final File sourceDir = new File(sourceDirPath);
            final File targetDir = new File(targetDirPath);

            execute(rgbProfile, sourceDir, targetDir, new DefaultErrorHandler(),
                    new PrintWriterProgressMonitor(System.out));
        } else {
            printUsage();
        }
    }

    private static void execute(String rgbProfile, File sourceDir, File targetDir, ErrorHandler handler,
                                ProgressMonitor pm) {
        final File[] sourceFiles = sourceDir.listFiles();
        try {
            pm.beginTask("Generating quick-look images...", sourceFiles.length);
            final RgbQuickLookGenerator generator = new RgbQuickLookGenerator(rgbProfile);
            for (final File file : sourceFiles) {
                Product product = null;
                try {
                    product = ProductIO.readProduct(file);
                    if (product != null && generator.isApplicableTo(product)) {
                        final BufferedImage image = generator.createQuickLookImage(product);
                        ImageIO.write(image, "jpg", createImageFile(targetDir, product));
                    }
                    pm.worked(1);
                } catch (Exception e) {
                    handler.warning(e);
                } finally {
                    if (product != null) {
                        product.dispose();
                    }
                }
            }
        } catch (Exception e) {
            handler.error(e);
        } finally {
            pm.done();
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
                           "    The name or path of the RGB image profile.");
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

    @Override
    public void run(Object argument, ProgressMonitor pm) throws Exception {
        main((String[]) argument);
    }
}
