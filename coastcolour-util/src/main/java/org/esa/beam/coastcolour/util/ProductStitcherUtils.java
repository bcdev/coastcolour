package org.esa.beam.coastcolour.util;

import org.apache.commons.collections.map.HashedMap;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.Section;
import ucar.nc2.Variable;

import java.util.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for product stitching
 * Date: 12.03.12
 * Time: 15:15
 *
 * @author olafd
 */
public class ProductStitcherUtils {

    public static Map<Integer, Integer> getRowToProductIndexMap(Vector<float[][]> latVector, Vector<float[][]> lonVector) {
        if (latVector.size() != lonVector.size()) {
            throw new IllegalStateException("Cannot stitch products - mismatch in latitude and longitude array sizes.");
        }

        float[][][] allLats = new float[latVector.size()][][];
        for (int i = 0; i < latVector.size(); i++) {
            allLats[i] = latVector.get(i);
        }
        float[][][] allLons = new float[lonVector.size()][][];
        for (int i = 0; i < lonVector.size(); i++) {
            allLons[i] = lonVector.get(i);
        }

        int row = 0;
        final int width = allLats[0][0].length;
        Map<Integer, Integer> rowToProductIndexMap = new HashMap<Integer, Integer>();
        for (int i = 0; i < allLats.length - 1; i++) {
            float[][] allLat = allLats[i];
            float[][] allLon = allLons[i];
            float[][] allLatNext = allLats[i + 1];
            float[][] allLonNext = allLons[i + 1];
            for (int j = 0; j < allLat.length; j++) {
                if (allLat[j][0] == allLatNext[0][0] && allLat[j][width - 1] == allLatNext[0][width - 1] &&
                        allLon[j][0] == allLonNext[0][0] && allLon[j][width - 1] == allLonNext[0][width - 1]) {
                    break;
                }
                rowToProductIndexMap.put(row, i);
                row++;
            }
        }
        // add indices of last product
        float[][] allLat = allLats[allLats.length - 1];
        for (int j = 0; j < allLat.length; j++) {
            rowToProductIndexMap.put(row++, allLats.length - 1);
        }

        return rowToProductIndexMap;
    }

    public static float[][] getFloat2DArrayFromNetcdfVariable(Variable variable) {
        final int[] origin = new int[variable.getRank()];
        final int[] shape = variable.getShape();
        Array array = null;
        try {
            array = variable.read(new Section(origin, shape));
        } catch (Exception e) {
            new DefaultErrorHandler().error(e);
        }
        final Array arrayFloat = Array.factory(DataType.FLOAT, shape, array.get1DJavaArray(Float.class));
        return (float[][]) arrayFloat.copyToNDJavaArray();
    }

    public static void writeStitchedProduct(String ncResultFilename, List<List<Variable>> variableListAll) {
        // todo : implement
    }
}
