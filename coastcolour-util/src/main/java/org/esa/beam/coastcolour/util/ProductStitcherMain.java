package org.esa.beam.coastcolour.util;

import org.apache.commons.cli.*;
import org.esa.beam.util.logging.BeamLogManager;
import ucar.nc2.NetcdfFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Main class for stitching coastcolour output products.
 * The coastcolour products must be in netCDF format and MUST follow the ENVISAT/MERIS file naming convention, except for:
 *  - MER_XXX_YZ is replaced by either MER_XXX_CCL1P, MER_XXX_CCL2R, or MER_XXX_CCL2W
 *  - file extension .nc (netCDF) instead of .N1
 *  --> a valid file name is e.g. 'MER_FRS_CCL2W_20120309_074759_000001893112_00265_52434_0001.nc'
 *
 * @author olafd
 */
public class ProductStitcherMain {

    public static final String TOOL_NAME = "cc_stitcher";

    public static final File DEFAULT_OUTPUT_DIR = new File(".");
    public static final Option OPT_OUTPUT_DIR = OptionBuilder
            .hasArg()
            .withArgName("OUTDIR")
            .withLongOpt("output-dir")
            .withDescription("The stitch output directory path (default is current directory).")
            .create("o");
    public static final Option OPT_HELP = OptionBuilder
            .withLongOpt("help")
            .withDescription("Prints out this usage help.")
            .create();

    public static final String DEFAULT_LOGFILE_NAME = "cc-stitch-log.txt";

    private Options options;

    private String[] sourceFilePaths;
    private File outputDir;

    private Logger logger;
    private File logFile;

    public static void main(String[] args) {
        final ProductStitcherMain productStitcherMain = new ProductStitcherMain(args);
        productStitcherMain.execute();
    }

    public ProductStitcherMain(String[] args) {
        CommandLine commandLine = null;

        initLogger();

        options = createCommandLineOptions();

        try {
            commandLine = parseCommandLine(args);
        } catch (ParseException e) {
            logger.log(Level.SEVERE, "Error: " + e.getMessage() + " (use option '-h' for help)");
            System.exit(-1);
        }

        if (commandLine.hasOption("help")) {
            printHelp();
            System.exit(0);
        }

        extractCommandLineInput(commandLine);
        sourceFilePaths = commandLine.getArgs();
        try {
            ProductStitcher.validateSourceProducts(sourceFilePaths);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error: " + e.getMessage() + " (use option '-h' for help)");
            System.exit(-1);
        }
    }

    private void printHelp() {
        HelpFormatter helpFormatter = new HelpFormatter();
        String argString = TOOL_NAME + " ncFilePath1 [ncFilePath_2 ... ncFilePath_n]";
        helpFormatter.printHelp(argString, options, true);
    }

    private void initLogger() {
        logger = BeamLogManager.getSystemLogger();
        logger.setLevel(Level.INFO);
        final FileHandler fileTxt;
        logFile = new File(System.getProperty("user.home") + File.separator + DEFAULT_LOGFILE_NAME);
        try {
            fileTxt = new FileHandler(logFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Problems with creating the log file - cannot continue.");
        }

        // Create txt Formatter
        SimpleFormatter formatterTxt = new SimpleFormatter();
        fileTxt.setFormatter(formatterTxt);
        logger.addHandler(fileTxt);
        logger.setLevel(Level.INFO);
    }


    @SuppressWarnings({"AccessStaticViaInstance"})
    static Options createCommandLineOptions() {
        Options options = new Options();

        // argument options
        options.addOption(OPT_OUTPUT_DIR);
        options.addOption(OPT_HELP);

        return options;
    }

    public CommandLine parseCommandLine(String... args) throws ParseException {
        Parser parser = new GnuParser();
        return parser.parse(options, args);
    }

    private void extractCommandLineInput(CommandLine cl) {
        setOutputDir(cl);
    }

    private void setOutputDir(CommandLine cl) {
        outputDir = DEFAULT_OUTPUT_DIR;
        if (cl.hasOption(OPT_OUTPUT_DIR.getOpt())) {
            outputDir = new File(cl.getOptionValue(OPT_OUTPUT_DIR.getOpt()));
        }
        if (!outputDir.isDirectory()) {
            logger.log(Level.SEVERE, "Error: The given output directory '" + outputDir.getPath() + "' is not a directory.");
            System.exit(1);
        }
    }

    private void execute() {
        List<NetcdfFile> ncFileList = ProductStitcherNetcdfUtils.getSourceProductSetsToStitch(sourceFilePaths);
        final String stitchProductFileName = ProductStitcherNetcdfUtils.getStitchedProductFileName(sourceFilePaths);
        try {
            final long t1 = System.currentTimeMillis();
            ProductStitcher stitcher = new ProductStitcher(ncFileList, logger);
            final File stitchProductFile = new File(outputDir + File.separator + stitchProductFileName);
            stitcher.writeStitchedProduct(stitchProductFile);
            final long t2 = System.currentTimeMillis();
            logger.log(Level.INFO, "Processing time: " + (t2 - t1) / 1000 + " seconds.");
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
