package org.esa.beam.coastcolour.util;

import org.esa.beam.util.logging.BeamLogManager;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.Section;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

/**
 * Netcdf utility class for CC product stitcher
 *
 * @author olafd
 */
public class ProductStitcherNetcdfUtils {

    public static final String DATE_PATTERN = "dd-MMM-yyyy HH:mm:ss";

    /**
     * Gets the list of netCDF files from given array of source file paths
     *
     * @param sourceFilePaths - the source file paths
     * @return the list of netCDF files to stitch
     */
    static List<NetcdfFile> getSourceProductSetsToStitch(String[] sourceFilePaths) throws IOException {
        Arrays.sort(sourceFilePaths);

        List<NetcdfFile> ncProducts = new ArrayList<NetcdfFile>();
        for (String sourceFilePath : sourceFilePaths) {
            final NetcdfFile ncFile = NetcdfFile.open(sourceFilePath);
            ncProducts.add(ncFile);
        }

        return ncProducts;
    }

    /**
     * Sets up filename of stitched product: just computes the acquisition time for the "stitch interval" and
     * replaces this in the filename of first product
     *
     * @param ncFileListGroup - the list of netcdf files
     * @return the filename of the stitched product
     */
    public static String getStitchedProductFileName(List<NetcdfFile> ncFileListGroup) {
        String[] sourcePaths = new String[ncFileListGroup.size()];
        int index = 0;
        for (NetcdfFile netcdfFile : ncFileListGroup) {
            sourcePaths[index++] = netcdfFile.getLocation();
        }
        return getStitchedProductFileName(sourcePaths);
    }

    /**
     * Sets up filename of stitched product: just computes the acquisition time for the "stitch interval" and
     * replaces this in the filename of first product
     *
     * @param sourcePaths - the source file paths
     * @return the filename of the stitched product
     */
    static String getStitchedProductFileName(String[] sourcePaths) {
        // e.g. from product set
        //      MER_FRS_CCL2W_20120309_074740_000000563112_00265_52434_0001.nc
        //      MER_FRS_CCL2W_20120309_074759_000001893112_00265_52434_0001.nc
        //      MER_FRS_CCL2W_20120309_075032_000000873112_00265_52434_0001.nc
        // - compute acquisition length in seconds (first 8 digits in '000000563112'):
        //   --> diff. between 'start time + acq. of last product' and 'start time of first product' (here '20120309_074740')
        //   --> filename shall be 'MER_FRS_CCL2W_20120309_074740_000002593112_00265_52434_0001_STITCHED.nc   (172+87=259 secs)

        Arrays.sort(sourcePaths);
        final File firstFile = new File(sourcePaths[0]);
        final String firstFileName = firstFile.getName();
        final File lastFile = new File(sourcePaths[sourcePaths.length - 1]);
        final String lastFileName = lastFile.getName();

        final int firstDate = Integer.parseInt(firstFileName.substring(14, 22));
        final int lastDate = Integer.parseInt(lastFileName.substring(14, 22));
        final int firstHours = Integer.parseInt(firstFileName.substring(23, 25));
        final int lastHours = Integer.parseInt(lastFileName.substring(23, 25));
        final int firstMins = Integer.parseInt(firstFileName.substring(25, 27));
        final int lastMins = Integer.parseInt(lastFileName.substring(25, 27));
        final int firstSecs = Integer.parseInt(firstFileName.substring(27, 29));
        final int lastSecs = Integer.parseInt(lastFileName.substring(27, 29));
        final int lastAcquisitionTime = Integer.parseInt(lastFileName.substring(30, 38));

        final int firstFileSecondsInDay = firstHours * 3600 + firstMins * 60 + firstSecs;
        final int lastFileSecondsInDay = lastHours * 3600 + lastMins * 60 + lastSecs;
        final int dayDiffInSeconds = (lastDate - firstDate) * 86400;

        final int stitchAcquisitionTime = dayDiffInSeconds + (lastFileSecondsInDay - firstFileSecondsInDay) + lastAcquisitionTime;

        final String stitchAcquisitionTimeString = String.format("%08d", stitchAcquisitionTime);
        return (firstFileName.substring(0, 30) + stitchAcquisitionTimeString + firstFileName.substring(38));
    }

    static float[][] getFloat2DArrayFromNetcdfVariable(Variable variable) throws IOException {
        final Array arrayFloat = getDataArray(DataType.FLOAT, variable, Float.class);
        return (float[][]) arrayFloat.copyToNDJavaArray();
    }

    static short[][] getShort2DArrayFromNetcdfVariable(Variable variable) throws IOException {
        final Array arrayShort = getDataArray(DataType.SHORT, variable, Short.class);
        return (short[][]) arrayShort.copyToNDJavaArray();
    }

    static byte[][] getByte2DArrayFromNetcdfVariable(Variable variable) throws IOException {
        final Array arrayByte = getDataArray(DataType.BYTE, variable, Byte.class);
        return (byte[][]) arrayByte.copyToNDJavaArray();
    }

    static byte getByte0DArrayFromNetcdfVariable(Variable variable) throws IOException {
        final Array arrayByte = getDataArray(DataType.BYTE, variable, Byte.class);
        return arrayByte.getByte(0);
    }

    static long parse(String text, String pattern) throws ParseException {
        if (text == null) {
            throw new IllegalArgumentException("parse: text is null");
        }
        if (pattern == null) {
            throw new IllegalArgumentException("parse: pattern is null");
        }

        final int dotPos = text.lastIndexOf(".");
        String noFractionString = text;
        long micros = 0;
        if (dotPos > 0) {
            noFractionString = text.substring(0, dotPos);
            final String fractionString = text.substring(dotPos + 1, text.length());
            if (fractionString.length() > 6) { // max. 6 digits!
                throw new ParseException("Unparseable date:" + text, dotPos);
            }
            try {
                micros = Integer.parseInt(fractionString);
            } catch (NumberFormatException e) {
                throw new ParseException("Unparseable date:" + text, dotPos);
            }
            for (int i = fractionString.length(); i < 6; i++) {
                micros *= 10;
            }
        }

        final DateFormat dateFormat = createDateFormat(pattern);
        final Date date = dateFormat.parse(noFractionString);
        return create(date, micros);
    }

    private static Array getDataArray(DataType type, Variable variable, Class clazz) throws IOException {
        final int[] origin = new int[variable.getRank()];
        final int[] shape = variable.getShape();
        Array array = null;
        try {
            array = variable.read(new Section(origin, shape));
        } catch (Exception e) {
            throw new IOException(e);
        }
        if (array != null) {
            return Array.factory(type, shape, array.get1DJavaArray(clazz));
        }
        return null;
    }

    private static DateFormat createDateFormat(String pattern) {
        final SimpleDateFormat dateFormat = new SimpleDateFormat(pattern, Locale.ENGLISH);
        dateFormat.setCalendar(createCalendar());
        return dateFormat;
    }

    private static Calendar createCalendar() {
        final Calendar calendar = GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
        calendar.clear();
        calendar.set(2000, Calendar.JANUARY, 1);
        return calendar;
    }

    private static long create(final Date date, long micros) {
        final Calendar calendar = createCalendar();
        final long offset = calendar.getTimeInMillis();
        calendar.setTime(date);
        final int millsPerSecond = 1000;
        final int millisPerDay = 24 * 60 * 60 * millsPerSecond;
        calendar.add(Calendar.DATE, -(int) (offset / millisPerDay));
        calendar.add(Calendar.MILLISECOND, -(int) (offset % millisPerDay));
        final long millisToAdd = Math.round(micros / 1000.0);
        return calendar.getTimeInMillis() + millisToAdd;
    }

    public static List<List<NetcdfFile>> getNcFileSubGroups(List<NetcdfFile> ncFileList) {

        List<List<NetcdfFile>> ncFileSubGroups = new ArrayList<List<NetcdfFile>>();
        if (ncFileList.size() == 1) {
            ncFileSubGroups.add(ncFileList);
            return ncFileSubGroups;
        }

        List<NetcdfFile> ncFileSubGroup = new ArrayList<NetcdfFile>();
        ncFileSubGroup.add(ncFileList.get(0));
        for (int i = 0; i < ncFileList.size() - 1; i++) {
            final NetcdfFile thisNcFile = ncFileList.get(i);
            final NetcdfFile nextNcFile = ncFileList.get(i + 1);
            final long thisStopTime = getStopTime(thisNcFile);
            final long nextStartTime = getStartTime(nextNcFile);
            final long nextStopTime = getStopTime(nextNcFile);
            if (nextStartTime <= thisStopTime) {
                if (nextStopTime > thisStopTime) {
                    // overlap
                    ncFileSubGroup.add(nextNcFile);
                } else {
                    // 'next' product is fully included in current product, no need to process
                    // --> no action
                }
            } else {
                // gap --> new subgroup!
                ncFileSubGroups.add(ncFileSubGroup);
                ncFileSubGroup = new ArrayList<NetcdfFile>();
                ncFileSubGroup.add(nextNcFile);
            }
        }

        // add last group
        ncFileSubGroups.add(ncFileSubGroup);

        return ncFileSubGroups;
    }

    public static long getStartTime(NetcdfFile ncFile) {
        final List<Attribute> globalAttributes = ncFile.getGlobalAttributes();

        for (Attribute attribute : globalAttributes) {
            if (attribute.getName().equals("start_date")) {
                return getTimeAsLong(attribute);
            }
        }
        return -1;
    }

    public static long getStopTime(NetcdfFile ncFile) {
        final List<Attribute> globalAttributes = ncFile.getGlobalAttributes();

        for (Attribute attribute : globalAttributes) {
            if (attribute.getName().equals("stop_date")) {
                return getTimeAsLong(attribute);
            }
        }
        return -1;
    }

    public static long getTimeAsLong(Attribute attribute) {
        final String dateString = attribute.getStringValue();
        try {
            return ProductStitcherNetcdfUtils.parse(dateString, DATE_PATTERN);
        } catch (ParseException e) {
            BeamLogManager.getSystemLogger().log(Level.SEVERE, e.getMessage());
        }
        return -1;
    }


}
