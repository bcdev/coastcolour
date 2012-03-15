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
    private List<NetcdfFile> ncFileList;

    private ProductStitcher testStitcher;

    @Before
    public void setUp() throws Exception {
        final String srcRootDir = "C:/Users/olafd/bc/coastcolour/stitch/testdata/";
//        final String ncFilename1 = srcRootDir + "l1p/l1p_subset_test_part1.nc";
//        final String ncFilename2 = srcRootDir +  "l1p/l1p_subset_test_part2.nc";
//        final String ncFilename3 = srcRootDir +  "l1p/l1p_subset_test_part3.nc";
        final String ncFilename1 = srcRootDir + "l2r/stitch_test_l2r_small_part1.nc";
        final String ncFilename2 = srcRootDir + "l2r/stitch_test_l2r_small_part2.nc";
        final String ncFilename3 = srcRootDir + "l2r/stitch_test_l2r_small_part3.nc";
//        final String ncFilename1 = srcRootDir + "l1p/mediterranean/MER_FRS_CCL1P_20120308_081908_000000703112_00251_52420_0001.nc";
//        final String ncFilename2 = srcRootDir + "l1p/mediterranean/MER_FRS_CCL1P_20120308_081935_000001973112_00251_52420_0001.nc";
//        final String ncFilename3 = srcRootDir + "l1p/mediterranean/MER_FRS_CCL1P_20120308_082210_000001443112_00251_52420_0001.nc";

        ncFile1 = NetcdfFile.open(ncFilename1);
        ncFile2 = NetcdfFile.open(ncFilename2);
        ncFile3 = NetcdfFile.open(ncFilename3);

        ncFileList = new ArrayList<NetcdfFile>();
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
    public void testWriteStitchedProduct() throws Exception {
//        final File resultFile = new File("C:/Users/olafd/coastcolour/stitch/testdata/stitch_test_l2r_small_result.nc");
        final File resultFile = new File("C:/Users/olafd/bc/coastcolour/stitch/testdata/l1p/stitch_test_result.nc");
        testStitcher.writeStitchedProduct(resultFile, new DefaultErrorHandler());
        // todo continue


    }
}
