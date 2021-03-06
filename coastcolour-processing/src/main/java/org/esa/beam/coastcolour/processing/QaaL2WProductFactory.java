package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.meris.qaa.algorithm.QaaConstants;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ConstantDescriptor;
import javax.media.jai.operator.SubtractDescriptor;
import java.awt.RenderingHints;
import java.awt.image.RenderedImage;

/**
 * @author Marco Peters
 */
class QaaL2WProductFactory extends L2WProductFactory {

    private Product l2rProduct;
    private Product qaaProduct;

    private boolean iopBandsOnly;

    public QaaL2WProductFactory(Product l2rProduct, Product qaaProduct) {
        this.l2rProduct = l2rProduct;
        this.qaaProduct = qaaProduct;
    }

    @Override
    Product createL2WProduct() {
        return createL2WQaaProduct();
    }

    private Product createL2WQaaProduct() {
        String l2wProductType = l2rProduct.getProductType().substring(0, 8) + "CCL2W";
        final int sceneWidth = qaaProduct.getSceneRasterWidth();
        final int sceneHeight = qaaProduct.getSceneRasterHeight();
        final Product l2wProduct = new Product(qaaProduct.getName(), l2wProductType, sceneWidth, sceneHeight);
        l2wProduct.setStartTime(qaaProduct.getStartTime());
        l2wProduct.setEndTime(qaaProduct.getEndTime());
        l2wProduct.setDescription("MERIS CoastColour L2W");

        ProductUtils.copyMetadata(l2rProduct, l2wProduct);
        copyIOPBands(qaaProduct, l2wProduct);
        renameIops(l2wProduct);
        addQaaPrefix(l2wProduct);

        if (!isIopBandsOnly()) {
            addPatternToAutoGrouping(l2wProduct, CONC_GROUPING_PATTERN);
            addKMinBand(l2wProduct);
            addIOPQualityBand(l2wProduct);
            if (isOutputKdSpectrum()) {
                addKdSpectrum(l2wProduct);
                addPatternToAutoGrouping(l2wProduct, "Kd");
            } else {
                addKdBand(l2wProduct, -1, KD_LAMBDAS[2]); // Kd_490
            }

            addZ90Band(l2wProduct);
            addTurbidityBand(l2wProduct);

            copyMasks(l2rProduct, l2wProduct);
            ProductUtils.copyFlagBands(l2rProduct, l2wProduct, true);
            ProductUtils.copyTiePointGrids(qaaProduct, l2wProduct);
            copyReflecBandsIfRequired(l2rProduct, l2wProduct);
            sortFlagBands(l2wProduct);
            addL2WMasksAndFlags(l2wProduct);
            ProductUtils.copyGeoCoding(qaaProduct, l2wProduct);
            copyAltitudeBand(l2rProduct, l2wProduct);
        }

        return l2wProduct;
    }

    public boolean isIopBandsOnly() {
        return iopBandsOnly;
    }

    public void setIopBandsOnly(boolean iopBandsOnly) {
        this.iopBandsOnly = iopBandsOnly;
    }

    protected void addQaaPrefix(Product targetProduct) {
        final Band aTotal443Band = targetProduct.getBand(IOP_A_TOTAL_443_NAME);
        if (aTotal443Band != null) {
            aTotal443Band.setName(QAA_PREFIX_TARGET_BAND_NAME + IOP_A_TOTAL_443_NAME);
        }
        final Band aYs443Band = targetProduct.getBand(IOP_A_YS_443_NAME);
        if (aYs443Band != null) {
            aYs443Band.setName(QAA_PREFIX_TARGET_BAND_NAME + IOP_A_YS_443_NAME);
        }
        final Band aPig443Band = targetProduct.getBand(IOP_A_PIG_443_NAME);
        if (aPig443Band != null) {
            aPig443Band.setName(QAA_PREFIX_TARGET_BAND_NAME + IOP_A_PIG_443_NAME);
        }
        final Band aDet443Band = targetProduct.getBand(IOP_A_DET_443_NAME);
        if (aDet443Band != null) {
            aDet443Band.setName(QAA_PREFIX_TARGET_BAND_NAME + IOP_A_DET_443_NAME);
        }
        final Band bTsm443Band = targetProduct.getBand(IOP_B_TSM_443_NAME);
        if (bTsm443Band != null) {
            bTsm443Band.setName(QAA_PREFIX_TARGET_BAND_NAME + IOP_B_TSM_443_NAME);
        }
        final Band bWhit443Band = targetProduct.getBand(IOP_B_WHIT_443_NAME);
        if (bWhit443Band != null) {
            bWhit443Band.setName(QAA_PREFIX_TARGET_BAND_NAME + IOP_B_WHIT_443_NAME);
        }
        final Band aPocBand = targetProduct.getBand(IOP_A_POC_443_NAME);
        if (aPocBand != null) {
            aPocBand.setName(QAA_PREFIX_TARGET_BAND_NAME + IOP_A_POC_443_NAME);
        }
        final Band bbSpm443Band = targetProduct.getBand(IOP_BB_SPM_443_NAME);
        if (bbSpm443Band != null) {
            bbSpm443Band.setName(QAA_PREFIX_TARGET_BAND_NAME + IOP_BB_SPM_443_NAME);
        }
        final Band iopQualityBand = targetProduct.getBand(IOP_QUALITY_BAND_NAME);
        if (iopQualityBand != null) {
            iopQualityBand.setName(QAA_PREFIX_TARGET_BAND_NAME + IOP_QUALITY_BAND_NAME);
        }

        String groupPattern = QAA_PREFIX_TARGET_BAND_NAME + IOP_PREFIX_TARGET_BAND_NAME.substring(0, IOP_PREFIX_TARGET_BAND_NAME.length() - 1);
        addPatternToAutoGrouping(targetProduct, groupPattern);
    }

    private void addTurbidityBand(Product l2wProduct) {
        addBand(l2wProduct, TURBIDITY_NAME, "Turbidity index in FNU (Formazine Nephelometric Unit)",
                "FNU", L2W_VALID_EXPRESSION);

    }

    private void addZ90Band(Product l2wProduct) {
        addBand(l2wProduct, Z90_MAX_NAME, "Maximum signal depth.", "m", L2W_VALID_EXPRESSION);
    }

    private void addKdSpectrum(Product l2wProduct) {
        for (int i = 0; i < KD_LAMBDAS.length; i++) {
            int wavelength = KD_LAMBDAS[i];
            addKdBand(l2wProduct, i, wavelength);
        }
    }

    private void addKdBand(Product l2wProduct, int i, int wavelength) {
        final String descriptionFormat = "Downwelling irradiance attenuation coefficient at wavelength %s.";
        final Band kdBand = addBand(l2wProduct, "Kd_" + wavelength, String.format(descriptionFormat, wavelength),
                                    "m^-1", L2W_VALID_EXPRESSION);
        kdBand.setSpectralBandIndex(i);
        kdBand.setSpectralWavelength(wavelength);
    }

    private Band addKMinBand(Product l2wProduct) {
        return addBand(l2wProduct, K_MIN_NAME, "Minimum downwelling irradiance attenuation coefficient.",
                       "m^-1", L2W_VALID_EXPRESSION);
    }

    private Band addBand(Product l2wProduct, String name, String description, String unit, String validExpression) {
        final Band band = l2wProduct.addBand(name, ProductData.TYPE_FLOAT32);
        band.setDescription(description);
        band.setUnit(unit);
        band.setValidPixelExpression(validExpression);
        return band;
    }

    protected void copyIOPBands(Product source, Product target) {
        for (String iopSourceBandName : IOP_SOURCE_BAND_NAMES) {
            final Band targetBand = ProductUtils.copyBand(iopSourceBandName, source, target, false);
            final Band sourceBand = source.getBand(iopSourceBandName);
            RenderedImage sourceImage = getIOPSourceImage(sourceBand);
            targetBand.setSourceImage(sourceImage);
            targetBand.setValidPixelExpression(L2W_VALID_EXPRESSION);
        }
    }

    private RenderedImage getIOPSourceImage(Band sourceBand) {
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
        final RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT,
                                                        new ImageLayout(iopQualityBand.getSourceImage()));
        final RenderedOp nanImage = ConstantDescriptor.create((float) l2wProduct.getSceneRasterWidth(),
                                                              (float) l2wProduct.getSceneRasterHeight(),
                                                              new Float[]{Float.NaN}, hints);
        iopQualityBand.setValidPixelExpression("!NaN");
        iopQualityBand.setSourceImage(nanImage);
    }

    // currently not used
//    private void addChlAndTsmBands(Product l2wProduct) {
//        final Band tsm = l2wProduct.addBand(OWT_CONC_TSM_NAME, ProductData.TYPE_FLOAT32);
//        tsm.setDescription("Total suspended matter dry weight concentration.");
//        tsm.setUnit("g m^-3");
//        tsm.setValidPixelExpression(L2W_VALID_EXPRESSION);
//        final VirtualBandOpImage tsmImage = VirtualBandOpImage.create("1.73 * pow((bb_spm_443 / 0.01), 1.0)",
//                                                                      ProductData.TYPE_FLOAT32, Double.NaN,
//                                                                      qaaProduct, ResolutionLevel.MAXRES);
//
//        tsm.setSourceImage(tsmImage);
//
//        final Band conc_chl = l2wProduct.addBand(OWT_CONC_CHL_NAME, ProductData.TYPE_FLOAT32);
//        conc_chl.setDescription("Chlorophyll concentration.");
//        conc_chl.setUnit("mg m^-3");
//        conc_chl.setValidPixelExpression(L2W_VALID_EXPRESSION);
//        final VirtualBandOpImage chlConcImage = VirtualBandOpImage.create("21.0 * pow(a_pig_443, 1.04)",
//                                                                          ProductData.TYPE_FLOAT32, Double.NaN,
//                                                                          qaaProduct, ResolutionLevel.MAXRES);
//        conc_chl.setSourceImage(chlConcImage);
//    }

    protected void copyAltitudeBand(Product sourceProduct, Product targetProduct) {
        Band band = sourceProduct.getBand(ALTITUDE_SOURCE_NAME);
        if (band != null) { // altitude does not exist for RR and FR only for FSG
            ProductUtils.copyBand(band.getName(), sourceProduct, targetProduct, true);
        }

    }


}
