package org.esa.beam.coastcolour.util;

import com.bc.ceres.core.PrintWriterProgressMonitor;
import ucar.ma2.ArrayByte;
import ucar.ma2.ArrayFloat;
import ucar.ma2.ArrayShort;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class providing the product stitching. Applicable for Coastcolour L1P, L2R and L2W NetCDF input products.
 *
 * @author olafd
 */
public class ProductStitcher {

    static final String DIMX_NAME = "x";
    static final String DIMY_NAME = "y";
    static final String TP_DIMX_NAME = "tp_x";
    static final String TP_DIMY_NAME = "tp_y";

    List<NetcdfFile> ncFileList;
    List<Map<Integer, Long>> bandRowToScanTimeMaps;

    List<Map<Integer, Long>> tpRowToScanTimeMaps;
    List<List<Attribute>> allAttributesLists = new ArrayList<List<Attribute>>();

    List<List<Dimension>> allDimensionsLists = new ArrayList<List<Dimension>>();
    List<List<Variable>> allBandVariablesLists = new ArrayList<List<Variable>>();
    List<List<Variable>> allTpVariablesLists = new ArrayList<List<Variable>>();
    Map<Integer, Long> stitchedProductBandRowToScanTimeMap;

    Map<Integer, Long> stitchedProductTpRowToScanTimeMap;
    Map<Long, TimeInterval> stitchedProductTpRowToScanNeighbourTimesMap;
    int stitchedProductWidthBands;

    int stitchedProductHeightBands;
    int stitchedProductHeightTps;
    int stitchedProductWidthTps;

    /**
     * Product stitcher constructor.
     *
     * @param ncFileList - the list of input netCDF files
     */
    public ProductStitcher(List<NetcdfFile> ncFileList) {
        this.ncFileList = ncFileList;

        setAllAttributesList();
        setAllDimensionsList();
        setAllBandVariablesLists();
        setAllTpVariablesLists();
        setRowToScanTimeMaps(true);
        setRowToScanTimeMaps(false);
        setStitchedProductSizeBands();
        setStitchedProductSizeTps();
        setStitchedProductRowToScanTimeMap(false, stitchedProductHeightBands);
        setStitchedProductRowToScanTimeMap(true, stitchedProductHeightTps);
    }

    /**
     * Validates source products:
     * - product names must all have same length
     * - product names must contain
     */
    public static void validateSourceProducts(String[] sourceFilePaths) throws IOException {
        final int sourceProductLength = sourceFilePaths[0].length();
        for (String sourceFilePath : sourceFilePaths) {
            if (sourceFilePath.length() != sourceProductLength) {
                throw new IOException("Inconsistent source products names (have not same length) - must be checked!");
            }
        }

        final int ccProductPrefixIndex = sourceFilePaths[0].indexOf("_CCL");
        for (String sourceFilePath : sourceFilePaths) {
            if (sourceFilePath.indexOf("_CCL") != ccProductPrefixIndex) {
                throw new IOException("Inconsistent source products names (CC identifiers not at same position) - must be checked!");
            }
            if (!sourceFilePath.substring(ccProductPrefixIndex, ccProductPrefixIndex + 6).
                    equals(sourceFilePaths[0].substring(ccProductPrefixIndex, ccProductPrefixIndex + 6))) {
                throw new IOException("Inconsistent source products names (CC identifiers different) - must be checked!");
            }
        }
    }

    /**
     * Writes the stitched product.
     *
     * @param ncResultFile - the file to write to.
     */
    public void writeStitchedProduct(File ncResultFile) throws IOException, InvalidRangeException {
        NetcdfFileWriteable outFile = null;
        final PrintWriterProgressMonitor pm = new PrintWriterProgressMonitor(System.out);
        pm.beginTask("Writing stitched product '" + ncResultFile.getAbsolutePath() + "' ...", 0);
        try {
            outFile = NetcdfFileWriteable.createNew(ncResultFile.getAbsolutePath(), false);

            // add global attributes from first product, exchange specific single attributes:
            addGlobalAttributes(allAttributesLists, outFile);

            // add dimensions to output: we have y, x, tp_y, tp_x in this sequence:
            final Dimension yDim = allDimensionsLists.get(0).get(0);
            final Dimension xDim = allDimensionsLists.get(0).get(1);
            final Dimension yTpDim = allDimensionsLists.get(0).get(2);
            final Dimension xTpDim = allDimensionsLists.get(0).get(3);
            addDimensions(outFile, yDim, xDim, yTpDim, xTpDim);

            // add bands and tie point variable attributes to output:
            addVariableAttributes(allBandVariablesLists, outFile, yDim, xDim);
            addVariableAttributes(allTpVariablesLists, outFile, yTpDim, xTpDim);

            // we need to call 'create' after all attributes and dimensions were added:
            try {
                // try in standard mode first, which may fail for large files...
                outFile.create();
            } catch (Exception e) {
                System.out.println("Switching to NetCDF 'large file' mode...");
                outFile.setLargeFile(true);
                outFile.create();
            }

            // add band and tie point data to output:
            writeVariables(allBandVariablesLists, bandRowToScanTimeMaps, outFile, false);
            writeVariables(allTpVariablesLists, tpRowToScanTimeMaps, outFile, true);

        } finally {
            if (null != outFile) {
                outFile.close();
            }
            pm.done();
        }
        System.out.println("Finished writing stitched product '" + ncResultFile.getAbsolutePath() + "'.");
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
                if (ProductStitcherValidation.isValidBandVariable(variable) ||
                        ProductStitcherValidation.isValidFlagBandVariable(variable) ||
                        ProductStitcherValidation.isValidMaskBandVariable(variable)) {
                    bandVariablesList.add(variable);
                }
            }
            allBandVariablesLists.add(bandVariablesList);
        }
    }

    private void setAllTpVariablesLists() {
        for (NetcdfFile ncFile : ncFileList) {
            final List<Variable> allVariablesList = ncFile.getVariables();
            List<Variable> tpVariablesList = new ArrayList<Variable>();
            for (Variable variable : allVariablesList) {
                if (ProductStitcherValidation.isValidTpVariable(variable)) {
                    tpVariablesList.add(variable);
                }
            }
            allTpVariablesLists.add(tpVariablesList);
        }
    }

    private void setRowToScanTimeMaps(boolean isTiepoints) {

        // sets up the maps which hold the scan times for each row in the original products

        List<Map<Integer, Long>> rowToScanTimeMaps = new ArrayList<Map<Integer, Long>>();
        for (NetcdfFile netcdfFile : ncFileList) {
            int yDim = -1;
            int xDim = -1;
            final String xDimName = (isTiepoints ? ProductStitcher.TP_DIMX_NAME : ProductStitcher.DIMX_NAME);
            final String yDimName = (isTiepoints ? ProductStitcher.TP_DIMY_NAME : ProductStitcher.DIMY_NAME);
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
                    startTime = ProductStitcherNetcdfUtils.getTimeAsLong(attribute);
                }
                if (attribute.getName().equals("stop_date")) {
                    stopTime = ProductStitcherNetcdfUtils.getTimeAsLong(attribute);
                }
            }

            if (startTime == -1 || stopTime == -1) {
                throw new IllegalStateException("Input file ' " + netcdfFile.getLocation() +
                                                        "': start/stop times cannot be parsed - check product!");
            }

            // interpolation:
            Map<Integer, Long> bandRowToScanTimeMap = new HashMap<Integer, Long>();
            for (int j = 0; j < yDim; j++) {
                if (isTiepoints) {
                    final long deltaT = (long) Math.floor((stopTime - startTime) * 1.0 / (yDim - 1));
                    final long scanTime = startTime + j * deltaT;
                    bandRowToScanTimeMap.put(j, scanTime);
                } else {
                    bandRowToScanTimeMap.put(j, startTime + j * (stopTime - startTime) / (yDim - 1));
                }
            }
            rowToScanTimeMaps.add(bandRowToScanTimeMap);
        }

        if (isTiepoints) {
            tpRowToScanTimeMaps = rowToScanTimeMaps;
        } else {
            bandRowToScanTimeMaps = rowToScanTimeMaps;
        }
    }

    private void setStitchedProductRowToScanTimeMap(boolean isTiepoints, int yDim) {

        // sets up the map which holds the scan time for each row (either related to regular or tie point grid)
        // in the stitched product

        final NetcdfFile firstNcFile = ncFileList.get(0);
        final NetcdfFile lastNcFile = ncFileList.get(ncFileList.size() - 1);

        final List<Attribute> firstGlobalAttributes = firstNcFile.getGlobalAttributes();
        long startTime = -1;
        long firstStopTime = -1;
        int firstYDim = -1;
        final List<Dimension> dimensions = firstNcFile.getDimensions();
        String yDimName = (isTiepoints ? ProductStitcher.TP_DIMY_NAME : ProductStitcher.DIMY_NAME);
        for (Dimension dimension : dimensions) {
            if (dimension.getName().equals(yDimName)) {
                firstYDim = dimension.getLength();
            }
        }
        for (Attribute attribute : firstGlobalAttributes) {
            if (attribute.getName().equals("start_date")) {
                startTime = ProductStitcherNetcdfUtils.getTimeAsLong(attribute);
            }
            if (attribute.getName().equals("stop_date")) {
                firstStopTime = ProductStitcherNetcdfUtils.getTimeAsLong(attribute);
            }
        }

        final List<Attribute> lastGlobalAttributes = lastNcFile.getGlobalAttributes();
        long stopTime = -1;
        for (Attribute attribute : lastGlobalAttributes) {
            if (attribute.getName().equals("stop_date")) {
                stopTime = ProductStitcherNetcdfUtils.getTimeAsLong(attribute);
                if (isTiepoints) {
                    // make sure last tiepoint row time does not exceed last one from last single product
                    final Map<Integer, Long> tpMap = tpRowToScanTimeMaps.get(tpRowToScanTimeMaps.size() - 1);
                    final Long stopTimeLimit = tpMap.get(tpMap.size() - 1);
                    stopTime = Math.min(stopTime, stopTimeLimit);
                }
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
        Map<Integer, Long> stitchedProductRowToScanTimeMap = new HashMap<Integer, Long>();
        for (int j = 0; j < yDim; j++) {
            if (isTiepoints) {
                final long deltaT = (long) Math.floor((firstStopTime - startTime) * 1.0 / (firstYDim - 1));
                final long scanTime = startTime + j * deltaT;
                if (scanTime <= stopTime) {
                    stitchedProductRowToScanTimeMap.put(j, scanTime);
                }
            } else {
                stitchedProductRowToScanTimeMap.put(j, startTime + j * (stopTime - startTime) / (yDim - 1));
            }
        }
        if (isTiepoints) {
            // this map holds the scan times on the interpolated tie point grid
            stitchedProductTpRowToScanTimeMap = stitchedProductRowToScanTimeMap;
            // set up the map with the neighbour scan times from the TPG of the original product...
            setStitchedProductTpRowToScanNeighbourTimesMap();
        } else {
            stitchedProductBandRowToScanTimeMap = stitchedProductRowToScanTimeMap;
        }
    }

    private void setStitchedProductSizeBands() {

        // sets the band data dimensions of the stitched product

        // go through row <--> scanTime
        for (int i = 0; i < bandRowToScanTimeMaps.size() - 1; i++) {
            final Map<Integer, Long> map = bandRowToScanTimeMaps.get(i);
            final Map<Integer, Long> nextMap = bandRowToScanTimeMaps.get(i + 1);
            // count until start time of next product is reached
            int j = 0;
            while (j < map.size() && map.get(j++) <= nextMap.get(0)) {
                stitchedProductHeightBands++;
            }
        }
        stitchedProductHeightBands += bandRowToScanTimeMaps.get(bandRowToScanTimeMaps.size() - 1).size();
        stitchedProductWidthBands = allDimensionsLists.get(0).get(1).getLength();
    }

    private void setStitchedProductSizeTps() {

        // sets the tie point data dimensions of the stitched product

        // go through row <--> scanTime
        for (int i = 0; i < tpRowToScanTimeMaps.size() - 1; i++) {
            final Map<Integer, Long> map = tpRowToScanTimeMaps.get(i);
            final Map<Integer, Long> nextMap = tpRowToScanTimeMaps.get(i + 1);
            int j = 0;
            // count until start time of next product is reached
            while (j < map.size() && map.get(j++) <= nextMap.get(0)) {
                stitchedProductHeightTps++;
            }
        }
        stitchedProductHeightTps += tpRowToScanTimeMaps.get(tpRowToScanTimeMaps.size() - 1).size();
        stitchedProductWidthTps = allDimensionsLists.get(0).get(3).getLength();
    }


    private static void addGlobalAttributes(List<List<Attribute>> allAttributesLists, NetcdfFileWriteable outFile) {

        // writes all global attributes to output netCDF file.

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

    private void addDimensions(NetcdfFileWriteable outFile, Dimension yDim, Dimension xDim, Dimension yTpDim, Dimension xTpDim) {

        // writes all dimensions to output netCDF file.

        yDim.setLength(stitchedProductHeightBands);
        xDim.setLength(stitchedProductWidthBands);
        outFile.addDimension(DIMY_NAME, yDim.getLength());
        outFile.addDimension(DIMX_NAME, xDim.getLength());
        stitchedProductHeightTps = Math.min(stitchedProductHeightTps, stitchedProductTpRowToScanTimeMap.size());
        yTpDim.setLength(stitchedProductHeightTps);
        xTpDim.setLength(stitchedProductWidthTps);
        outFile.addDimension(TP_DIMY_NAME, yTpDim.getLength());
        outFile.addDimension(TP_DIMX_NAME, xTpDim.getLength());
        outFile.addGlobalAttribute("TileSize", yDim.getLength() + ":" + xDim.getLength());
    }

    private static void addVariableAttributes(List<List<Variable>> variableLists,
                                              NetcdfFileWriteable outFile,
                                              Dimension yDim, Dimension xDim) throws IOException, InvalidRangeException {

        // writes all variable attributes to output netCDF file.

        List<Variable> firstVariables = variableLists.get(0);
        // loop over variables
        for (Variable variable : firstVariables) {
            // add band variables, take from first product
            if (variable.getDimensions().size() == 2) {
                outFile.addVariable(variable.getName(), variable.getDataType(), new Dimension[]{yDim, xDim});
                final List<Attribute> variableAttributes = variable.getAttributes();
                for (Attribute attribute : variableAttributes) {
                    outFile.addVariableAttribute(variable.getName(), attribute);
                }
            } else if (variable.getDimensions().size() == 0) {
                outFile.addVariable(variable.getName(), variable.getDataType(), "");
                final List<Attribute> variableAttributes = variable.getAttributes();
                for (Attribute attribute : variableAttributes) {
                    outFile.addVariableAttribute(variable.getName(), attribute);
                }
            }
        }
    }

    private void writeVariables(List<List<Variable>> variableLists,
                                List<Map<Integer, Long>> rowToScanTimeMaps,
                                NetcdfFileWriteable outFile,
                                boolean isTiepoints) throws IOException, InvalidRangeException {

        // writes all variables (band data and interpolated tie point data) to output netCDF file.

        final int width = (isTiepoints ? stitchedProductWidthTps : stitchedProductWidthBands);
        final int height = (isTiepoints ? stitchedProductHeightTps : stitchedProductHeightBands);

        // set up data buffers for all types which occur in L1P, L2R, L2W products
        ArrayFloat.D2 bandDataFloat = new ArrayFloat.D2(height, width);
        ArrayShort.D2 bandDataShort = new ArrayShort.D2(height, width);
        ArrayByte.D2 bandDataByte = new ArrayByte.D2(height, width);

        final List<Variable> firstProductBandVariables = variableLists.get(0);

        // loop over bands or tpg's
        for (Variable variable : firstProductBandVariables) {
            System.out.println("Stitching data of variable '" + variable.getName() + "'...");
            // loop over single products
            for (int i = 0; i < variableLists.size(); i++) {
                List<Variable> allBandVariables = variableLists.get(i);
                for (Variable variable2 : allBandVariables) {
                    if (variable2.getName().equals(variable.getName())) {
                        if (ProductStitcherValidation.isMetadataVariable(variable)) {
                            // skip
                        } else if (ProductStitcherValidation.isValidMaskBandVariable(variable)) {
                            // skip
                        } else {
                            // get data array for THIS variable and THIS single product
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
                            int sourceProductIndexPrev = 0;
                            int valuesRowIndex = 0;

                            // now loop over ALL rows:
                            for (int j = 0; j < height; j++) {
                                // search the right single product by row time
                                int sourceProductIndex = getSourceProductIndex(rowToScanTimeMaps, j, isTiepoints);

                                if (sourceProductIndex < 0 || sourceProductIndex > ncFileList.size()) {
                                    throw new IllegalStateException("Unknown status of source product start/stop times - cannot continue.");
                                }

                                if (sourceProductIndex > sourceProductIndexPrev) {
                                    valuesRowIndex = 0;
                                }

                                // if the current single product is the right one, loop over raster and set netcdf floatVals
                                if (sourceProductIndex == i) {
                                    if (valuesRowIndex >= variable2.getShape(0)) {
                                        valuesRowIndex--; // dirty fix for product length problem
                                    }
                                    for (int k = 0; k < width; k++) {
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
                        }
                    }
                }
            }

            System.out.println("...writing variable '" + variable.getName() + "'.");
            final List<Variable> allBandVariables = variableLists.get(0);
            for (Variable variable2 : allBandVariables) {
                if (variable2.getName().equals(variable.getName()) &&
                        !ProductStitcherValidation.isMetadataVariable(variable) &&
                        !ProductStitcherValidation.isValidMaskBandVariable(variable)) {
                    switch (variable2.getDataType()) {
                        case BYTE:
                            outFile.write(variable2.getName(), bandDataByte);
                            break;
                        case SHORT:
                            outFile.write(variable2.getName(), bandDataShort);
                            break;
                        case FLOAT:
                            if (isTiepoints) {
//                                ArrayFloat.D2 tpDataInterpol = interpolateTiePointData(bandDataFloat, width);
                                ArrayFloat.D2 tpDataInterpol;
                                if (ncFileList.size() > 1) {
                                    tpDataInterpol = interpolateTiePointData(bandDataFloat, width);
                                } else {
                                    tpDataInterpol = bandDataFloat;
                                }
                                outFile.write(variable2.getName(), tpDataInterpol);
                            } else {
                                outFile.write(variable2.getName(), bandDataFloat);
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("Data type '" + variable2.getDataType().name() + "' not supported.");
                    }
                }
            }
        }
    }

    private ArrayFloat.D2 interpolateTiePointData(ArrayFloat.D2 tpData, int width) {
        // set the tie point data row-by-row on the regular tie point grid of the stitched product:
        // - get the data from the tie points of the original products
        // - in general, there is an irregular row-shift at the stitch boundaries, which requires
        // interpolation of data from 'previous' and 'next' row of the original products.
        // Scan time is used to identify the fractions to interpolate.
        ArrayFloat.D2 tpDataInterpol = new ArrayFloat.D2(stitchedProductTpRowToScanTimeMap.size(), width);
        for (int j = 0; j < stitchedProductTpRowToScanTimeMap.size() - 1; j++) {
            final Long scanTime = stitchedProductTpRowToScanTimeMap.get(j);
            final TimeInterval scanTimeInterval = stitchedProductTpRowToScanNeighbourTimesMap.get(scanTime);
            final long prevTime = scanTimeInterval.getStartTime();
            final long nextTime = scanTimeInterval.getStopTime();
            for (int i = 0; i < width; i++) {
                final float frac = (scanTime - prevTime) * 1.0f / (nextTime - prevTime);
                final float result = tpData.get(j, i) + frac * (tpData.get(j + 1, i) - tpData.get(j, i));
                tpDataInterpol.set(j, i, result);
            }
        }

        // if we have more than one row to interpolate: for the last row, use the delta from the previous step for interpolation
        if (stitchedProductTpRowToScanTimeMap.size() > 2) {
            for (int i = 0; i < width; i++) {
                final float lastResultDelta = tpDataInterpol.get(stitchedProductTpRowToScanTimeMap.size() - 2, i) -
                        tpDataInterpol.get(stitchedProductTpRowToScanTimeMap.size() - 3, i);
                final float lastResult = tpDataInterpol.get(stitchedProductTpRowToScanTimeMap.size() - 2, i) + lastResultDelta;
                tpDataInterpol.set(stitchedProductTpRowToScanTimeMap.size() - 1, i, lastResult);
            }
        }
        return tpDataInterpol;
    }

    private int getSourceProductIndex(List<Map<Integer, Long>> rowToScanTimeMaps, int rowIndex, boolean isTiepoints) {
        int sourceProductIndex = -1;
        Map<Integer, Long> stitchedProductRowToScanTimeMap;
        if (isTiepoints) {
            stitchedProductRowToScanTimeMap = stitchedProductTpRowToScanTimeMap;
        } else {
            stitchedProductRowToScanTimeMap = stitchedProductBandRowToScanTimeMap;
        }

        if (rowIndex < stitchedProductRowToScanTimeMap.size()) {
            long targetProductTime = stitchedProductRowToScanTimeMap.get(rowIndex);
            for (int k = rowToScanTimeMaps.size() - 1; k >= 0; k--) {
                Map<Integer, Long> map = rowToScanTimeMaps.get(k);
                final int mapStartIndex = 0;
                long startTime = map.get(mapStartIndex);
                long stopTime = map.get(map.size() - 1);
                if (startTime <= targetProductTime && targetProductTime <= stopTime) {
                    sourceProductIndex = k;
                    break;
                }
            }
        }

        return sourceProductIndex;
    }

    private void setStitchedProductTpRowToScanNeighbourTimesMap() {
        stitchedProductTpRowToScanNeighbourTimesMap = new HashMap<Long, TimeInterval>();
        for (int j = 0; j < stitchedProductTpRowToScanTimeMap.size(); j++) {
            long sourceProductTime = stitchedProductTpRowToScanTimeMap.get(j);
            int sourceProductIndex = getSourceProductIndex(tpRowToScanTimeMaps, j, true);
            if (sourceProductIndex == -1) {
                sourceProductIndex = getSourceProductIndex(tpRowToScanTimeMaps, j, true);
            }
            Map<Integer, Long> map = tpRowToScanTimeMaps.get(sourceProductIndex);
            for (int k = 0; k < map.size() - 1; k++) {
                final long t1 = map.get(k);
                final long t2 = map.get(k + 1);
                if (t1 <= sourceProductTime && sourceProductTime <= t2) {
                    stitchedProductTpRowToScanNeighbourTimesMap.put(sourceProductTime, new TimeInterval(t1, t2));
                    break;
                }
            }
        }
    }

    private class TimeInterval {
        long startTime;
        long stopTime;

        private TimeInterval(long startTime, long stopTime) {
            this.startTime = startTime;
            this.stopTime = stopTime;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getStopTime() {
            return stopTime;
        }
    }
}