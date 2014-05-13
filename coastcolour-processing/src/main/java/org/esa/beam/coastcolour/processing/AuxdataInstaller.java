package org.esa.beam.coastcolour.processing;

import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.logging.BeamLogManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * This class is responsible for installing auxiliary data.
 *
 * @author Marco Peters
 * @since 1.3
 */
class AuxdataInstaller {

    private AuxdataInstaller() {
    }

    static void installAuxdata(Class providerClass) {
        installAuxdata(ResourceInstaller.getSourceUrl(providerClass));
    }

    static void installAuxdata(URL sourceBaseUrl) {
        installAuxdata(sourceBaseUrl, "auxdata/", "coastcolour/auxdata");
        installAuxdata(sourceBaseUrl, "auxdata/color_palettes/", "beam-ui/auxdata/color-palettes");
        installAuxdata(sourceBaseUrl, "auxdata/rgb_profiles/", "beam-core/auxdata/rgb_profiles");
    }

    private static void installAuxdata(URL sourceBaseUrl, String sourceRelPath, String targetRelPath) {
        final ResourceInstaller resourceInstaller = new ResourceInstaller(sourceBaseUrl, sourceRelPath, new File(SystemUtils.getApplicationDataDir(), targetRelPath));
        try {
            resourceInstaller.install(".*", com.bc.ceres.core.ProgressMonitor.NULL);
            BeamLogManager.getSystemLogger().info("Installed CoastColour auxiliary data '" + sourceRelPath + "'");
        } catch (IOException ignore) {
            BeamLogManager.getSystemLogger().severe("Failed to install CoastColour auxiliary data '" + sourceRelPath + "'");
        }
    }
}
