package org.esa.beam.coastcolour.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Product Stitcher test class
 * Date: 12.03.12
 * Time: 13:54
 *
 * @author olafd
 */
public class ProductStitcherTest {

    private NetcdfFile ncFile1;
    private NetcdfFile ncFile2;
    private NetcdfFile ncFile3;
    private List<List<Variable>> variableListAll;

    @Before
    public void setUp() throws Exception {
        final String ncFilename1 = getClass().getResource("stitch_test_l2r_small_part1.nc").getFile();
        final String ncFilename2 = getClass().getResource("stitch_test_l2r_small_part2.nc").getFile();
        final String ncFilename3 = getClass().getResource("stitch_test_l2r_small_part3.nc").getFile();
        ncFile1 = NetcdfFile.openInMemory(ncFilename1);
        ncFile2 = NetcdfFile.openInMemory(ncFilename2);
        ncFile3 = NetcdfFile.openInMemory(ncFilename3);

        final Group rootGroup1 = ncFile1.getRootGroup();
        final Group rootGroup2 = ncFile2.getRootGroup();
        final Group rootGroup3 = ncFile3.getRootGroup();

        final List<Variable> variableList1 = rootGroup1.getVariables();
        final List<Variable> variableList2 = rootGroup2.getVariables();
        final List<Variable> variableList3 = rootGroup3.getVariables();
        variableListAll = new ArrayList<List<Variable>>();
        variableListAll.add(variableList1);
        variableListAll.add(variableList2);
        variableListAll.add(variableList3);
    }

    @After
    public void tearDown() throws Exception {
        ncFile1.close();
        ncFile2.close();
        ncFile3.close();
    }

    @Test
    public void testGetL2RVariablesFromNetcdf() throws Exception {
        final Group rootGroup = ncFile1.getRootGroup();
        final List<Variable> variableList = rootGroup.getVariables();
        assertNotNull(variableList);
        assertEquals(75, variableList.size()); // netcdf variables contain metadata, bands, tiepoints and masks!

        assertEquals("metadata", variableList.get(0).getName());
        assertEquals("reflec_8", variableList.get(8).getName());
        assertEquals("norm_refl_12", variableList.get(23).getName());
        assertEquals("atm_press", variableList.get(47).getName());
        assertEquals("l2r_cc_solzen_mask", variableList.get(62).getName());
        assertEquals("l1b_invalid_mask", variableList.get(74).getName());

        final Variable reflec8 = variableList.get(8);
        assertEquals(2, reflec8.getRank());
        assertEquals(4, reflec8.getElementSize());
        assertEquals(12, reflec8.getShape(0));
        assertEquals(120, reflec8.getSize());
        assertEquals(8, reflec8.getAttributes().size());
        assertEquals("long_name", reflec8.getAttributes().get(0).getName());
        assertEquals("wavelength", reflec8.getAttributes().get(4).getName());
        assertEquals(Float.class.getSimpleName().toLowerCase(), reflec8.getDataType().getClassType().getSimpleName());
        assertEquals(2, reflec8.getDimensions().size());
        assertEquals(12, reflec8.getDimension(0).getLength());
        assertEquals(10, reflec8.getDimension(1).getLength());
        assertEquals(2, reflec8.getRanges().size());
        assertEquals(2, reflec8.getShape().length);
        assertEquals(12, reflec8.getShape()[0]);
        assertEquals(10, reflec8.getShape()[1]);
    }

    @Test
    public void testGetRowToProductIndexMap() throws Exception {
        Vector<float[][]> latVector = new Vector<float[][]>();
        Vector<float[][]> lonVector = new Vector<float[][]>();
        Map<Integer, Integer> rowToProductIndexMap = getRowToProductIndexTestMap(variableListAll, latVector, lonVector);

        assertNotNull(rowToProductIndexMap);
        assertEquals(30, rowToProductIndexMap.size());
        assertEquals(0, rowToProductIndexMap.get(new Integer(0)).intValue());
        assertEquals(0, rowToProductIndexMap.get(new Integer(4)).intValue());
        assertEquals(1, rowToProductIndexMap.get(new Integer(12)).intValue());
        assertEquals(1, rowToProductIndexMap.get(new Integer(15)).intValue());
        assertEquals(2, rowToProductIndexMap.get(new Integer(21)).intValue());
        assertEquals(2, rowToProductIndexMap.get(new Integer(29)).intValue());

        lonVector.remove(2);
        try {
            rowToProductIndexMap = ProductStitcherUtils.getRowToProductIndexMap(latVector, lonVector);
        } catch (Exception e) {
            assertEquals(IllegalStateException.class, e.getClass());
            assertEquals(e.getMessage(), "Cannot stitch products - mismatch in latitude and longitude array sizes.");
        }
    }

    @Test
    public void testGetStitchedProductHeight() throws Exception {
        List<List<Variable>> overallVariableList = variableListAll;
        Vector<float[][]> latVector = new Vector<float[][]>();
        Vector<float[][]> lonVector = new Vector<float[][]>();
        Map<Integer, Integer> rowToProductIndexMap = getRowToProductIndexTestMap(overallVariableList, latVector, lonVector);
        assertEquals(30, rowToProductIndexMap.size());
    }

    @Test
    public void testWriteStitchedProduct() throws Exception {
        Vector<float[][]> latVector = new Vector<float[][]>();
        Vector<float[][]> lonVector = new Vector<float[][]>();
        Map<Integer, Integer> rowToProductIndexMap = getRowToProductIndexTestMap(variableListAll, latVector, lonVector);

//        final String ncResultFilename = getClass().getResource("stitch_test_l2r_small_result.nc").getFile();
//        ProductStitcherUtils.writeStitchedProduct(ncResultFilename, variableListAll);

//            final int NLAT = 6;
//            final int NLON = 12;
//            final float SAMPLE_PRESSURE = 900.0f;
//            final float SAMPLE_TEMP = 9.0f;
//            final float START_LAT = 25.0f;
//            final float START_LON = -125.0f;
//
//
//            // Create the file.
//            String filename = "sfc_pres_temp.nc";
//            NetcdfFileWriteable dataFile = null;
//
//            try {
//                //Create new netcdf-3 file with the given filename
//                dataFile = NetcdfFileWriteable.createNew(filename, false);
//
//                // In addition to the latitude and longitude dimensions, we will
//                // also create latitude and longitude netCDF variables which will
//                // hold the actual latitudes and longitudes. Since they hold data
//                // about the coordinate system, the netCDF term for these is:
//                // "coordinate variables."
//                Dimension latDim = dataFile.addDimension("latitude", NLAT );
//                Dimension lonDim = dataFile.addDimension("longitude", NLON );
//                ArrayList dims =  null;
//
//
//                dataFile.addVariable("latitude", DataType.FLOAT, new Dimension[] {latDim});
//                dataFile.addVariable("longitude", DataType.FLOAT, new Dimension[] {lonDim});
//
//                // Define units attributes for coordinate vars. This attaches a
//                // text attribute to each of the coordinate variables, containing
//                // the units.
//
//                dataFile.addVariableAttribute("longitude", "units", "degrees_east");
//                dataFile.addVariableAttribute("latitude", "units", "degrees_north");
//
//                // Define the netCDF data variables.
//                dims =  new ArrayList();
//                dims.add(latDim);
//                dims.add(lonDim);
//                dataFile.addVariable("pressure", DataType.FLOAT, dims);
//                dataFile.addVariable("temperature", DataType.FLOAT, dims);
//
//                // Define units attributes for variables.
//                dataFile.addVariableAttribute("pressure", "units", "hPa");
//                dataFile.addVariableAttribute("temperature", "units", "celsius");
//
//                // Write the coordinate variable data. This will put the latitudes
//                // and longitudes of our data grid into the netCDF file.
//                dataFile.create();
//
//
//                ArrayFloat.D1 dataLat = new ArrayFloat.D1(latDim.getLength());
//                ArrayFloat.D1 dataLon = new ArrayFloat.D1(lonDim.getLength());
//
//                // Create some pretend data. If this wasn't an example program, we
//                // would have some real data to write, for example, model
//                // output.
//                int i,j;
//
//
//                for (i=0; i<latDim.getLength(); i++) {
//                    dataLat.set(i,  START_LAT + 5.f * i );
//                }
//
//                for (j=0; j<lonDim.getLength(); j++) {
//                    dataLon.set(j,  START_LON + 5.f * j );
//                }
//
//
//                dataFile.write("latitude", dataLat);
//                dataFile.write("longitude", dataLon);
//
//                // Create the pretend data. This will write our surface pressure and
//                // surface temperature data.
//
//                ArrayFloat.D2 dataTemp = new ArrayFloat.D2(latDim.getLength(), lonDim.getLength());
//                ArrayFloat.D2 dataPres = new ArrayFloat.D2(latDim.getLength(), lonDim.getLength());
//
//                for (i=0; i<latDim.getLength(); i++) {
//                    for (j=0; j<lonDim.getLength(); j++) {
//                        dataTemp.set(i,j,  SAMPLE_TEMP + .25f * (j * NLAT + i));
//                        dataPres.set(i,j,  SAMPLE_PRESSURE + (j * NLAT + i));
//                    }
//                }
//
//                int[] origin = new int[2];
//
//                dataFile.write("pressure", origin, dataPres);
//                dataFile.write("temperature", origin, dataTemp);
//
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            } catch (InvalidRangeException e) {
//                e.printStackTrace();
//            } finally {
//                if (null != dataFile)
//                    try {
//                        dataFile.close();
//                    } catch (IOException ioe) {
//                        ioe.printStackTrace();
//                    }
//            }
//            System.out.println( "*** SUCCESS writing example file sfc_pres_temp.nc!" );
//        }
//
    }

    private Map<Integer, Integer> getRowToProductIndexTestMap(List<List<Variable>> overallVariableList, Vector<float[][]> latVector, Vector<float[][]> lonVector) {
        for (List<Variable> variableList : overallVariableList) {
            for (Variable variable : variableList) {
                if ("lat".equals(variable.getName())) {
                    latVector.add(ProductStitcherUtils.getFloat2DArrayFromNetcdfVariable(variable));
                }
                if ("lon".equals(variable.getName())) {
                    lonVector.add(ProductStitcherUtils.getFloat2DArrayFromNetcdfVariable(variable));
                }
            }
        }
        return ProductStitcherUtils.getRowToProductIndexMap(latVector, lonVector);
    }

}
