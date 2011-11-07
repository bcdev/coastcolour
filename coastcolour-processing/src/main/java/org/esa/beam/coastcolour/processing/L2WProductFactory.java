package org.esa.beam.coastcolour.processing;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.util.ProductUtils;

/**
 * @author Marco Peters
 * @since 1.4
 */
abstract class L2WProductFactory {

    private static final String L2W_FLAGS_NAME = "l2w_flags";
    private static final String CASE2_FLAGS_NAME = "case2_flags";

    private static final String[] IOP_SOURCE_BAND_NAMES = new String[]{
            "a_total_443",
            "a_ys_443",
            "a_pig_443",
            "bb_spm_443"
    };
    static final String EXP_FLH_681_NAME = "exp_FLH_681";
    static final String EXP_FLH_681_NORM_NAME = "exp_FLH_681_norm";
    static final String EXP_FLH_681_ALT_NAME = "exp_FLH_681_alt";
    static final String EXP_FLH_NORM_OLD_681_NAME = "exp_FLH_norm_old_681";
    static final String EXP_FLH_ALT_OLD_681_NAME = "exp_FLH_alt_old_681";


    private boolean outputKdSpectrum;
    private boolean outputFLH;
    private boolean outputReflectance;
    private String invalidPixelExpression;
    private FLHAlgorithm flhAlgorithm;


    abstract Product createL2WProduct();

    public void setOutputKdSpectrum(boolean outputKdSpectrum) {
        this.outputKdSpectrum = outputKdSpectrum;
    }

    public boolean isOutputKdSpectrum() {
        return outputKdSpectrum;
    }

    public void setOutputFLH(boolean outputFLH) {
        this.outputFLH = outputFLH;
    }

    public boolean isOutputFLH() {
        return outputFLH;
    }

    public void setOutputReflectance(boolean outputReflectance) {
        this.outputReflectance = outputReflectance;
    }

    public boolean isOutputReflectance() {
        return outputReflectance;
    }

    public String getInvalidPixelExpression() {
        return invalidPixelExpression;
    }

    public void setInvalidPixelExpression(String invalidPixelExpression) {
        this.invalidPixelExpression = invalidPixelExpression;
    }

    public FLHAlgorithm getFlhAlgorithm() {
        return flhAlgorithm;
    }

    public void setFlhAlgorithm(FLHAlgorithm flhAlgorithm) {
        this.flhAlgorithm = flhAlgorithm;
    }

    protected void copyFlagBands(Product source, Product target) {
        ProductUtils.copyFlagBands(source, target);
        final Band[] radiometryBands = source.getBands();
        for (Band band : radiometryBands) {
            if (band.isFlagBand()) {
                final Band targetBand = target.getBand(band.getName());
                targetBand.setSourceImage(band.getSourceImage());
            }
        }
    }

    protected void copyBands(Product source, Product target) {
        final Band[] case2rBands = source.getBands();
        for (Band band : case2rBands) {
            if (!band.isFlagBand() && !target.containsBand(band.getName())) {
                final Band targetBand = ProductUtils.copyBand(band.getName(), source, target);
                targetBand.setSourceImage(band.getSourceImage());
            }
        }
    }

    protected void copyIOPBands(Product source, Product target) {
        for (String iopSourceBandName : IOP_SOURCE_BAND_NAMES) {
            final Band targetBand = ProductUtils.copyBand(iopSourceBandName, source, target);
            targetBand.setSourceImage(source.getBand(iopSourceBandName).getSourceImage());
            targetBand.setValidPixelExpression("!l2w_flags.INVALID");
        }
    }

    protected void copyMasks(Product sourceProduct, Product targetProduct) {
        ProductNodeGroup<Mask> maskGroup = sourceProduct.getMaskGroup();
        for (int i = 0; i < maskGroup.getNodeCount(); i++) {
            Mask mask = maskGroup.get(i);
            if (!mask.getImageType().getName().equals(Mask.VectorDataType.TYPE_NAME)) {
                mask.getImageType().transferMask(mask, targetProduct);
            }
        }
    }

    protected void copyReflecBandsIfRequired(Product sourceProduct, Product targetProduct) {
        if (isOutputReflectance()) {
            Band[] bands = sourceProduct.getBands();
            for (Band band : bands) {
                if (band.getName().startsWith("reflec_")) {
                    Band targetBand = ProductUtils.copyBand(band.getName(), sourceProduct, targetProduct);
                    Band sourceBand = sourceProduct.getBand(band.getName());
                    targetBand.setSourceImage(sourceBand.getSourceImage());
                }
            }
            addPatternToAutoGrouping(targetProduct, "reflec");
        }

    }

    protected void sortFlagBands(Product targetProduct) {
        Band l1_flags = targetProduct.getBand("l1_flags");
        Band l1p_flags = targetProduct.getBand("l1p_flags");
        Band l2r_flags = targetProduct.getBand("l2r_flags");
        Band case2_flags = targetProduct.getBand("case2_flags");
        targetProduct.removeBand(l1_flags);
        targetProduct.removeBand(l1p_flags);
        targetProduct.removeBand(l2r_flags);
        targetProduct.removeBand(case2_flags);
        targetProduct.addBand(l1_flags);
        targetProduct.addBand(l1p_flags);
        targetProduct.addBand(l2r_flags);
        targetProduct.addBand(case2_flags);
    }

    protected void changeL2WMasksAndFlags(Product targetProduct) {
        ProductNodeGroup<FlagCoding> flagCodingGroup = targetProduct.getFlagCodingGroup();
        FlagCoding l2wFlags = flagCodingGroup.get(CASE2_FLAGS_NAME);
        l2wFlags.setName(L2W_FLAGS_NAME);
        l2wFlags.removeAttribute(l2wFlags.getFlag("FIT_FAILED"));
        Band band = targetProduct.getBand(CASE2_FLAGS_NAME);
        band.setName(L2W_FLAGS_NAME);
        band.setDescription("CC L2W water constituents and IOPs retrieval quality flags.");
        ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();

        Mask fit_failed = maskGroup.get("case2_fit_failed");
        maskGroup.remove(fit_failed);

        int insertIndex = 0;
        String wlrOorDescription = "Water leaving reflectance out of training range";
        Mask wlr_oor = updateMask(maskGroup, "case2_wlr_oor", "l2w_cc_wlr_ootr", wlrOorDescription);
        reorderMask(maskGroup, wlr_oor, insertIndex);
        l2wFlags.getFlag("WLR_OOR").setDescription(wlrOorDescription);

        String concOorDescription = "Water constituents out of training range";
        Mask conc_oor = updateMask(maskGroup, "case2_conc_oor", "l2w_cc_conc_ootr", concOorDescription);
        reorderMask(maskGroup, conc_oor, ++insertIndex);
        l2wFlags.getFlag("CONC_OOR").setDescription(concOorDescription);

        String ootrDescription = "Spectrum out of training range (chiSquare threshold)";
        Mask ootr = updateMask(maskGroup, "case2_ootr", "l2w_cc_ootr", ootrDescription);
        reorderMask(maskGroup, ootr, ++insertIndex);
        l2wFlags.getFlag("OOTR").setDescription(ootrDescription);

        String whitecapsDescription = "Risk for white caps";
        Mask whitecaps = updateMask(maskGroup, "case2_whitecaps", "l2w_cc_whitecaps", whitecapsDescription);
        reorderMask(maskGroup, whitecaps, ++insertIndex);
        l2wFlags.getFlag("WHITECAPS").setDescription(whitecapsDescription);

        String invalidDescription = "Invalid pixels (" + getInvalidPixelExpression() + " || l2w_flags.OOTR)";
        Mask invalid = updateMask(maskGroup, "case2_invalid", "l2w_cc_invalid", invalidDescription);
        reorderMask(maskGroup, invalid, ++insertIndex);
        Mask.BandMathsType.setExpression(invalid, getInvalidPixelExpression() + " || l2w_flags.OOTR");
        l2wFlags.getFlag("INVALID").setDescription(invalidDescription);
    }

    private void reorderMask(ProductNodeGroup<Mask> maskGroup, Mask wlr_oor, int newIndex) {
        maskGroup.remove(wlr_oor);
        maskGroup.add(newIndex, wlr_oor);
    }

    private Mask updateMask(ProductNodeGroup<Mask> maskGroup, String oldMaskName, String newMaskName,
                            String description) {
        Mask mask = maskGroup.get(oldMaskName);
        mask.setName(newMaskName);
        mask.setDescription(description);
        return mask;
    }

    protected void renameIops(Product targetProduct) {
        String aTotal = "a_total_443";
        String aGelbstoff = "a_ys_443";
        String aPigment = "a_pig_443";
        String aPoc = "a_poc_443";
        String bbSpm = "bb_spm_443";
        targetProduct.getBand(aTotal).setName("iop_" + aTotal);
        targetProduct.getBand(aGelbstoff).setName("iop_" + aGelbstoff);
        targetProduct.getBand(aPigment).setName("iop_" + aPigment);
        Band aPocBand = targetProduct.getBand(aPoc);
        if (aPocBand != null) {
            aPocBand.setName("iop_" + aPoc);
        }
        targetProduct.getBand(bbSpm).setName("iop_" + bbSpm);
        addPatternToAutoGrouping(targetProduct, "iop");
    }

    protected void renameConcentrations(Product targetProduct) {
        targetProduct.getBand("tsm").setName("conc_tsm");
        targetProduct.getBand("chl_conc").setName("conc_chl");
        addPatternToAutoGrouping(targetProduct, "conc");
    }


    protected void renameTurbidityBand(Product targetProduct) {
        targetProduct.getBand("turbidity_index").setName("turbidity");
    }


    protected void addFLHBands(Product target) {
        Band flhBand = target.addBand(EXP_FLH_681_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setDescription("Fluorescence line height at 681 nm");
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = target.addBand(EXP_FLH_681_NORM_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = target.addBand(EXP_FLH_681_ALT_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = target.addBand(EXP_FLH_NORM_OLD_681_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setDescription("Fluorescence line height at 681 nm");
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        flhBand = target.addBand(EXP_FLH_ALT_OLD_681_NAME, ProductData.TYPE_FLOAT32);
        flhBand.setDescription("Fluorescence line height at 681 nm");
        flhBand.setNoDataValue(Float.NaN);
        flhBand.setNoDataValueUsed(true);
        addPatternToAutoGrouping(target, "exp");
    }

    protected void addPatternToAutoGrouping(Product targetProduct, String groupPattern) {
        Product.AutoGrouping autoGrouping = targetProduct.getAutoGrouping();
        String stringPattern = autoGrouping != null ? autoGrouping.toString() + ":" + groupPattern : groupPattern;
        targetProduct.setAutoGrouping(stringPattern);
    }

}
