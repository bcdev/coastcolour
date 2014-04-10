package org.esa.beam.coastcolour.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ucar.nc2.NetcdfFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * Product Stitcher test class
 *
 * @author olafd
 */
public class ProductStitcherTest {

    private NetcdfFile ncFile1;
    private NetcdfFile ncFile2;
    private NetcdfFile ncFile3;

    private ProductStitcher testStitcher;

    @Before
    public void setUp() throws Exception {
        final String ncFilename1 = getClass().getResource("stitch_test_l2r_small_part1.nc").getFile();
        final String ncFilename2 = getClass().getResource("stitch_test_l2r_small_part2.nc").getFile();
        final String ncFilename3 = getClass().getResource("stitch_test_l2r_small_part3.nc").getFile();
        ncFile1 = NetcdfFile.open(ncFilename1);
        ncFile2 = NetcdfFile.open(ncFilename2);
        ncFile3 = NetcdfFile.open(ncFilename3);

        List<NetcdfFile> ncFileList = new ArrayList<NetcdfFile>();
        ncFileList.add(ncFile1);
        ncFileList.add(ncFile2);
        ncFileList.add(ncFile3);

        testStitcher = new ProductStitcher(ncFileList);
    }

    @After
    public void tearDown() throws Exception {
        ncFile1.close();
        ncFile2.close();
        ncFile3.close();
    }

    @Test
    public void testGlobalAttributes() throws Exception {
        assertNotNull(testStitcher.allAttributesLists);
        assertEquals(3, testStitcher.allAttributesLists.size());
        assertEquals(10, testStitcher.allAttributesLists.get(0).size());
        assertEquals(10, testStitcher.allAttributesLists.get(1).size());
        assertEquals(10, testStitcher.allAttributesLists.get(2).size());
        assertEquals("TileSize", testStitcher.allAttributesLists.get(0).get(1).getName());
        assertEquals("12:10", testStitcher.allAttributesLists.get(0).get(1).getStringValue());
        assertEquals("metadata_version", testStitcher.allAttributesLists.get(1).get(4).getName());
        assertEquals("beam", testStitcher.allAttributesLists.get(1).get(3).getStringValue());
        assertEquals("stop_date", testStitcher.allAttributesLists.get(2).get(8).getName());
        assertEquals("MERIS CoastColour L2R", testStitcher.allAttributesLists.get(2).get(9).getStringValue());
    }

    @Test
    public void testDimensions() throws Exception {
        assertNotNull(testStitcher.allDimensionsLists);
        assertEquals(3, testStitcher.allDimensionsLists.size());
        assertEquals(4, testStitcher.allDimensionsLists.get(0).size());
        assertEquals(4, testStitcher.allDimensionsLists.get(1).size());
        assertEquals(4, testStitcher.allDimensionsLists.get(2).size());
        assertEquals("y", testStitcher.allDimensionsLists.get(0).get(0).getName());
        assertEquals(12, testStitcher.allDimensionsLists.get(0).get(0).getLength());
        assertEquals("x", testStitcher.allDimensionsLists.get(1).get(1).getName());
        assertEquals(10, testStitcher.allDimensionsLists.get(1).get(1).getLength());
        assertEquals("tp_y", testStitcher.allDimensionsLists.get(1).get(2).getName());
        assertEquals(3, testStitcher.allDimensionsLists.get(1).get(2).getLength());
        assertEquals("tp_x", testStitcher.allDimensionsLists.get(2).get(3).getName());
        assertEquals(3, testStitcher.allDimensionsLists.get(2).get(3).getLength());
    }

    @Test
    public void testBandVariables() throws Exception {
        assertNotNull(testStitcher.allBandVariablesLists);
        assertEquals(3, testStitcher.allBandVariablesLists.size());
        assertEquals(60, testStitcher.allBandVariablesLists.get(0).size());
        assertEquals(60, testStitcher.allBandVariablesLists.get(1).size());
        assertEquals(60, testStitcher.allBandVariablesLists.get(2).size());
        assertEquals("metadata", testStitcher.allBandVariablesLists.get(0).get(0).getName());
        assertEquals("reflec_7", testStitcher.allBandVariablesLists.get(0).get(7).getName());
        assertEquals("Water leaving radiance reflectance at 664,573 nm",
                testStitcher.allBandVariablesLists.get(0).get(7).getDescription());
        assertEquals(2, testStitcher.allBandVariablesLists.get(0).get(7).getRank());
        assertEquals(2, testStitcher.allBandVariablesLists.get(0).get(7).getShape().length);
        assertEquals(12, testStitcher.allBandVariablesLists.get(0).get(7).getShape()[0]);
        assertEquals(10, testStitcher.allBandVariablesLists.get(0).get(7).getShape()[1]);
        assertEquals("reflec_5", testStitcher.allBandVariablesLists.get(1).get(5).getName());
        assertEquals("norm_refl_6", testStitcher.allBandVariablesLists.get(1).get(18).getName());
        assertEquals("norm_refl_9", testStitcher.allBandVariablesLists.get(2).get(21).getName());
        assertEquals("lat", testStitcher.allBandVariablesLists.get(2).get(33).getName());
    }

    @Test
    public void testTpVariables() throws Exception {
        assertNotNull(testStitcher.allTpVariablesLists);
        assertEquals(3, testStitcher.allTpVariablesLists.size());
        assertEquals(15, testStitcher.allTpVariablesLists.get(0).size());
        assertEquals(15, testStitcher.allTpVariablesLists.get(1).size());
        assertEquals(15, testStitcher.allTpVariablesLists.get(2).size());
        assertEquals("dem_alt", testStitcher.allTpVariablesLists.get(0).get(2).getName());
        assertNull(testStitcher.allTpVariablesLists.get(0).get(2).getDescription());
        assertEquals(2, testStitcher.allTpVariablesLists.get(0).get(7).getRank());
        assertEquals(2, testStitcher.allTpVariablesLists.get(0).get(7).getShape().length);
        assertEquals(3, testStitcher.allTpVariablesLists.get(0).get(7).getShape()[0]);
        assertEquals(3, testStitcher.allTpVariablesLists.get(0).get(7).getShape()[1]);
        assertEquals("lon_corr", testStitcher.allTpVariablesLists.get(1).get(5).getName());
        assertEquals("view_zenith", testStitcher.allTpVariablesLists.get(1).get(8).getName());
        assertEquals("merid_wind", testStitcher.allTpVariablesLists.get(2).get(11).getName());
        assertEquals("ozone", testStitcher.allTpVariablesLists.get(2).get(13).getName());
    }

    @Test
    public void testSetRowToScanTimeMaps() throws Exception {
        assertNotNull(testStitcher.bandRowToScanTimeMaps);
        assertEquals(3, testStitcher.bandRowToScanTimeMaps.size());
        assertEquals(12, testStitcher.bandRowToScanTimeMaps.get(0).size());

        assertNotNull(testStitcher.tpRowToScanTimeMaps);
        assertEquals(3, testStitcher.tpRowToScanTimeMaps.size());
        assertEquals(3, testStitcher.tpRowToScanTimeMaps.get(0).size());
    }

    @Test
    public void testStitchedProductSize() throws Exception {
        assertEquals(32, testStitcher.stitchedProductHeightBands);
        assertEquals(7, testStitcher.stitchedProductHeightTps);
        assertEquals(10, testStitcher.stitchedProductWidthBands);
        assertEquals(3, testStitcher.stitchedProductWidthTps);
    }

    @Test
    public void testStitchedProductRowToScanTimeMap() throws Exception {
        assertNotNull(testStitcher.stitchedProductBandRowToScanTimeMap);
        assertEquals(32, testStitcher.stitchedProductBandRowToScanTimeMap.size());
        assertNotNull(testStitcher.stitchedProductTpRowToScanTimeMap);
        assertEquals(6, testStitcher.stitchedProductTpRowToScanTimeMap.size());
    }

    @Test
    public void testGetStitchedProductFileName() throws Exception {

        String[] inputProducts = new String[]{
                "MER_FRS_CCL2W_20120309_074740_000000563112_00265_52434_0001.nc",
                "MER_FRS_CCL2W_20120309_074759_000001893112_00265_52434_0001.nc",
                "MER_FRS_CCL2W_20120309_075032_000000873112_00265_52434_0001.nc"
        };

        String stitchedProductFileName = ProductStitcherNetcdfUtils.getStitchedProductFileName(inputProducts);
        assertEquals("MER_FRS_CCL2W_20120309_074740_000002593112_00265_52434_0001.nc", stitchedProductFileName);

        // should work for any sequence:
        inputProducts = new String[]{
                "MER_FRS_CCL2W_20120309_074759_000001893112_00265_52434_0001.nc",
                "MER_FRS_CCL2W_20120309_074740_000000563112_00265_52434_0001.nc",
                "MER_FRS_CCL2W_20120309_075032_000000873112_00265_52434_0001.nc"
        };

        stitchedProductFileName = ProductStitcherNetcdfUtils.getStitchedProductFileName(inputProducts);
        assertEquals("MER_FRS_CCL2W_20120309_074740_000002593112_00265_52434_0001.nc", stitchedProductFileName);
    }

    @Test
    public void testValidateSourceProducts() {
        // correct products
        String[] inputProducts = new String[]{
                "C://test//MER_FRS_CCL2W_20120309_074759_000001893112_00265_52434_0001.nc",
                "C://test//MER_FRS_CCL2W_20120309_074740_000000563112_00265_52434_0001.nc",
                "C://test//MER_FRS_CCL2W_20120309_075032_000000873112_00265_52434_0001.nc"
        };

        try {
            testStitcher.validateSourceProducts(inputProducts);
        } catch (IOException e) {
            fail(e.getMessage());
        }

        // mix of L2W and L2R - bad
        inputProducts = new String[]{
                "C://test//MER_FRS_CCL2W_20120309_074759_000001893112_00265_52434_0001.nc",
                "C://test//MER_FRS_CCL2R_20120309_074740_000000563112_00265_52434_0001.nc",
                "C://test//MER_FRS_CCL2W_20120309_075032_000000873112_00265_52434_0001.nc"
        };

        try {
            testStitcher.validateSourceProducts(inputProducts);
        } catch (IOException e) {
            assertEquals("Inconsistent source products names (CC identifiers different) - must be checked!", e.getMessage());
        }

        // product has incorrect name
        inputProducts = new String[]{
                "C://test//MER_FRS_CCL2W_20120309_074759_000001893112_00265_52434_0001.nc",
                "C://test//MER_FRS_CCL2W_20120309_074740_000000563112_00265_52434_0001.nc",
                "C://test//MER_FRS_CCL2W_20120309_bla.nc"
        };

        try {
            testStitcher.validateSourceProducts(inputProducts);
        } catch (IOException e) {
            assertEquals("Inconsistent source products names (have not same length) - must be checked!", e.getMessage());
        }
    }

}
