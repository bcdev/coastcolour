package org.esa.beam.coastcolour.util;

import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.Section;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Netcdf utility class for CC product stitcher
 * Date: 15.03.12
 * Time: 11:25
 *
 * @author olafd
 */
public class ProductStitcherNetcdfUtils {
    public static List<NetcdfFile> getSortedAndValidatedInputProducts(File configFile, File sourceProductDir) {
        // Sorting:
        // - sort by start time
        // Validation:
        // - make sure all products have same orbit number as first product in sorted list
        // - make sure all products have same dimensions as first product in sorted list

        List<NetcdfFile> unsortedProducts = new ArrayList<NetcdfFile>();
        BufferedReader reader = null;
        try {
            String line;
            reader = new BufferedReader(new FileReader(configFile.getAbsolutePath()));
            while ((line = reader.readLine()) != null) {
                final String filename = line.trim();
                System.out.println("filename = " + filename);
                final String filePath = sourceProductDir.getAbsolutePath() + File.separator + filename;
                final NetcdfFile ncFile = NetcdfFile.open(filePath);
                unsortedProducts.add(ncFile);
            }
        } catch (IOException e) {
            // todo
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                }
            }
        }

        List<NetcdfFile> sortedProducts = new ArrayList<NetcdfFile>();
        sortedProducts = unsortedProducts;
        // todo: sort and validate
        return sortedProducts;
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

    public static long parse(String text, String pattern) throws ParseException {
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
        return  calendar.getTimeInMillis() + millisToAdd;
    }

}
