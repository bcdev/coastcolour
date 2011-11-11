package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.VirtualBandOpImage;
import org.esa.beam.meris.qaa.QaaConstants;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ConstantDescriptor;
import javax.media.jai.operator.SubtractDescriptor;
import java.awt.image.RenderedImage;

/**
 * @author Marco Peters
 */
class QaaL2WProductFactory extends L2WProductFactory {

    private Product l2rProduct;
    private Product case2rProduct;
    private Product qaaProduct;

    public QaaL2WProductFactory(Product l2rProduct, Product case2rProduct, Product qaaProduct) {
        this.l2rProduct = l2rProduct;
        this.case2rProduct = case2rProduct;
        this.qaaProduct = qaaProduct;
    }


    @Override
    Product createL2WProduct() {
        String l2wProductType = l2rProduct.getProductType().substring(0, 8) + "CCL2W";
        final int sceneWidth = case2rProduct.getSceneRasterWidth();
        final int sceneHeight = case2rProduct.getSceneRasterHeight();
        final Product l2wProduct = new Product(case2rProduct.getName(), l2wProductType, sceneWidth, sceneHeight);
        l2wProduct.setStartTime(case2rProduct.getStartTime());
        l2wProduct.setEndTime(case2rProduct.getEndTime());
        l2wProduct.setDescription("MERIS CoastColour L2W");
        ProductUtils.copyMetadata(case2rProduct, l2wProduct);
        copyMasks(case2rProduct, l2wProduct);
        copyMasks(l2rProduct, l2wProduct);

        copyIOPBands(qaaProduct, l2wProduct);
        addIOPQualityBand(l2wProduct);
        addChlAndTsmBands(l2wProduct);

        copyBands(case2rProduct, l2wProduct);

        if (isOutputKdSpectrum()) {
            addPatternToAutoGrouping(l2wProduct, "Kd");
        }
        if (isOutputFLH()) {
            addFLHBands(l2wProduct);
        }
        copyFlagBands(l2rProduct, l2wProduct);
//        copyFlagBands(case2rProduct, l2wProduct);

        ProductUtils.copyTiePointGrids(case2rProduct, l2wProduct);
        renameIops(l2wProduct);
        renameConcentrations(l2wProduct);
        renameTurbidityBand(l2wProduct);
        copyReflecBandsIfRequired(l2rProduct, l2wProduct);
        sortFlagBands(l2wProduct);
        addL2WMasksAndFlags(l2wProduct);
        ProductUtils.copyGeoCoding(case2rProduct, l2wProduct);

        return l2wProduct;
    }


    @Override
    protected boolean considerBandInGeneralBandCopy(Band band, Product target) {
        if (super.considerBandInGeneralBandCopy(band, target)) {
            return !"chiSquare".equals(band.getName());
        } else {
            return false;
        }
    }

    protected void copyIOPBands(Product source, Product target) {
        for (String iopSourceBandName : IOP_SOURCE_BAND_NAMES) {
            final Band targetBand = ProductUtils.copyBand(iopSourceBandName, source, target);
            final Band sourceBand = source.getBand(iopSourceBandName);
            RenderedImage sourceImage = getSourceImage(sourceBand);
            targetBand.setSourceImage(sourceImage);
            targetBand.setValidPixelExpression("!l2w_flags.INVALID");
        }
    }

    private RenderedImage getSourceImage(Band sourceBand) {
        RenderedImage sourceImage = sourceBand.getSourceImage();
        if (IOP_SOURCE_BAND_NAMES[0].equals(sourceBand.getName())) {
            final RenderedOp awCoeffImage = ConstantDescriptor.create((float) sourceBand.getSceneRasterWidth(),
                                                                      (float) sourceBand.getSceneRasterHeight(),
                                                                      new Float[]{(float) QaaConstants.AW_COEFS[1]},
                                                                      null);
            sourceImage = SubtractDescriptor.create(sourceImage, awCoeffImage, null);
        }
        return sourceImage;
    }

    private void addIOPQualityBand(Product l2wProduct) {
        final Band iopQualityBand = l2wProduct.addBand(IOP_QUALITY_BAND_NAME, ProductData.TYPE_FLOAT32);
        iopQualityBand.setUnit("1");
        iopQualityBand.setDescription(IOP_QUALITY_DESCRIPTION);
        final RenderedOp nanImage = ConstantDescriptor.create((float) l2wProduct.getSceneRasterWidth(),
                                                              (float) l2wProduct.getSceneRasterHeight(),
                                                              new Float[]{Float.NaN}, null);
        iopQualityBand.setValidPixelExpression("!NaN");
        iopQualityBand.setSourceImage(nanImage);
    }

    private void addChlAndTsmBands(Product l2wProduct) {
        final Band tsm = l2wProduct.addBand("tsm", ProductData.TYPE_FLOAT32);
        tsm.setDescription("Total suspended matter dry weight concentration.");
        tsm.setUnit("g m^-3");
        tsm.setValidPixelExpression("!l2w_flags.INVALID");
        final VirtualBandOpImage tsmImage = VirtualBandOpImage.create("1.73 * pow((bb_spm_443 / 0.01), 1.0)",
                                                                      ProductData.TYPE_FLOAT32, Double.NaN,
                                                                      qaaProduct, ResolutionLevel.MAXRES);

        tsm.setSourceImage(tsmImage);

        final Band conc_chl = l2wProduct.addBand("chl_conc", ProductData.TYPE_FLOAT32);
        conc_chl.setDescription("Chlorophyll concentration.");
        conc_chl.setUnit("mg m^-3");
        conc_chl.setValidPixelExpression("!l2w_flags.INVALID");
        final VirtualBandOpImage chlConcImage = VirtualBandOpImage.create("21.0 * pow(a_pig_443, 1.04)",
                                                                          ProductData.TYPE_FLOAT32, Double.NaN,
                                                                          qaaProduct, ResolutionLevel.MAXRES);
        conc_chl.setSourceImage(chlConcImage);

    }
}
