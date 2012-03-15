package org.esa.beam.coastcolour.util;

import ucar.ma2.ArrayByte;
import ucar.ma2.ArrayFloat;
import ucar.ma2.ArrayShort;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.*;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class for product stitching
 * Date: 12.03.12
 * Time: 15:15
 *
 * @author olafd
 */
public class ProductStitcher {

    static final String DIMX_NAME = "x";
    static final String DIMY_NAME = "y";
    static final String TP_DIMX_NAME = "tp_x";
    static final String TP_DIMY_NAME = "tp_y";

    public static final String DATE_PATTERN = "dd-MMM-yyyy HH:mm:ss";

    List<NetcdfFile> ncFileList;

    List<Map<Integer, Long>> bandRowToScanTimeMaps;
    List<Map<Integer, Long>> tpRowToScanTimeMaps;

    List<List<Attribute>> allAttributesLists = new ArrayList<List<Attribute>>();
    List<List<Dimension>> allDimensionsLists = new ArrayList<List<Dimension>>();
    List<List<Variable>> allBandVariablesLists = new ArrayList<List<Variable>>();
    List<List<Variable>> allTpVariablesLists = new ArrayList<List<Variable>>();

    int stitchedProductHeightBands;
    int stitchedProductHeightTps;
    Map<Integer, Long> stitchedProductRowToScanTimeMap;

    public ProductStitcher(List<NetcdfFile> ncFileList) {
        this.ncFileList = ncFileList;
        setAllAttributesList();
        setAllDimensionsList();
        setAllBandVariablesLists();
        setAllTpVariablesLists();
        setRowToScanTimeMaps(true);
        setRowToScanTimeMaps(false);
        setStitchedProductHeightBands();
        setStitchedProductHeightTps();
        setStitchedProductRowToScanTimeMap(false, stitchedProductHeightBands);
        setStitchedProductRowToScanTimeMap(true, stitchedProductHeightTps);
    }


    private void setRowToScanTimeMaps(boolean isTiepoints) {
        List<Map<Integer, Long>> rowToScanTimeMaps = new ArrayList<Map<Integer, Long>>();
        for (NetcdfFile netcdfFile : ncFileList) {
            int yDim = -1;
            int xDim = -1;
            String xDimName = (isTiepoints ? ProductStitcher.TP_DIMX_NAME : ProductStitcher.DIMX_NAME);
            String yDimName = (isTiepoints ? ProductStitcher.TP_DIMY_NAME : ProductStitcher.DIMY_NAME);
            final List<Dimension> dimensions = netcdfFile.getDimensions();
            for (Dimension dimension : dimensions) {
                if (dimension.getName().equals(xDimName)) {
                    xDim = dimension.getLength();
                }
                if (dimension.getName().equals(yDimName)) {
                    yDim = dimension.getLength();
                }
            }
            if (xDim == -1 || yDim == -1) {
                throw new IllegalStateException("Input file ' " + netcdfFile.getLocation() +
                                                        "' does not have expected dimension names - check product!");
            }

            final List<Attribute> globalAttributes = netcdfFile.getGlobalAttributes();
            long startTime = -1;
            long stopTime = -1;

            for (Attribute attribute : globalAttributes) {
                if (attribute.getName().equals("start_date")) {
                    startTime = getTimeAsLong(attribute);
                }
                if (attribute.getName().equals("stop_date")) {
                    stopTime = getTimeAsLong(attribute);
                }
            }

            if (startTime == -1 || stopTime == -1) {
                throw new IllegalStateException("Input file ' " + netcdfFile.getLocation() +
                                                        "': start/stop times cannot be parsed - check product!");
            }

            // interpolation:
            Map<Integer, Long> bandRowToScanTimeMap = new HashMap<Integer, Long>();
            for (int i = 0; i < yDim; i++) {
                bandRowToScanTimeMap.put(i, startTime + i * (stopTime - startTime) / (yDim - 1));
            }
            rowToScanTimeMaps.add(bandRowToScanTimeMap);
        }

        if (isTiepoints) {
            bandRowToScanTimeMaps = rowToScanTimeMaps;
        } else {
            tpRowToScanTimeMaps = rowToScanTimeMaps;
        }
    }

    private void setAllAttributesList() {
        for (NetcdfFile ncFile : ncFileList) {
            final List<Attribute> attributes = ncFile.getGlobalAttributes();
            allAttributesLists.add(attributes);
        }
    }

    private void setAllDimensionsList() {
        for (NetcdfFile ncFile : ncFileList) {
            final List<Dimension> dimensions = ncFile.getDimensions();
            allDimensionsLists.add(dimensions);
        }
    }

    private void setAllBandVariablesLists() {
        for (NetcdfFile ncFile : ncFileList) {
            final List<Variable> allVariablesList = ncFile.getVariables();
            List<Variable> bandVariablesList = new ArrayList<Variable>();
            for (Variable variable : allVariablesList) {
                if (ProductStitcherValidation.isValidBandVariable(variable) || ProductStitcherValidation.isValidFlagBandVariable(variable)) {
                    bandVariablesList.add(variable);
                }
            }
            allBandVariablesLists.add(allVariablesList);
        }
    }

    private void setAllTpVariablesLists() {
        for (NetcdfFile ncFile : ncFileList) {
            final List<Variable> allVariablesList = ncFile.getVariables();
            List<Variable> tpVariablesList = new ArrayList<Variable>();
            for (Variable variable : allVariablesList) {
                // todo: this is bad. validate as for bands
                if (variable.getDimensions().size() == 2 && variable.getDataType().getClassType().getSimpleName().equals("float") &&
                        variable.getDimension(0).getName().equals(TP_DIMY_NAME) && variable.getDimension(1).getName().equals(TP_DIMX_NAME)) {
                    tpVariablesList.add(variable);
                }
            }
            allTpVariablesLists.add(tpVariablesList);
        }
    }

    public void setStitchedProductRowToScanTimeMap(boolean isTiepoints, int yDim) {

        NetcdfFile firstNcFile = ncFileList.get(0);
        NetcdfFile lastNcFile = ncFileList.get(ncFileList.size() - 1);

        final List<Attribute> firstGlobalAttributes = firstNcFile.getGlobalAttributes();
        long startTime = -1;
        for (Attribute attribute : firstGlobalAttributes) {
            if (attribute.getName().equals("start_date")) {
                startTime = getTimeAsLong(attribute);
            }
        }

        final List<Attribute> lastGlobalAttributes = lastNcFile.getGlobalAttributes();
        long stopTime = -1;
        for (Attribute attribute : lastGlobalAttributes) {
            if (attribute.getName().equals("stop_date")) {
                stopTime = getTimeAsLong(attribute);
            }
        }

        if (startTime == -1) {
            throw new IllegalStateException("Input file ' " + firstNcFile.getLocation() +
                                                    "': start time cannot be parsed - check product!");
        }
        if (stopTime == -1) {
            throw new IllegalStateException("Input file ' " + lastNcFile.getLocation() +
                                                    "': stop time cannot be parsed - check product!");
        }

        // interpolation:
        stitchedProductRowToScanTimeMap = new HashMap<Integer, Long>();
        for (int i = 0; i < yDim; i++) {
            stitchedProductRowToScanTimeMap.put(i, startTime + i * (stopTime - startTime) / (yDim - 1));
        }
    }

    private static long getTimeAsLong(Attribute attribute) {
        String dateString = attribute.getStringValue();
        try {
            return ProductStitcherNetcdfUtils.parse(dateString, DATE_PATTERN);
        } catch (ParseException e) {
            // todo
            e.printStackTrace();
        }
        return -1;
    }

    public void setStitchedProductHeightBands() {
        // go through row <--> scanTime
        for (int i = 0; i < bandRowToScanTimeMaps.size() - 1; i++) {
            final Map<Integer, Long> map = bandRowToScanTimeMaps.get(i);
            final Map<Integer, Long> nextMap = bandRowToScanTimeMaps.get(i + 1);
            for (int j = 0; j < map.size(); j++) {
                // count until start time of next product is reached
                while (map.get(j) < nextMap.get(0)) {
                    stitchedProductHeightBands++;
                }
            }
        }
        stitchedProductHeightBands += bandRowToScanTimeMaps.get(bandRowToScanTimeMaps.size() - 1).size();
    }

    public void setStitchedProductHeightTps() {
        // go through row <--> scanTime
        for (int i = 0; i < tpRowToScanTimeMaps.size() - 1; i++) {
            final Map<Integer, Long> map = tpRowToScanTimeMaps.get(i);
            final Map<Integer, Long> nextMap = tpRowToScanTimeMaps.get(i + 1);
            for (int j = 0; j < map.size(); j++) {
                // count until start time of next product is reached
                while (map.get(j) < nextMap.get(0)) {
                    stitchedProductHeightTps++;
                }
            }
        }
        stitchedProductHeightTps += tpRowToScanTimeMaps.get(tpRowToScanTimeMaps.size() - 1).size();
    }


    public void writeStitchedProduct(File ncResultFile,
                                     List<List<Attribute>> allAttributesLists,
                                     List<List<Dimension>> allDimensionsLists,
                                     List<List<Variable>> allBandVariableLists,
                                     List<List<Variable>> allTpVariableLists,
//                                     Map<Integer, Integer> bandRowToProductIndexMap,
//                                     Map<Integer, Integer> tpRowToProductIndexMap,
                                     List<Map<Integer, Long>> bandRowToScanTimeMaps,
                                     List<Map<Integer, Long>> tpRowToScanTimeMaps,
                                     DefaultErrorHandler handler) {
        NetcdfFileWriteable outFile = null;
        Logger.getAnonymousLogger().log(Level.INFO, "Start writing stitched product...");
        try {
            outFile = NetcdfFileWriteable.createNew(ncResultFile.getAbsolutePath(), false);

            // add global attributes from first product, exchange specific single attributes:
            addGlobalAttributes(allAttributesLists, outFile);

            // add dimensions to output: we have y, x, tp_y, tp_x in this sequence:
            outFile.addDimension(DIMY_NAME, stitchedProductHeightBands);
            final Dimension xDim = allDimensionsLists.get(0).get(1);
            outFile.addDimension(DIMX_NAME, xDim.getLength());
            outFile.addDimension(TP_DIMY_NAME, stitchedProductHeightTps);
            final Dimension xTpDim = allDimensionsLists.get(0).get(3);
            outFile.addDimension(TP_DIMX_NAME, xTpDim.getLength());

            // add bands and tie point variable attributes to output:
            addVariableAttributes(allBandVariableLists, outFile, new Dimension(DIMY_NAME, stitchedProductHeightBands), new Dimension(DIMX_NAME, xDim));
            addVariableAttributes(allTpVariableLists, outFile, new Dimension(TP_DIMY_NAME, stitchedProductHeightTps), new Dimension(TP_DIMX_NAME, xDim));

            // we need to call 'create' after all attributes and dimensions were added:
            try {
                // try in standard mode first, which may fail for large files...
                outFile.create();
            } catch (IllegalArgumentException e) {
//                BeamLogManager.getSystemLogger().log(Level.INFO, "Switching to NetCDF 'large file' mode...");
                Logger.getAnonymousLogger().log(Level.INFO, "Switching to NetCDF 'large file' mode...");
                outFile.setLargeFile(true);
                outFile.create();
            }

            // add band and tie point data to output:
            writeVariables(allBandVariableLists, bandRowToScanTimeMaps, outFile, xDim.getLength());
            writeVariables(allTpVariableLists, tpRowToScanTimeMaps, outFile, xTpDim.getLength());

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
        Logger.getAnonymousLogger().log(Level.INFO, "Finished writing stitched product.");
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

    private void writeVariables(List<List<Variable>> variableLists,
//                                       Map<Integer, Integer> rowToProductIndexMap,
                                       List<Map<Integer, Long>> rowToScanTimeMaps,
                                       NetcdfFileWriteable outFile,
                                       int xDim) throws IOException, InvalidRangeException {
        int sourceProductIndex;

        // set up data buffers for all types which occur in L1P, L2R, L2W products
        ArrayFloat.D2 bandDataFloat = new ArrayFloat.D2(stitchedProductHeightBands, xDim);
        ArrayShort.D2 bandDataShort = new ArrayShort.D2(stitchedProductHeightBands, xDim);
        ArrayByte.D2 bandDataByte = new ArrayByte.D2(stitchedProductHeightBands, xDim);

        List<Variable> firstProductBandVariables = variableLists.get(0);

        for (Variable variable : firstProductBandVariables) {
            // loop over single products
            Logger.getAnonymousLogger().log(Level.INFO, "...writing variable '" + variable.getName() + "'...");
            for (int i = 0; i < variableLists.size(); i++) {
                List<Variable> allBandVariables = variableLists.get(i);
                final Map<Integer, Long> rowToScanTimeMap = rowToScanTimeMaps.get(i);
                for (Variable variable2 : allBandVariables) {
                    if (variable2.getName().equals(variable.getName())) {
                        if (variable.getName().equals("metadata")) {
                            ArrayByte.D1 metadataBuffer = new ArrayByte.D1(1);
                            metadataBuffer.set(0, variable2.readScalarByte());
                        } else {
                            // get data array for THIS variable and THIS single product
                            // todo: check if and why we need this?!
                            variable2.getDimension(0).setLength(variable2.getShape(0));
                            variable2.getDimension(1).setLength(variable2.getShape(1));
                            ////
                            byte[][] byteVals = null;
                            short[][] shortVals = null;
                            float[][] floatVals = null;
                            switch (variable2.getDataType()) {
                                case BYTE:
                                    byteVals = ProductStitcherNetcdfUtils.getByte2DArrayFromNetcdfVariable(variable2);
                                    break;
                                case SHORT:
                                    shortVals = ProductStitcherNetcdfUtils.getShort2DArrayFromNetcdfVariable(variable2);
                                    break;
                                case FLOAT:
                                    floatVals = ProductStitcherNetcdfUtils.getFloat2DArrayFromNetcdfVariable(variable2);
                                    break;
                                default:
                                    throw new IllegalArgumentException("Data type '" + variable.getDataType().name() + "' not supported.");
                            }
                            int valuesRowIndex = 0;
                            int sourceProductIndexPrev = 0;
                            // now loop over ALL rows:
                            for (int j = 0; j < stitchedProductHeightBands; j++) {
                                // if the current single product is the right one, loop over raster and set netcdf floatVals
//                                sourceProductIndex = rowToProductIndexMap.get(j);
                                // todo !!!!!
                                sourceProductIndex = -1;
//                                sourceProductIndex = getSourceProductIndex(j);
                                if (sourceProductIndex > sourceProductIndexPrev) {
                                    valuesRowIndex = 0;
                                }
                                if (sourceProductIndex == i) {
                                    for (int k = 0; k < xDim; k++) {
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

}
