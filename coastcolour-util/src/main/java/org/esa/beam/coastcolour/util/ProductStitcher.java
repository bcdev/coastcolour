package org.esa.beam.coastcolour.util;

import org.apache.commons.collections.map.HashedMap;
import ucar.ma2.*;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.Variable;

import java.io.File;
import java.io.IOException;
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
public class ProductStitcher {

    private static final String DIMX_NAME = "x";
    private static final String DIMY_NAME = "y";
    private static final String TP_DIMX_NAME = "tp_x";
    private static final String TP_DIMY_NAME = "tp_y";

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
                // todo: we compare just coordinates of first and last pixel in row - is this a good criterion?
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

    static float[][] getFloat2DArrayFromNetcdfVariable(Variable variable) {
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

    static Vector<float[][]> getNetcdfVariableFloat2DDataFromSingleProducts(List<List<Variable>> variableList,
                                                                            String variableName) {
        Vector<float[][]> dataVector = new Vector<float[][]>();
        for (List<Variable> variables : variableList) {
            for (Variable variable : variables) {
                if (variableName.equals(variable.getName())) {
                    dataVector.add(ProductStitcher.getFloat2DArrayFromNetcdfVariable(variable));
                }
            }
        }
        return dataVector;
    }

    static Map<Integer, Integer> getBandRowToProductIndexMap(List<List<Variable>> variableList) {
        Vector<float[][]> latVector = getNetcdfVariableFloat2DDataFromSingleProducts(variableList, "lat");
        Vector<float[][]> lonVector = getNetcdfVariableFloat2DDataFromSingleProducts(variableList, "lon");
        return ProductStitcher.getRowToProductIndexMap(latVector, lonVector);
    }

    static Map<Integer, Integer> getTpRowToProductIndexMap(List<List<Variable>> variableList) {
        Vector<float[][]> latVector = getNetcdfVariableFloat2DDataFromSingleProducts(variableList, "latitude");
        Vector<float[][]> lonVector = getNetcdfVariableFloat2DDataFromSingleProducts(variableList, "longitude");
        return ProductStitcher.getRowToProductIndexMap(latVector, lonVector);
    }

    static List<Variable> getBandVariablesList(List<Variable> allVariablesList) {
        List<Variable> bandVariableList = new ArrayList<Variable>();
        for (Variable variable : allVariablesList) {
            if (variable.getDimensions().size() == 2 &&
                    variable.getDimension(0).getName().equals(DIMY_NAME) && variable.getDimension(1).getName().equals(DIMX_NAME)) {
                bandVariableList.add(variable);
            }
        }
        return bandVariableList;
    }

    static List<Variable> getTpVariablesList(List<Variable> allVariablesList) {
        List<Variable> bandVariableList = new ArrayList<Variable>();
        for (Variable variable : allVariablesList) {
            if (variable.getDimensions().size() == 2 &&
                    variable.getDimension(0).getName().equals(TP_DIMY_NAME) && variable.getDimension(1).getName().equals(TP_DIMX_NAME)) {
                bandVariableList.add(variable);
            }
        }
        return bandVariableList;
    }

    static void writeStitchedProduct(File ncResultFile,
                                     List<List<Attribute>> allAttributesLists, List<List<Dimension>> allDimensionsLists, List<List<Variable>> allBandVariableLists,
                                     List<List<Variable>> allTpVariableLists,
                                     Map<Integer, Integer> bandRowToProductIndexMap, Map<Integer, Integer> tpRowToProductIndexMap) {
        NetcdfFileWriteable outFile = null;
        try {
            outFile = NetcdfFileWriteable.createNew(ncResultFile.getAbsolutePath(), false);

            // add global attributes from first product, exchange specific single attributes:
            // - TileSize todo
            // - stop date from last product todo
            final List<Attribute> firstProductGlobalAttributes = allAttributesLists.get(0);
            for (Attribute attribute : firstProductGlobalAttributes) {
                outFile.addGlobalAttribute(attribute);
            }
            final List<Attribute> lastProductGlobalAttributes = allAttributesLists.get(allAttributesLists.size() - 1);
            for (Attribute attribute : lastProductGlobalAttributes) {
                if (attribute.getName().equals("stop_date")) {
                    outFile.addGlobalAttribute(attribute);
                }
            }

            // add dimensions: we have y, x, tp_y, tp_x in this sequence:
            // take from first product
            // todo: make sure earlier that all products have same dimensions
            final Dimension yDim = allDimensionsLists.get(0).get(0);
            yDim.setLength(bandRowToProductIndexMap.size());
            final Dimension xDim = allDimensionsLists.get(0).get(1);
            final Dimension yTpDim = allDimensionsLists.get(0).get(2);
            yTpDim.setLength(tpRowToProductIndexMap.size());
            final Dimension xTpDim = allDimensionsLists.get(0).get(3);
            outFile.addDimension(DIMY_NAME, yDim.getLength());
            outFile.addDimension(DIMX_NAME, xDim.getLength());
            outFile.addDimension(TP_DIMY_NAME, yTpDim.getLength());
            outFile.addDimension(TP_DIMX_NAME, xTpDim.getLength());
            outFile.addGlobalAttribute("TileSize", yDim.getLength() + ":" + xDim.getLength());

            // add band data taken from correct product:
            int sourceProductIndex = 0;

            // set up data buffer
            ArrayFloat.D2 bandData = new ArrayFloat.D2(yDim.getLength(), xDim.getLength());
            List<Variable> firstBandVariables = allBandVariableLists.get(0);

            // loop over variables
            for (Variable variable : firstBandVariables) {
                // add band variables, take from first product
                outFile.addVariable(variable.getName(), DataType.FLOAT, new Dimension[]{yDim, xDim});
                final List<Attribute> variableAttributes = variable.getAttributes();
                for (Attribute attribute : variableAttributes) {
                    outFile.addVariableAttribute(variable.getName(), attribute);
                }

                // loop over single products
                for (int i = 0; i < allBandVariableLists.size(); i++) {
                    List<Variable> allBandVariables = allBandVariableLists.get(i);
                    for (Variable variable2 : allBandVariables) {
                        if (variable2.getName().equals(variable.getName())) {
                            // get data array for THIS variable and THIS single product
                            final float[][] values = getFloat2DArrayFromNetcdfVariable(variable2);
                            // now loop over rows:
                            for (int j=0; j<yDim.getLength(); j++) {
                                // if the current single product is the right one, loop over raster and set netcdf values
                                sourceProductIndex = bandRowToProductIndexMap.get(j);
                                if (sourceProductIndex == i) {
                                    for (int k=0; k<xDim.getLength(); k++) {
                                        bandData.set(j, k, values[j][k]);
                                    }
                                }
                            }
                            outFile.write(variable2.getName(), bandData);
                        }
                    }
                }
            }

            outFile.create();
        } catch (IOException e) {
            // todo
            e.printStackTrace();
        } catch (InvalidRangeException e) {
            // todo
            e.printStackTrace();
        } finally {
            if (null != outFile)
                try {
                    outFile.close();
                } catch (IOException ignore) {
                }
        }

    }
}
