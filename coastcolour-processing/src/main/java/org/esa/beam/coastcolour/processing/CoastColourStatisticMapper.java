package org.esa.beam.coastcolour.processing;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.beam.BeamUtils;
import com.bc.calvalus.processing.shellexec.ProcessorException;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glayer.CollectionLayer;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.support.ImageLayer;
import com.bc.ceres.grender.support.BufferedImageRendering;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.esa.beam.coastcolour.util.WorldQuickLookGenerator;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RGBImageProfile;
import org.esa.beam.framework.datamodel.Stx;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.glayer.MaskLayerType;
import org.esa.beam.glevel.BandImageMultiLevelSource;
import org.esa.beam.util.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation used to generate statistics, RGB images and world map for CC L1P products
 */
public class CoastColourStatisticMapper
        extends Mapper<NullWritable, NullWritable, Text /*product name*/, Text /*stx*/> {

    // MERIS L1B Tristimulus RGB Profile
    private static final String RED_EXPRESSION = "log(1.0 + 0.35 * radiance_2 + 0.60 * radiance_5 + radiance_6 + 0.13 * radiance_7)";
    private static final String GREEN_EXPRESSION = "log(1.0 + 0.21 * radiance_3 + 0.50 * radiance_4 + radiance_5 + 0.38 * radiance_6)";
    private static final String BLUE_EXPRESSION = "log(1.0 + 0.21 * radiance_1 + 1.75 * radiance_2 + 0.47 * radiance_3 + 0.16 * radiance_4)";
    private static final Logger LOG = CalvalusLogger.getLogger();


    public static void main(String[] args) throws IOException {
        BeamUtils.initGpf(new Configuration());
        File productFile = new File(args[0]);
        Product product = ProductIO.readProduct(productFile);

        String productName = createProductName(productFile.getName());

        FileOutputStream quicklookOutputStream = new FileOutputStream(productName + "_QL.png");
        FileOutputStream worldMapOutputStream = new FileOutputStream(productName + "_WM.png");
        String statisticalData;
        try {
            statisticalData = createStatisticalData(product, quicklookOutputStream, worldMapOutputStream);
        } finally {
            quicklookOutputStream.close();
            worldMapOutputStream.close();
        }
        System.out.println(productName + "\t" + statisticalData);
    }

    private static String createProductName(String productFileName) {
        String inputFilename = FileUtils.getFilenameWithoutExtension(productFileName);
        inputFilename = "MER_FSG_CCL1P_" + inputFilename.substring(20);
        return inputFilename;
    }

    @Override
    public void run(Context context) throws IOException, InterruptedException, ProcessorException {
        BeamUtils.initGpf(context.getConfiguration());

        final FileSplit split = (FileSplit) context.getInputSplit();

        // write initial log entry for runtime measurements
        LOG.info(context.getTaskAttemptID() + " starts processing of split " + split);
        final long startTime = System.nanoTime();

        try {
            final Path inputPath = split.getPath();

            // parse request
            Configuration configuration = context.getConfiguration();

            // set up input reader
            Product sourceProduct = BeamUtils.readProduct(inputPath, configuration);
            String productName = createProductName(inputPath.getName());
            OutputStream quicklookOutputStream = createPngOutputStream(productName + "_QL", context);
            OutputStream worldMapOutputStream = createPngOutputStream(productName + "_WM", context);

            String statString = createStatisticalData(sourceProduct, quicklookOutputStream, worldMapOutputStream);

            context.write(new Text(productName), new Text(statString));


        } catch (Exception e) {
            LOG.log(Level.SEVERE, "CoastColourStatisticMapper exception: " + e.toString(), e);
            throw new ProcessorException("CoastColourStatisticMapper exception: " + e.toString(), e);
        } finally {
            // write final log entry for runtime measurements
            final long stopTime = System.nanoTime();
            LOG.info(
                    context.getTaskAttemptID() + " stops processing of split " + split + " after " + ((stopTime - startTime) / 1.0E9) + " sec");
        }
    }

    private static String createStatisticalData(Product sourceProduct, OutputStream quicklookOutputStream,
                                                OutputStream worldMapOutputStream) throws IOException {
        String statString = doStatisticsExtraction(sourceProduct);

        // create RGB image
        createQuicklookImage(sourceProduct, quicklookOutputStream);

        // create world map image
        createWorldMapImage(sourceProduct, worldMapOutputStream);
        return statString;
    }

    private static String doStatisticsExtraction(Product sourceProduct) {
        int width = sourceProduct.getSceneRasterWidth();
        int height = sourceProduct.getSceneRasterHeight();

        Band rasterWithInvalidMask = sourceProduct.getBand("radiance_5");
        Band rasterWithoutInvalidMask = sourceProduct.getBand("l1_flags");
        Mask invalidMask = sourceProduct.getMaskGroup().get("l1b_invalid");
        Mask cloudMask = sourceProduct.getMaskGroup().get("l1p_cc_cloud");
        Mask waterMask = Mask.BandMathsType.create("temp_water", "water", width, height,
                                                   "!l1p_flags.CC_LAND && !l1p_flags.CC_COASTLINE", Color.BLUE, 0.5f);
        sourceProduct.getMaskGroup().add(waterMask);
        Mask cloudOverWaterMask = Mask.BandMathsType.create("temp_cloud_water", "cloud_water", width, height,
                                                            "l1p_flags.CC_CLOUD && !l1p_flags.CC_LAND && !l1p_flags.CC_COASTLINE",
                                                            Color.BLUE,
                                                            0.5f);
        sourceProduct.getMaskGroup().add(cloudOverWaterMask);
        Stx invalidStx = Stx.create(rasterWithoutInvalidMask, invalidMask, ProgressMonitor.NULL);
        Stx cloudStx = Stx.create(rasterWithInvalidMask, cloudMask, ProgressMonitor.NULL);
        Stx cloudOverWaterStx = Stx.create(rasterWithInvalidMask, cloudOverWaterMask, ProgressMonitor.NULL);
        Stx waterStx = Stx.create(rasterWithInvalidMask, waterMask, ProgressMonitor.NULL);
        double numInvalidPixels = invalidStx.getSampleCount();
        double numCloudPixels = cloudStx.getSampleCount();
        double numCloudOverWaterPixels = cloudOverWaterStx.getSampleCount();
        double numWaterPixels = waterStx.getSampleCount();
        double numAllPixels = width * height;

        double invalidAllRatio = numInvalidPixels / numAllPixels;
        double cloudAllRation = numCloudPixels / numAllPixels;
        double waterAllRation = numWaterPixels / numAllPixels;
        double cloudWaterRatio = numCloudOverWaterPixels / numWaterPixels;

        return String.format(
                "width:\t%d\theight:\t%d\tinvalid:\t%f%%\tcloud:\t%f%%\twater:\t%f%%\tcloudOverWater:\t%f%%",
                width, height, invalidAllRatio * 100, cloudAllRation * 100, waterAllRation * 100,
                cloudWaterRatio * 100);
    }

    private static void createWorldMapImage(Product sourceProduct, OutputStream outputStream) throws IOException {
        BufferedImage sourceWorldMap = ImageIO.read(
                CoastColourStatisticMapper.class.getResourceAsStream("worldMap.png"));

        Color lineColor = new Color(255, 0, 0);
        Color fillColor = new Color(255, 255, 255, 150);
        WorldQuickLookGenerator worldGenerator = new WorldQuickLookGenerator(lineColor, fillColor);
        worldGenerator.addProduct(sourceProduct);
        BufferedImage worldMap = worldGenerator.createQuickLookImage(sourceWorldMap);
        ImageIO.write(worldMap, "png", outputStream);
    }

    private static void createQuicklookImage(Product sourceProduct, OutputStream outputStream) throws IOException {
        RenderedImage rgbImage = createRGBImage(sourceProduct, RED_EXPRESSION, GREEN_EXPRESSION, BLUE_EXPRESSION, "");
        ImageIO.write(rgbImage, "png", outputStream);
    }

    private OutputStream createPngOutputStream(String inputFilename, Context context) throws IOException,
                                                                                             InterruptedException {
        Path workOutputPath = FileOutputFormat.getWorkOutputPath(context);
        String quicklookFilename = FileUtils.exchangeExtension(inputFilename, ".png");
        final Path outputProductPath = new Path(workOutputPath, quicklookFilename);
        FileSystem fileSystem = FileSystem.get(context.getConfiguration());
        return fileSystem.create(outputProductPath);
    }

    static RenderedImage createRGBImage(Product product, String... RGBExpressions) {
        Map<String, Object> subsetParams = new HashMap<String, Object>();
        subsetParams.put("subSamplingX", 4);
        subsetParams.put("subSamplingY", 4);
        product = GPF.createProduct("Subset", subsetParams, product);
        RGBImageProfile.storeRgbaExpressions(product, RGBExpressions);
        final Band[] rgbBands = {
                product.getBand(RGBImageProfile.RED_BAND_NAME),
                product.getBand(RGBImageProfile.GREEN_BAND_NAME),
                product.getBand(RGBImageProfile.BLUE_BAND_NAME),
        };
        for (Band rgbBand : rgbBands) {
            rgbBand.setValidPixelExpression("!l1_flags.INVALID");
        }
        final ImageLayer imageLayer = new ImageLayer(BandImageMultiLevelSource.create(rgbBands, ProgressMonitor.NULL));
        CollectionLayer collectionLayer = new CollectionLayer();
        collectionLayer.getChildren().add(imageLayer);
        Layer landMask = MaskLayerType.createLayer(rgbBands[0], product.getMaskGroup().get("l1p_cc_land"));
        landMask.setVisible(true);
        Layer coastlineMask = MaskLayerType.createLayer(rgbBands[0], product.getMaskGroup().get("l1p_cc_coastline"));
        coastlineMask.setVisible(true);
        collectionLayer.getChildren().add(0, landMask);
        collectionLayer.getChildren().add(1, coastlineMask);
        BufferedImageRendering rendering = new BufferedImageRendering(product.getSceneRasterWidth(),
                                                                      product.getSceneRasterHeight());
        collectionLayer.render(rendering);
        return rendering.getImage();
    }

}
