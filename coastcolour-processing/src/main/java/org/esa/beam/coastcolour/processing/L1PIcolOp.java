package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.meris.MerisOp;

import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "CoastColour.L1P.Icol", version = "1.7",
                  authors = "Olaf Danne",
                  copyright = "(c) 2012 Brockmann Consult",
                  description = "Wrapper operator to apply ICOL correction to L1P product.")
public class L1PIcolOp extends Operator {

    // CoastColour L1P parameters
    @Parameter(defaultValue = "false",
               label = " Perform ICOL correction",
               description = "Whether to perform ICOL correction (NOTE: This step can be very time- and memory-consuming in case of large products!).")
    private boolean doIcol;

    @Parameter(defaultValue = "false",
               label = " Perform re-calibration",
               description = "Applies correction from MERIS 2nd to 3rd reprocessing quality.")
    private boolean doCalibration;

    @Parameter(defaultValue = "true",
               label = " Perform Smile-effect correction",
               description = "Whether to perform MERIS Smile-effect correction.")
    private boolean doSmile;

    @Parameter(defaultValue = "true",
               label = " Perform equalization",
               description = "Perform removal of detector-to-detector systematic radiometric differences in MERIS L1b data products.")
    private boolean doEqualization;

    // IdePix parameters
    @Parameter(defaultValue = "2", interval = "[0,100]",
               description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
               label = " Width of cloud buffer (# of pixels)")
    private int ccCloudBufferWidth;

    @Parameter(defaultValue = "1.4",
               description = "Threshold of Cloud Probability Feature Value above which cloud is regarded as still " +
                       "ambiguous (i.e. a higher value results in fewer ambiguous clouds).",
               label = " Cloud screening 'ambiguous' threshold")
    private double ccCloudScreeningAmbiguous = 1.4;      // Schiller

    @Parameter(defaultValue = "1.8",
               description = "Threshold of Cloud Probability Feature Value above which cloud is regarded as " +
                       "sure (i.e. a higher value results in fewer sure clouds).",
               label = " Cloud screening 'sure' threshold")
    private double ccCloudScreeningSure = 1.8;       // Schiller

    @Parameter(defaultValue = "false",
               description = "Write Cloud Probability Feature Value to the  CC L1P target product.",
               label = " Write Cloud Probability Feature Value to the target product")
    private boolean ccOutputCloudProbabilityFeatureValue = false;


    @SourceProduct(alias = "merisL1B",
                   label = "MERIS L1B product",
                   description = "The MERIS L1B input product")
    private Product sourceProduct;


    @Override
    public void initialize() throws OperatorException {
        Product icolizedL1bProduct = createIcolizedL1bProduct();

        HashMap<String, Object> l1pParams = createL1pParameterMap();
        Product l1pProduct = GPF.createProduct("CoastColour.L1P", l1pParams, icolizedL1bProduct);

        setTargetProduct(l1pProduct);
    }

    private Product createIcolizedL1bProduct() {
        HashMap<String, Object> icolParams = new HashMap<String, Object>();
        icolParams.put("icolAerosolCase2", true);
        icolParams.put("productType", 0);
        icolParams.put("aeArea", AeArea.COASTAL_OCEAN);
        icolParams.put("useAdvancedLandWaterMask", true);
        Map<String, Product> sourceProducts = new HashMap<String, Product>(1);
        sourceProducts.put("sourceProduct", sourceProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(MerisOp.class), icolParams, sourceProducts);
    }

    private HashMap<String, Object> createL1pParameterMap() {
        HashMap<String, Object> l1pParams = new HashMap<String, Object>();
        l1pParams.put("doCalibration", doCalibration);
        l1pParams.put("doSmile", doSmile);
        l1pParams.put("doEqualization", doEqualization);
        l1pParams.put("useIdepix", true);
        l1pParams.put("ccCloudBufferWidth", ccCloudBufferWidth);
        l1pParams.put("ccCloudScreeningAmbiguous", ccCloudScreeningAmbiguous);
        l1pParams.put("ccCloudScreeningSure", ccCloudScreeningSure);
        l1pParams.put("ccOutputCloudProbabilityFeatureValue", ccOutputCloudProbabilityFeatureValue);
        return l1pParams;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L1PIcolOp.class);
        }
    }
}
