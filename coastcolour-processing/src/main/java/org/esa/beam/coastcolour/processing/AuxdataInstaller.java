package org.esa.beam.coastcolour.processing;

import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;

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

    static void installAuxdata(URL sourceBaseUrl) {
        File defaultAuxdataDir = new File(SystemUtils.getApplicationDataDir(), "coastcolour/auxdata");
        final ResourceInstaller resourceInstaller = new ResourceInstaller(sourceBaseUrl, "auxdata/", defaultAuxdataDir);
        try {
            resourceInstaller.install(".*", com.bc.ceres.core.ProgressMonitor.NULL);
        } catch (IOException ignore) {
            // failed, so what
        }
    }
}
