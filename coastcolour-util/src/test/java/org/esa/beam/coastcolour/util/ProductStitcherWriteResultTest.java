package org.esa.beam.coastcolour.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Product Stitcher test class
 * Date: 12.03.12
 * Time: 13:54
 *
 * @author olafd
 */
public class ProductStitcherWriteResultTest {

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
        final String srcRootDir = "C:/Users/olafd/coastcolour/stitch/testdata/";
//        final String ncFilename1 = getClass().getResource("stitch_test_l2r_small_part1.nc").getFile();
//        final String ncFilename2 = getClass().getResource("stitch_test_l2r_small_part2.nc").getFile();
//        final String ncFilename3 = getClass().getResource("stitch_test_l2r_small_part3.nc").getFile();
        // todo: change this later!
//        final String ncFilename1 = "C:/Users/olafd/coastcolour/stitch/testdata/stitch_test_part1.nc";
//        final String ncFilename2 = "C:/Users/olafd/coastcolour/stitch/testdata/stitch_test_part2.nc";
//        final String ncFilename3 = "C:/Users/olafd/coastcolour/stitch/testdata/stitch_test_part3.nc";
//        final String ncFilename1 = "C:/Users/olafd/coastcolour/stitch/testdata/l1p/l1p_subset_test_part1.nc";
//        final String ncFilename2 = "C:/Users/olafd/coastcolour/stitch/testdata/l1p/l1p_subset_test_part2.nc";
//        final String ncFilename3 = "C:/Users/olafd/coastcolour/stitch/testdata/l1p/l1p_subset_test_part3.nc";
        final String ncFilename1 = srcRootDir + "l1p/mediterranean/MER_FRS_CCL1P_20120308_081908_000000703112_00251_52420_0001.nc";
        final String ncFilename2 = srcRootDir + "l1p/mediterranean/MER_FRS_CCL1P_20120308_081935_000001973112_00251_52420_0001.nc";
        final String ncFilename3 = srcRootDir + "l1p/mediterranean/MER_FRS_CCL1P_20120308_082210_000001443112_00251_52420_0001.nc";
//        final String ncFilename1 = "C:/Users/olafd/coastcolour/stitch/testdata/l2r/l2r_subset_test_part1.nc";
//        final String ncFilename2 = "C:/Users/olafd/coastcolour/stitch/testdata/l2r/l2r_subset_test_part2.nc";
//        final String ncFilename3 = "C:/Users/olafd/coastcolour/stitch/testdata/l2r/l2r_subset_test_part3.nc";
//        final String ncFilename1 = "C:/Users/olafd/coastcolour/stitch/testdata/l2w/l2w_subset_test_part1.nc";
//        final String ncFilename2 = "C:/Users/olafd/coastcolour/stitch/testdata/l2w/l2w_subset_test_part2.nc";
//        final String ncFilename3 = "C:/Users/olafd/coastcolour/stitch/testdata/l2w/l2w_subset_test_part3.nc";
        ncFile1 = NetcdfFile.open(ncFilename1);
        ncFile2 = NetcdfFile.open(ncFilename2);
        ncFile3 = NetcdfFile.open(ncFilename3);

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

//        bandVariableList1 = ProductStitcher.setBandVariablesList(variableList1);
//        bandVariableList2 = ProductStitcher.setBandVariablesList(variableList2);
//        bandVariableList3 = ProductStitcher.setBandVariablesList(variableList3);
//        allBandVariablesLists = new ArrayList<List<Variable>>();
//        allBandVariablesLists.add(bandVariableList1);
//        allBandVariablesLists.add(bandVariableList2);
//        allBandVariablesLists.add(bandVariableList3);
//
//        tpVariableList1 = ProductStitcher.getTpVariablesList(variableList1);
//        tpVariableList2 = ProductStitcher.getTpVariablesList(variableList2);
//        tpVariableList3 = ProductStitcher.getTpVariablesList(variableList3);
//        allTpVariablesLists = new ArrayList<List<Variable>>();
//        allTpVariablesLists.add(tpVariableList1);
//        allTpVariablesLists.add(tpVariableList2);
//        allTpVariablesLists.add(tpVariableList3);
    }

    @After
    public void tearDown() throws Exception {
        ncFile1.close();
        ncFile2.close();
        ncFile3.close();
    }

//    @Test
//    public void testWriteStitchedProduct() throws Exception {
//        List<NetcdfFile> ncFileList = new ArrayList<NetcdfFile>();
//        ncFileList.add(ncFile1);
//        ncFileList.add(ncFile2);
//        ncFileList.add(ncFile3);
//        final List<Map<Integer, Long>> bandRowToScanTimeMaps = ProductStitcher.setRowToScanTimeMaps(ncFileList, false);
//        final List<Map<Integer, Long>> tpRowToScanTimeMaps = ProductStitcher.setRowToScanTimeMaps(ncFileList, true);
//
//
//        // todo change this path!
////        final File resultFile = new File("C:/Users/olafd/coastcolour/stitch/testdata/stitch_test_l2r_small_result.nc");
//        final File resultFile = new File("C:/Users/olafd/coastcolour/stitch/testdata/l1p/mediterranean/stitch_test_result.nc");
//        ProductStitcher.writeStitchedProduct(resultFile,
//                                             allAttributesLists,
//                                             allDimensionsLists,
//                                             allBandVariablesLists,
//                                             allTpVariablesLists,
////                                             bandRowToProductIndexMap,
////                                             tpRowToProductIndexMap,
//                                             bandRowToScanTimeMaps,
//                                             tpRowToScanTimeMaps,
//                                             new DefaultErrorHandler());
//        // todo continue
//
//
//    }
}
