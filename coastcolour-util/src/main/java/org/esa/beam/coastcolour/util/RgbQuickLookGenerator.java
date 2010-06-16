package org.esa.beam.coastcolour.util;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glayer.support.ImageLayer;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.RGBImageProfile;
import org.esa.beam.framework.datamodel.RGBImageProfileManager;
import org.esa.beam.glevel.BandImageMultiLevelSource;

import javax.media.jai.PlanarImage;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.text.MessageFormat;

class RgbQuickLookGenerator {

    private final String[] rgbExpressions;

    RgbQuickLookGenerator(String name) {
        rgbExpressions = findRgbExpressions(name);
    }

    BufferedImage createQuickLookImage(Product product) {
        RGBImageProfile.storeRgbaExpressions(product, rgbExpressions);
        final Band[] rgbBands = {
                product.getBand(RGBImageProfile.RED_BAND_NAME),
                product.getBand(RGBImageProfile.GREEN_BAND_NAME),
                product.getBand(RGBImageProfile.BLUE_BAND_NAME),
        };
        final ImageLayer imageLayer = new ImageLayer(BandImageMultiLevelSource.create(rgbBands, ProgressMonitor.NULL));
        final RenderedImage image = imageLayer.getImage(imageLayer.getMultiLevelSource().getModel().getLevel(0.5));

        return PlanarImage.wrapRenderedImage(image).getAsBufferedImage();
    }

    private static String[] findRgbExpressions(String name) {
        for (final RGBImageProfile profile : RGBImageProfileManager.getInstance().getAllProfiles()) {
            if (profile.getName().equals(name)) {
                return profile.getRgbaExpressions();
            }
        }
        throw new IllegalArgumentException(MessageFormat.format("Cannot find RGB profile ''{0}''.", name));
    }
}
