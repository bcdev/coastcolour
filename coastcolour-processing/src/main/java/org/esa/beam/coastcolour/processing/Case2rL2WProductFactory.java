package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.ProductUtils;

/**
 * @author Marco Peters
 * @since 1.4
 */
class Case2rL2WProductFactory extends L2WProductFactory {

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
        copyMasks(l2rProduct, l2wProduct);
        copyIOPBands(case2rProduct, l2wProduct);

        copyBands(case2rProduct, l2wProduct);

        if (isOutputKdSpectrum()) {
            addPatternToAutoGrouping(l2wProduct, "Kd");
        }
        if (isOutputFLH()) {
            addFLHBands(l2wProduct);
        }
        copyFlagBands(l2rProduct, l2wProduct);
        ProductUtils.copyTiePointGrids(case2rProduct, l2wProduct);
        renameIops(l2wProduct);
        renameChiSquare(l2wProduct);
        renameConcentrations(l2wProduct);
        renameTurbidityBand(l2wProduct);
        copyReflecBandsIfRequired(l2rProduct, l2wProduct);
        sortFlagBands(l2wProduct);
        addL2WMasksAndFlags(l2wProduct);
        ProductUtils.copyGeoCoding(case2rProduct, l2wProduct);

        return l2wProduct;

    }

    private void copyBands(Product source, Product target) {
        final Band[] case2rBands = source.getBands();
        for (Band band : case2rBands) {
            if (considerBandInBandCopy(band, target)) {
                final Band targetBand = ProductUtils.copyBand(band.getName(), source, target);
                targetBand.setSourceImage(band.getSourceImage());
                targetBand.setValidPixelExpression(L2W_VALID_EXPRESSION);
            }
        }
    }

    private boolean considerBandInBandCopy(Band band, Product target) {
        return !band.isFlagBand() && !target.containsBand(band.getName());
    }

    protected void copyIOPBands(Product source, Product target) {
        for (String iopSourceBandName : IOP_SOURCE_BAND_NAMES) {
            final Band targetBand = ProductUtils.copyBand(iopSourceBandName, source, target);
            targetBand.setSourceImage(source.getBand(iopSourceBandName).getSourceImage());
            targetBand.setValidPixelExpression("!l2w_flags.INVALID");
        }
    }

    private void renameChiSquare(Product l2wProduct) {
        final Band iopQualityBand = l2wProduct.getBand("chiSquare");
        iopQualityBand.setName(IOP_QUALITY_BAND_NAME);
        iopQualityBand.setUnit("1");
        iopQualityBand.setDescription(IOP_QUALITY_DESCRIPTION);

    }


}
