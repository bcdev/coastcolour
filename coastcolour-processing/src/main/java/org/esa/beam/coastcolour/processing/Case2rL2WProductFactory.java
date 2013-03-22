package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.util.ProductUtils;

/**
 * @author Marco Peters
 * @since 1.4
 */
class Case2rL2WProductFactory extends L2WProductFactory {

    private static final String TURBIDITY_INDEX_SOURCE_NAME = "turbidity_index";


    private Product l2rProduct;
    private Product case2rProduct;

    public Case2rL2WProductFactory(Product l2rProduct, Product case2rProduct) {
        this.l2rProduct = l2rProduct;
        this.case2rProduct = case2rProduct;
    }

    @Override
    public Product createL2WProduct() {
        String l2wProductType = l2rProduct.getProductType().substring(0, 8) + "CCL2W";
        final int sceneWidth = case2rProduct.getSceneRasterWidth();
        final int sceneHeight = case2rProduct.getSceneRasterHeight();
        final Product l2wProduct = new Product(case2rProduct.getName(), l2wProductType, sceneWidth, sceneHeight);
        l2wProduct.setStartTime(case2rProduct.getStartTime());
        l2wProduct.setEndTime(case2rProduct.getEndTime());
        l2wProduct.setDescription("MERIS CoastColour L2W");
        ProductUtils.copyMetadata(case2rProduct, l2wProduct);
        ProductUtils.copyTiePointGrids(case2rProduct, l2wProduct);
        ProductUtils.copyGeoCoding(case2rProduct, l2wProduct);
        copyMasks(l2rProduct, l2wProduct);
        copyIOPBands(case2rProduct, l2wProduct);
        copyBands(case2rProduct, l2wProduct);
//        if (L2WOp.ENABLE_OWT_CONC_BANDS) {
//            addChlAndTsmBands(l2wProduct);
//        }
        addPatternToAutoGrouping(l2wProduct, CONC_GROUPING_PATTERN);

        if (isOutputKdSpectrum()) {
            addPatternToAutoGrouping(l2wProduct, "Kd");
        }
        if (isOutputFLH()) {
            addFLHBands(l2wProduct);
        }
        ProductUtils.copyFlagBands(l2rProduct, l2wProduct, true);

        renameIops(l2wProduct);

        Band adg = l2wProduct.addBand("iop_a_dg_443", "iop_a_det_443 +  iop_a_ys_443");
        adg.setDescription("Yellow substance absorption + Pigment absorption at 443 nm.");
        adg.setUnit("m^-1");
        adg.setSpectralBandwidth(433);

        l2wProduct.getBand(K_MIN_NAME).setName(KD_MIN_NAME);
        renameChiSquare(l2wProduct);
        renameTurbidityBand(l2wProduct);
        copyReflecBandsIfRequired(l2rProduct, l2wProduct);
        sortFlagBands(l2wProduct);
        addL2WMasksAndFlags(l2wProduct);

        return l2wProduct;

    }

    private void copyBands(Product source, Product target) {
        final Band[] sourceBands = source.getBands();
        for (Band band : sourceBands) {
            if (considerBandInBandCopy(band, target)) {
                Band targetBand = new Band(band.getName(),
                                           band.getGeophysicalDataType(),
                                           band.getRasterWidth(),
                                           band.getRasterHeight());
                ProductUtils.copyRasterDataNodeProperties(band, targetBand);
                targetBand.setLog10Scaled(false);
                targetBand.setSourceImage(band.getGeophysicalImage());
                targetBand.setValidPixelExpression(L2W_VALID_EXPRESSION);
                target.addBand(targetBand);
            }
        }
    }

    private boolean considerBandInBandCopy(Band band, Product target) {
        return !band.isFlagBand() &&
                !target.containsBand(band.getName()) &&
                !band.getName().equals(TSM_SOURCE_NAME) &&
                !band.getName().equals(CHL_CONC_SOURCE_NAME);
    }

    protected void copyIOPBands(Product source, Product target) {
        for (String iopSourceBandName : IOP_SOURCE_BAND_NAMES) {
            final Band targetBand = ProductUtils.copyBand(iopSourceBandName, source, target, false);
            targetBand.setLog10Scaled(false);
            targetBand.setSourceImage(source.getBand(iopSourceBandName).getGeophysicalImage());
            targetBand.setValidPixelExpression(L2W_VALID_EXPRESSION);
        }
    }

    // currently not used
    private void addChlAndTsmBands(Product l2wProduct) {
        final Band tsm = l2wProduct.addBand(OWT_CONC_TSM_NAME, ProductData.TYPE_FLOAT32);
        tsm.setDescription("Total suspended matter dry weight concentration.");
        tsm.setUnit("g m^-3");
        tsm.setValidPixelExpression(L2W_VALID_EXPRESSION);

        final Band conc_chl = l2wProduct.addBand(OWT_CONC_CHL_NAME, ProductData.TYPE_FLOAT32);
        conc_chl.setDescription("Chlorophyll concentration.");
        conc_chl.setUnit("mg m^-3");
        conc_chl.setValidPixelExpression(L2W_VALID_EXPRESSION);
    }

    private void renameChiSquare(Product l2wProduct) {
        final Band iopQualityBand = l2wProduct.getBand("chiSquare");
        iopQualityBand.setName(IOP_QUALITY_BAND_NAME);
        iopQualityBand.setUnit("1");
        iopQualityBand.setDescription(IOP_QUALITY_DESCRIPTION);

    }

    private void renameTurbidityBand(Product targetProduct) {
        targetProduct.getBand(TURBIDITY_INDEX_SOURCE_NAME).setName(TURBIDITY_NAME);
    }

}
