package org.esa.beam.coastcolour.processing.ui;

import com.bc.ceres.core.CoreException;
import com.bc.ceres.core.runtime.ConfigurationElement;
import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * Action for L2R and L2W processing
 *
 * @author olafd
 */
public class L2RAction extends AbstractVisatAction {
    private ModelessDialog dialog;
    private String operatorName;
    private String dialogTitle;
    private String targetProductNameSuffix;

    @Override
    public void actionPerformed(CommandEvent event) {
        if (dialog == null) {
            dialog = createOperatorDialog();
        }
        dialog.show();
    }

    @Override
    public void configure(ConfigurationElement config) throws CoreException {
        operatorName = getConfigString(config, "operatorName");
        if (operatorName == null) {
            throw new CoreException("Missing DefaultOperatorAction property 'operatorName'.");
        }
        dialogTitle = getValue(config, "dialogTitle", operatorName);
        targetProductNameSuffix = getConfigString(config, "targetProductNameSuffix");
        super.configure(config);
    }

    protected ModelessDialog createOperatorDialog() {
        L2ProcessingDialog productDialog = new L2ProcessingDialog(operatorName,
                                                                  getAppContext(),
                                                                  dialogTitle,
                                                                  getHelpId());

//        DefaultSingleTargetProductDialog productDialog = new DefaultSingleTargetProductDialog(operatorName, getAppContext(),
//                                                                                              dialogTitle, getHelpId());

        if (targetProductNameSuffix != null) {
            productDialog.setTargetProductNameSuffix(targetProductNameSuffix);
        }
        return productDialog;
    }
}
