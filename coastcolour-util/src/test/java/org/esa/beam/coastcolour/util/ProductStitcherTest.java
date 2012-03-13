package org.esa.beam.coastcolour.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ucar.nc2.*;

import java.io.File;
import java.net.URL;
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
    private List<List<Variable>> allBandVariablesLists;
    private List<List<Variable>> allTpVariablesLists;
    private List<Variable> bandVariableList1;
    private List<Variable> bandVariableList2;
    private List<Variable> bandVariableList3;
    private List<Variable> tpVariableList1;
    private List<Variable> tpVariableList2;
    private List<Variable> tpVariableList3;
    private List<List<Dimension>> allDimensionsLists;
    private List<List<Attribute>> allAttributesLists;

    @Before
    public void setUp() throws Exception {
        final String ncFilename1 = getClass().getResource("stitch_test_l2r_small_part1.nc").getFile();
        final String ncFilename2 = getClass().getResource("stitch_test_l2r_small_part2.nc").getFile();
        final String ncFilename3 = getClass().getResource("stitch_test_l2r_small_part3.nc").getFile();
        ncFile1 = NetcdfFile.openInMemory(ncFilename1);
        ncFile2 = NetcdfFile.openInMemory(ncFilename2);
        ncFile3 = NetcdfFile.openInMemory(ncFilename3);

        final List<Attribute> attributes1 = ncFile1.getGlobalAttributes();
        final List<Attribute> attributes2 = ncFile2.getGlobalAttributes();
        final List<Attribute> attributes3 = ncFile3.getGlobalAttributes();
        allAttributesLists = new ArrayList<List<Attribute>>();
        allAttributesLists.add(attributes1);
        allAttributesLists.add(attributes2);
        allAttributesLists.add(attributes3);

        final List<Dimension> dimensions1 = ncFile1.getDimensions();
        final List<Dimension> dimensions2 = ncFile2.getDimensions();
        final List<Dimension> dimensions3 = ncFile3.getDimensions();
        allDimensionsLists = new ArrayList<List<Dimension>>();
        allDimensionsLists.add(dimensions1);
        allDimensionsLists.add(dimensions2);
        allDimensionsLists.add(dimensions3);

        List<Variable> variableList1 = ncFile1.getVariables();
        List<Variable> variableList2 = ncFile2.getVariables();
        List<Variable> variableList3 = ncFile3.getVariables();

        bandVariableList1 = getBandVariablesList(variableList1);
        bandVariableList2 = getBandVariablesList(variableList2);
        bandVariableList3 = getBandVariablesList(variableList3);
        allBandVariablesLists = new ArrayList<List<Variable>>();
        allBandVariablesLists.add(bandVariableList1);
        allBandVariablesLists.add(bandVariableList2);
        allBandVariablesLists.add(bandVariableList3);

        tpVariableList1 = getTpVariablesList(variableList1);
        tpVariableList2 = getTpVariablesList(variableList2);
        tpVariableList3 = getTpVariablesList(variableList3);
        allTpVariablesLists = new ArrayList<List<Variable>>();
        allTpVariablesLists.add(tpVariableList1);
        allTpVariablesLists.add(tpVariableList2);
        allTpVariablesLists.add(tpVariableList3);
    }

    @After
    public void tearDown() throws Exception {
        ncFile1.close();
        ncFile2.close();
        ncFile3.close();
    }

    @Test
    public void testGetBandVariablesList() throws Exception {
        final List<Variable> bandVariablesList = ProductStitcher.getBandVariablesList(bandVariableList1);
        assertNotNull(bandVariablesList);
        assertEquals(34, bandVariablesList.size());
        assertEquals("y", bandVariablesList.get(0).getDimension(0).getName());
        assertEquals("x", bandVariablesList.get(0).getDimension(1).getName());
        assertEquals("y", bandVariablesList.get(13).getDimension(0).getName());
        assertEquals("x", bandVariablesList.get(13).getDimension(1).getName());
        assertEquals("y", bandVariablesList.get(33).getDimension(0).getName());
        assertEquals("x", bandVariablesList.get(33).getDimension(1).getName());
    }

    @Test
    public void testGetTpVariablesList() throws Exception {
        final List<Variable> tpVariablesList = ProductStitcher.getTpVariablesList(tpVariableList1);
        assertNotNull(tpVariablesList);
        assertEquals(15, tpVariablesList.size());
        assertEquals("tp_y", tpVariablesList.get(0).getDimension(0).getName());
        assertEquals("tp_x", tpVariablesList.get(0).getDimension(1).getName());
        assertEquals("tp_y", tpVariablesList.get(3).getDimension(0).getName());
        assertEquals("tp_x", tpVariablesList.get(3).getDimension(1).getName());
        assertEquals("tp_y", tpVariablesList.get(14).getDimension(0).getName());
        assertEquals("tp_x", tpVariablesList.get(14).getDimension(1).getName());
    }

    @Test
    public void testGetNetcdfVariableFloat2DDataFromSingleProducts() throws Exception {
        Vector<float[][]> dataVector = ProductStitcher.getNetcdfVariableFloat2DDataFromSingleProducts(allBandVariablesLists, "reflec_8");
        assertEquals(3, dataVector.size());
        assertEquals(12, dataVector.get(0).length);
        assertEquals(12, dataVector.get(1).length);
        assertEquals(12, dataVector.get(2).length);
        assertEquals(10, dataVector.get(0)[0].length);
        assertEquals(0.0007733037f, dataVector.get(0)[0][0], 1.E-6);
        assertEquals(0.0008143207f, dataVector.get(0)[3][7], 1.E-6);
        assertEquals(0.000782416f, dataVector.get(1)[4][9], 1.E-6);
        assertEquals(0.0008488487f, dataVector.get(2)[11][1], 1.E-6);
        assertEquals(0.0008642305f, dataVector.get(2)[8][5], 1.E-6);
    }

    @Test
    public void testGetL2RVariablesFromNetcdf() throws Exception {
        // todo: remove this test later (no own functionality tested)
        final List<Variable> variableList = ncFile1.getVariables();
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
        assertEquals("y", reflec8.getDimension(0).getName());
        assertEquals("x", reflec8.getDimension(1).getName());
        assertEquals(2, reflec8.getRanges().size());
        assertEquals(2, reflec8.getShape().length);
        assertEquals(12, reflec8.getShape()[0]);
        assertEquals(10, reflec8.getShape()[1]);
    }

    @Test
    public void testGetBandRowToProductIndexMap() throws Exception {
        Map<Integer, Integer> bandRowToProductIndexMap = ProductStitcher.getBandRowToProductIndexMap(allBandVariablesLists);

        assertNotNull(bandRowToProductIndexMap);
        assertEquals(30, bandRowToProductIndexMap.size());
        assertEquals(0, bandRowToProductIndexMap.get(new Integer(0)).intValue());
        assertEquals(0, bandRowToProductIndexMap.get(new Integer(4)).intValue());
        assertEquals(1, bandRowToProductIndexMap.get(new Integer(12)).intValue());
        assertEquals(1, bandRowToProductIndexMap.get(new Integer(15)).intValue());
        assertEquals(2, bandRowToProductIndexMap.get(new Integer(21)).intValue());
        assertEquals(2, bandRowToProductIndexMap.get(new Integer(29)).intValue());
    }

    @Test
    public void testGetTpRowToProductIndexMap() throws Exception {
        Map<Integer, Integer> tpRowToProductIndexMap = ProductStitcher.getTpRowToProductIndexMap(allTpVariablesLists);

        assertNotNull(tpRowToProductIndexMap);
        assertEquals(3, tpRowToProductIndexMap.size());
        assertEquals(2, tpRowToProductIndexMap.get(new Integer(0)).intValue());
    }


    @Test
    public void testGetStitchedProductHeight() throws Exception {
        Map<Integer, Integer> rowToProductIndexMap = ProductStitcher.getBandRowToProductIndexMap(allBandVariablesLists);
        assertEquals(30, rowToProductIndexMap.size());
    }

//    @Test
    public void testWriteStitchedProduct() throws Exception {
        Map<Integer, Integer> bandRowToProductIndexMap = ProductStitcher.getBandRowToProductIndexMap(allBandVariablesLists);
        Map<Integer, Integer> tpRowToProductIndexMap = ProductStitcher.getTpRowToProductIndexMap(allTpVariablesLists);

//        final File resultFile = File.createTempFile("stitch_test_l2r_small_result", ".nc");
        // todo change this path!
        final File resultFile = new File("C:/Users/olafd/coastcolour/stitch/testdata/stitch_test_l2r_small_result.nc");
        ProductStitcher.writeStitchedProduct(resultFile,
                                             allAttributesLists,
                                             allDimensionsLists,
                                             allBandVariablesLists,
                                             allTpVariablesLists,
                                             bandRowToProductIndexMap,
                                             tpRowToProductIndexMap);
        // todo continue


    }



    private List<Variable> getBandVariablesList(List<Variable> allVariablesList) {
        List<Variable> bandVariableList = new ArrayList<Variable>();
        for (Variable variable : allVariablesList) {
            if (variable.getDimensions().size() == 2 &&
                    variable.getDimension(0).getName().equals("y") && variable.getDimension(1).getName().equals("x")) {
                bandVariableList.add(variable);
            }
        }
        return bandVariableList;
    }

    private List<Variable> getTpVariablesList(List<Variable> allVariablesList) {
        List<Variable> bandVariableList = new ArrayList<Variable>();
        for (Variable variable : allVariablesList) {
            if (variable.getDimensions().size() == 2 &&
                    variable.getDimension(0).getName().equals("tp_y") && variable.getDimension(1).getName().equals("tp_x")) {
                bandVariableList.add(variable);
            }
        }
        return bandVariableList;
    }


}
