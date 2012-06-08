package org.esa.beam.coastcolour.util;

import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.Section;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Netcdf utility class for CC product stitcher
 *
 * @author olafd
 */
public class ProductStitcherNetcdfUtils {

    /**
     * Gets the list of netCDF files from given array of source file paths
     *
     * @param sourceFilePaths - the source file paths
     * @return the list of netCDF files to stitch
     */
    static List<NetcdfFile> getSourceProductSetsToStitch(String[] sourceFilePaths) {
        Arrays.sort(sourceFilePaths);

        List<NetcdfFile> ncProducts = new ArrayList<NetcdfFile>();
        for (String sourceFilePath : sourceFilePaths) {
            try {
                final NetcdfFile ncFile = NetcdfFile.open(sourceFilePath);
                ncProducts.add(ncFile);
            } catch (IOException e) {
                // todo
                e.printStackTrace();
            }
        }

        return ncProducts;
    }

    /**
     * Sets up filename of stitched product: just computes the acquisition time for the "stitch interval" and
     * replaces this in the filename of first product
     *
     * @param sourcePaths  - the source file paths
     * @return   the filename of the stitched product
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

    static float[][] getFloat2DArrayFromNetcdfVariable(Variable variable) {
        final Array arrayFloat = getDataArray(DataType.FLOAT, variable, Float.class);
        return (float[][]) arrayFloat.copyToNDJavaArray();
    }

    static short[][] getShort2DArrayFromNetcdfVariable(Variable variable) {
        final Array arrayShort = getDataArray(DataType.SHORT, variable, Short.class);
        return (short[][]) arrayShort.copyToNDJavaArray();
    }

    static byte[][] getByte2DArrayFromNetcdfVariable(Variable variable) {
        final Array arrayByte = getDataArray(DataType.BYTE, variable, Byte.class);
        return (byte[][]) arrayByte.copyToNDJavaArray();
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

    private static Array getDataArray(DataType type, Variable variable, Class clazz) {
        final int[] origin = new int[variable.getRank()];
        final int[] shape = variable.getShape();
        Array array = null;
        try {
            array = variable.read(new Section(origin, shape));
        } catch (Exception e) {
            new DefaultErrorHandler().error(e);
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
        calendar.set(2000, 0, 1);
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

}
