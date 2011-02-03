package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

public class L1POp extends Operator {

    @SourceProduct(alias = "l1b")
    private Product source;

    @Override
    public void initialize() throws OperatorException {

        final Map<String, Object> rcParams = new HashMap<String, Object>();
        rcParams.put("doCalibration", true);
        rcParams.put("doSmile", true);
        rcParams.put("doEqualization", true);
        rcParams.put("doRadToRefl", false);
        final Product rcProduct = GPF.createProduct("Meris.CorrectRadiometry", rcParams, source);

        final Product idepixProduct = GPF.createProduct("idepix.ComputeChain", GPF.NO_PARAMS, rcProduct);

        final Band cloud_classif_flags = ProductUtils.copyBand("cloud_classif_flags", idepixProduct, rcProduct);
        cloud_classif_flags.setSourceImage(idepixProduct.getBand("cloud_classif_flags").getSourceImage());
        final FlagCoding flagCoding = cloud_classif_flags.getFlagCoding();
        ProductUtils.copyFlagCoding(flagCoding, rcProduct);
        cloud_classif_flags.setSampleCoding(rcProduct.getFlagCodingGroup().get("cloud_classif_flags"));

        final String productType = rcProduct.getProductType();
        rcProduct.setProductType(productType.replaceFirst("_1P", "L1P"));
        setTargetProduct(rcProduct);
    }
}
