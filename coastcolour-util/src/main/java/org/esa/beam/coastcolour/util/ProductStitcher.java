package org.esa.beam.coastcolour.util;

import org.apache.commons.collections.map.HashedMap;
import ucar.ma2.*;
import ucar.nc2.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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

    public static List<NetcdfFile> getSortedAndValidatedInputProducts(File configFile, File sourceProductDir) {
        // Sorting:
        // - sort by start time
        // Validation:
        // - make sure all products have same orbit number as first product in sorted list
        // - make sure all products have same dimensions as first product in sorted list
        // todo implement

        List<NetcdfFile> unsortedProducts = new ArrayList<NetcdfFile>();
        List<Long> startTimes = new ArrayList<Long>();
        BufferedReader reader = null;
        try {
            String line;
            reader = new BufferedReader(new FileReader(configFile.getAbsolutePath()));
            while ((line = reader.readLine()) != null) {
                final String filename = line.trim();
                System.out.println("filename = " + filename);
                final String filePath = sourceProductDir.getAbsolutePath() + File.separator + filename;
                final NetcdfFile ncFile = NetcdfFile.openInMemory(filePath);
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

//        final Map<String, Long> startTimeMap = getStartTimeFromNetcdfAttribute(unsortedProducts);
//        for (int i = 0; i < startTimeMap.size(); i++) {
//
//        }
//
//        Collections.sort(startTimes);
//        for (Long startTime : startTimes) {
//
//        }

        return null;
    }

//    private static Map<String, Long> getStartTimeFromNetcdfAttribute(List<NetcdfFile> ncFiles) {
//        final Map<String, Long> startTimeMap = new HashMap<String, Long>();
//
//        for (NetcdfFile ncFile : ncFiles) {
//            final List<Attribute> attributes = ncFile.getGlobalAttributes();
//            for (Attribute attribute : attributes) {
//                if (attribute.getName().equals("start_date")) {
//                    final String dateString = attribute.getStringValue();
//                    DateFormat dfm = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
//                    try {
//                        Date d = dfm.parse(dateString);
//                        startTimeMap.put(dateString, d.getTime());
//                    } catch (ParseException e) {
//                        // todo
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }
//        return startTimeMap;
//    }

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

    private static Array getDataArray(DataType type, Variable variable, Class clazz) {
        final int[] origin = new int[variable.getRank()];
        final int[] shape = variable.getShape();
        Array array = null;
        try {
            array = variable.read(new Section(origin, shape));
        } catch (Exception e) {
            new DefaultErrorHandler().error(e);
        }
        return Array.factory(type, shape, array.get1DJavaArray(clazz));
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
            if (isValidBandVariable(variable) || isValidFlagBandVariable(variable)) {
                bandVariableList.add(variable);
            }
        }
        return bandVariableList;
    }

    private static boolean isValidBandVariable(Variable variable) {
        return isValidL1PBandVariable(variable) || isValidL2RBandVariable(variable) || isValidL2WBandVariable(variable);
    }

    private static boolean isValidL2WBandVariable(Variable variable) {
        final String name = variable.getName();
        final boolean isNameValid = name.equals("lat") || name.equals("lon") ||
                name.equals("chiSquare") || name.equals("K_min") || name.equals("Z90_max") || name.equals("turbidity") ||
                name.startsWith("iop") || name.startsWith("conc") || name.startsWith("Kd");
        return isNameValid && areDimensionsValid(variable);
    }

    private static boolean isValidL2RBandVariable(Variable variable) {
        final String name = variable.getName();
        final boolean isNameValid = name.equals("lat") || name.equals("lon") || name.equals("ang_443_865") ||
                name.startsWith("reflec") || name.startsWith("norm_refl") || name.startsWith("atm_tau");
        return isNameValid && areDimensionsValid(variable);
    }

    private static boolean isValidL1PBandVariable(Variable variable) {
        final String name = variable.getName();
        final boolean isNameValid = name.equals("lat") || name.equals("lon") || name.startsWith("radiance");
        return isNameValid && areDimensionsValid(variable);
    }

    private static boolean areDimensionsValid(Variable variable) {
        return variable.getDimensions().size() == 2 &&
                variable.getDimension(0).getName().equals(DIMY_NAME) &&
                variable.getDimension(1).getName().equals(DIMX_NAME);
    }

    private static boolean isValidFlagBandVariable(Variable variable) {
        return isValidL1PFlagBandVariable(variable) ||
                isValidL2RFlagBandVariable(variable) ||
                isValidL2WFlagBandVariable(variable);
    }

    private static boolean isValidL2WFlagBandVariable(Variable variable) {
        final boolean isNameValid = isValidL2RFlagBandVariable(variable) || variable.getName().equals("l2w_flags");
        return isNameValid;
    }

    private static boolean isValidL2RFlagBandVariable(Variable variable) {
        final boolean isNameValid = isValidL1PFlagBandVariable(variable) || variable.getName().equals("l2r_flags");
        return isNameValid && areDimensionsValid(variable);
    }

    private static boolean isValidL1PFlagBandVariable(Variable variable) {
        final boolean isNameValid = variable.getName().equals("l1_flags") || variable.getName().equals("l1p_flags");
        return isNameValid && areDimensionsValid(variable);
    }

    static List<Variable> getTpVariablesList(List<Variable> allVariablesList) {
        List<Variable> bandVariableList = new ArrayList<Variable>();
        for (Variable variable : allVariablesList) {
            if (variable.getDimensions().size() == 2 && variable.getDataType().getClassType().getSimpleName().equals("float") &&
                    variable.getDimension(0).getName().equals(TP_DIMY_NAME) && variable.getDimension(1).getName().equals(TP_DIMX_NAME)) {
                bandVariableList.add(variable);
            }
        }
        return bandVariableList;
    }

    static void writeStitchedProduct(File ncResultFile,
                                     List<List<Attribute>> allAttributesLists,
                                     List<List<Dimension>> allDimensionsLists,
                                     List<List<Variable>> allBandVariableLists,
                                     List<List<Variable>> allTpVariableLists,
                                     Map<Integer, Integer> bandRowToProductIndexMap,
                                     Map<Integer, Integer> tpRowToProductIndexMap,
                                     DefaultErrorHandler handler) {
        NetcdfFileWriteable outFile = null;
        try {
            outFile = NetcdfFileWriteable.createNew(ncResultFile.getAbsolutePath(), false);

            // add global attributes from first product, exchange specific single attributes:
            addGlobalAttributes(allAttributesLists, outFile);

            // take dimensions from first product
            final Dimension yDim = allDimensionsLists.get(0).get(0);
            final Dimension xDim = allDimensionsLists.get(0).get(1);
            final Dimension yTpDim = allDimensionsLists.get(0).get(2);
            final Dimension xTpDim = allDimensionsLists.get(0).get(3);

            // add dimensions to output: we have y, x, tp_y, tp_x in this sequence:
            addDimensions(bandRowToProductIndexMap, tpRowToProductIndexMap, outFile, yDim, xDim, yTpDim, xTpDim);

            // add bands and tie point variable attributes to output:
            addVariableAttributes(allBandVariableLists, outFile, yDim, xDim);
            addVariableAttributes(allTpVariableLists, outFile, yTpDim, xTpDim);

            // we need to call this after all attributes and dimensions were added:
            outFile.setLargeFile(true);
            outFile.create();

            // add band and tie point data to output:
            writeVariables(allBandVariableLists, bandRowToProductIndexMap, outFile, yDim, xDim);
            writeVariables(allTpVariableLists, tpRowToProductIndexMap, outFile, yTpDim, xTpDim);

        } catch (IOException e) {
            handler.error(e);
        } catch (InvalidRangeException e) {
            handler.error(e);
        } finally {
            if (null != outFile)
                try {
                    outFile.close();
                } catch (IOException ignore) {
                }
        }
    }

    private static void addDimensions(Map<Integer, Integer> bandRowToProductIndexMap, Map<Integer, Integer> tpRowToProductIndexMap, NetcdfFileWriteable outFile, Dimension yDim, Dimension xDim, Dimension yTpDim, Dimension xTpDim) {
        yDim.setLength(bandRowToProductIndexMap.size());
        outFile.addDimension(DIMY_NAME, yDim.getLength());
        outFile.addDimension(DIMX_NAME, xDim.getLength());
        yTpDim.setLength(tpRowToProductIndexMap.size());
        outFile.addDimension(TP_DIMY_NAME, yTpDim.getLength());
        outFile.addDimension(TP_DIMX_NAME, xTpDim.getLength());
        outFile.addGlobalAttribute("TileSize", yDim.getLength() + ":" + xDim.getLength());
    }

    private static void addGlobalAttributes(List<List<Attribute>> allAttributesLists, NetcdfFileWriteable outFile) {
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
    }

    private static void addVariableAttributes(List<List<Variable>> variableLists,
                                              NetcdfFileWriteable outFile,
                                              Dimension yDim, Dimension xDim) throws IOException, InvalidRangeException {
        List<Variable> firstVariables = variableLists.get(0);
        // loop over variables
        for (Variable variable : firstVariables) {
            // add band variables, take from first product
            outFile.addVariable(variable.getName(), variable.getDataType(), new Dimension[]{yDim, xDim});
            final List<Attribute> variableAttributes = variable.getAttributes();
            for (Attribute attribute : variableAttributes) {
                outFile.addVariableAttribute(variable.getName(), attribute);
            }
        }
    }

    private static void writeVariables(List<List<Variable>> variableLists,
                                       Map<Integer, Integer> rowToProductIndexMap,
                                       NetcdfFileWriteable outFile,
                                       Dimension yDim, Dimension xDim) throws IOException, InvalidRangeException {
        int sourceProductIndex;

        // set up data buffers for all types which occur in L1P, L2R, L2W products
        ArrayFloat.D2 bandDataFloat = new ArrayFloat.D2(yDim.getLength(), xDim.getLength());
        ArrayShort.D2 bandDataShort = new ArrayShort.D2(yDim.getLength(), xDim.getLength());
        ArrayByte.D2 bandDataByte = new ArrayByte.D2(yDim.getLength(), xDim.getLength());

        List<Variable> firstProductBandVariables = variableLists.get(0);

        for (Variable variable : firstProductBandVariables) {
            // loop over single products
            for (int i = 0; i < variableLists.size(); i++) {
                List<Variable> allBandVariables = variableLists.get(i);
                for (Variable variable2 : allBandVariables) {
                    if (variable2.getName().equals(variable.getName())) {
                        // get data array for THIS variable and THIS single product
                        ////
                        // testdata files do not have right yDim
                        // todo: remove this later!!
                        variable2.getDimension(0).setLength(variable2.getShape(0));
                        variable2.getDimension(1).setLength(variable2.getShape(1));
                        ////
                        byte[][] byteVals = null;
                        short[][] shortVals = null;
                        float[][] floatVals = null;
                        switch (variable2.getDataType()) {
                            case BYTE:
                                byteVals = getByte2DArrayFromNetcdfVariable(variable2);
                                break;
                            case SHORT:
                                shortVals = getShort2DArrayFromNetcdfVariable(variable2);
                                break;
                            case FLOAT:
                                floatVals = getFloat2DArrayFromNetcdfVariable(variable2);
                                break;
                            default:
                                throw new IllegalArgumentException("Data type '" + variable.getDataType().name() + "' not supported.");
                        }
                        int valuesRowIndex = 0;
                        int sourceProductIndexPrev = 0;
                        // now loop over rows:
                        for (int j = 0; j < rowToProductIndexMap.size(); j++) {
                            // if the current single product is the right one, loop over raster and set netcdf floatVals
                            sourceProductIndex = rowToProductIndexMap.get(j);
                            if (sourceProductIndex > sourceProductIndexPrev) {
                                valuesRowIndex = 0;
                            }
                            if (sourceProductIndex == i) {
                                for (int k = 0; k < xDim.getLength(); k++) {
                                    switch (variable2.getDataType()) {
                                        case BYTE:
                                            bandDataByte.set(j, k, byteVals[valuesRowIndex][k]);
                                            break;
                                        case SHORT:
                                            bandDataShort.set(j, k, shortVals[valuesRowIndex][k]);
                                            break;
                                        case FLOAT:
                                            bandDataFloat.set(j, k, floatVals[valuesRowIndex][k]);
                                            break;
                                        default:
                                            throw new IllegalArgumentException("Data type '" + variable2.getDataType().name() + "' not supported.");
                                    }

                                }
                            }
                            valuesRowIndex++;
                            sourceProductIndexPrev = sourceProductIndex;
                        }
                        switch (variable2.getDataType()) {
                            case BYTE:
                                outFile.write(variable2.getName(), bandDataByte);
                                break;
                            case SHORT:
                                outFile.write(variable2.getName(), bandDataShort);
                                break;
                            case FLOAT:
                                outFile.write(variable2.getName(), bandDataFloat);
                                break;
                            default:
                                throw new IllegalArgumentException("Data type '" + variable2.getDataType().name() + "' not supported.");
                        }
                    }
                }
            }
        }
    }

}
