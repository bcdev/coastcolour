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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;

class RgbQuickLookGenerator {

    private final RGBImageProfile profile;

    RgbQuickLookGenerator(String rgbProfile) throws IOException {
        final File file = new File(rgbProfile);

        if (file.isFile() && file.canRead()) {
            this.profile = RGBImageProfile.loadProfile(file);
        } else {
            this.profile = findRgbProfile(rgbProfile);
        }
        if (rgbProfile == null) {
            throw new FileNotFoundException(MessageFormat.format(
                    "Cannot find RGB image profile ''{0}''.", file.getPath()));
        }
    }

    boolean isApplicableTo(Product product) {
        return profile.isApplicableTo(product);
    }

    BufferedImage createQuickLookImage(Product product) {
        if (isApplicableTo(product)) {
            RGBImageProfile.storeRgbaExpressions(product, profile.getRgbaExpressions());
            final Band[] rgbBands = {
                    product.getBand(RGBImageProfile.RED_BAND_NAME),
                    product.getBand(RGBImageProfile.GREEN_BAND_NAME),
                    product.getBand(RGBImageProfile.BLUE_BAND_NAME),
            };
            final ImageLayer imageLayer = new ImageLayer(
                    BandImageMultiLevelSource.create(rgbBands, ProgressMonitor.NULL));
            final RenderedImage image = imageLayer.getImage(imageLayer.getMultiLevelSource().getModel().getLevel(4.0));

            return PlanarImage.wrapRenderedImage(image).getAsBufferedImage();
        }
        throw new IllegalArgumentException(MessageFormat.format(
                "RGB profile is not applicable to product ''{0}''.", product.getFileLocation()));
    }

    private static RGBImageProfile findRgbProfile(String name) {
        for (final RGBImageProfile profile : RGBImageProfileManager.getInstance().getAllProfiles()) {
            if (profile.getName().toLowerCase().contains(name.toLowerCase())) {
                return profile;
            }
        }
        return null;
    }
}
