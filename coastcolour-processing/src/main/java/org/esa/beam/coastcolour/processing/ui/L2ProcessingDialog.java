package org.esa.beam.coastcolour.processing.ui;

import com.bc.ceres.binding.*;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.binding.PropertyPane;
import com.bc.ceres.swing.selection.AbstractSelectionChangeListener;
import com.bc.ceres.swing.selection.Selection;
import com.bc.ceres.swing.selection.SelectionChangeEvent;
import org.esa.beam.coastcolour.processing.CoastcolourConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeEvent;
import org.esa.beam.framework.datamodel.ProductNodeListener;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.descriptor.OperatorDescriptor;
import org.esa.beam.framework.gpf.internal.RasterDataNodeValues;
import org.esa.beam.framework.gpf.ui.*;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.UIUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Specific dialog for L2R and L2W processing:
 * allows to disable upstream processing parameters depending on input product.
 *
 * @author olafd
 */
public class L2ProcessingDialog extends SingleTargetProductDialog {

    private final String operatorName;
    private final OperatorDescriptor operatorDescriptor;
    private DefaultIOParametersPanel ioParametersPanel;
    private final OperatorParameterSupport parameterSupport;
    private final BindingContext bindingContext;

    private JTabbedPane form;
    private PropertyDescriptor[] rasterDataNodeTypeProperties;
    private String targetProductNameSuffix;
    private ProductChangedHandler productChangedHandler;
    private JPanel parametersPanel;


    public L2ProcessingDialog(String operatorName, AppContext appContext, String title, String helpID) {
        super(appContext, title, ID_APPLY_CLOSE, helpID);
        this.operatorName = operatorName;
        targetProductNameSuffix = "";

        OperatorSpi operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
        if (operatorSpi == null) {
            throw new IllegalArgumentException("No SPI found for operator name '" + operatorName + "'");
        }

        operatorDescriptor = operatorSpi.getOperatorDescriptor();
        ioParametersPanel = new DefaultIOParametersPanel(getAppContext(), operatorDescriptor, getTargetProductSelector());

        parameterSupport = new OperatorParameterSupport(operatorDescriptor);
        final ArrayList<SourceProductSelector> sourceProductSelectorList = ioParametersPanel.getSourceProductSelectorList();
        final PropertySet propertySet = parameterSupport.getPropertySet();
        bindingContext = new BindingContext(propertySet);

        if (propertySet.getProperties().length > 0) {
            if (!sourceProductSelectorList.isEmpty()) {
                Property[] properties = propertySet.getProperties();
                List<PropertyDescriptor> rdnTypeProperties = new ArrayList<>(properties.length);
                for (Property property : properties) {
                    PropertyDescriptor parameterDescriptor = property.getDescriptor();
                    if (parameterDescriptor.getAttribute(RasterDataNodeValues.ATTRIBUTE_NAME) != null) {
                        rdnTypeProperties.add(parameterDescriptor);
                    }
                }
                rasterDataNodeTypeProperties = rdnTypeProperties.toArray(
                        new PropertyDescriptor[rdnTypeProperties.size()]);
            }
        }
        productChangedHandler = new ProductChangedHandler();
        if (!sourceProductSelectorList.isEmpty()) {
            sourceProductSelectorList.get(0).addSelectionChangeListener(productChangedHandler);
        }
    }

    @Override
    public int show() {
        ioParametersPanel.initSourceProductSelectors();
        if (form == null) {
            initForm();
            if (getJDialog().getJMenuBar() == null) {
                final OperatorMenu operatorMenu = createDefaultMenuBar();
                getJDialog().setJMenuBar(operatorMenu.createDefaultMenu());
            }
        }
        setContent(form);
        return super.show();
    }

    @Override
    public void hide() {
        productChangedHandler.releaseProduct();
        ioParametersPanel.releaseSourceProductSelectors();
        super.hide();
    }

    @Override
    protected Product createTargetProduct() throws Exception {
        final HashMap<String, Product> sourceProducts = ioParametersPanel.createSourceProductsMap();
        return GPF.createProduct(operatorName, parameterSupport.getParameterMap(), sourceProducts);
    }

    public String getTargetProductNameSuffix() {
        return targetProductNameSuffix;
    }

    public void setTargetProductNameSuffix(String suffix) {
        targetProductNameSuffix = suffix;
    }

    private void initForm() {
        form = new JTabbedPane();
        form.add("I/O Parameters", ioParametersPanel);

        if (bindingContext.getPropertySet().getProperties().length > 0) {
            final PropertyPane parametersPane = new PropertyPane(bindingContext);
            parametersPanel = parametersPane.createPanel();
            parametersPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
            form.add("Processing Parameters", new JScrollPane(parametersPanel));
            updateSourceProduct();
        }
    }

    private OperatorMenu createDefaultMenuBar() {
        return new OperatorMenu(getJDialog(),
                                operatorDescriptor,
                                parameterSupport,
                                getAppContext(),
                                getHelpID());
    }

    private void updateSourceProduct() {
        try {
            Property property = bindingContext.getPropertySet().getProperty(UIUtils.PROPERTY_SOURCE_PRODUCT);
            if (property != null) {
                property.setValue(productChangedHandler.currentProduct);
            }
        } catch (ValidationException e) {
            throw new IllegalStateException("Property '" + UIUtils.PROPERTY_SOURCE_PRODUCT + "' must be of type " + Product.class + ".", e);
        }
    }

    private void checkComponentsToDisable(String productType) {
        if (form == null) {
            initForm();
        }
        if (parametersPanel != null && parametersPanel.getComponents() != null) {
            Component[] components = parametersPanel.getComponents();
            for (int i = 0; i < components.length - 1; i++) {
                if (isComponentToDisable(productType, components[i])) {
                    components[i].setEnabled(false);
                } else {
                    components[i].setEnabled(true);
                }
            }
        }
    }

    private boolean isComponentToDisable(String productType, Component component) {
        final boolean l1pComponentToDisable =
                (isCoastcolourL1PProduct(productType) || isCoastcolourL2RProduct(productType)) &&
                        isComponentofL1PParameter(component);
        final boolean l2rComponentToDisable =
                isCoastcolourL2RProduct(productType) &&
                        isComponentofL2RParameter(component);

        return (operatorName.equals("CoastColour.L2R") && l1pComponentToDisable) ||
                (operatorName.equals("CoastColour.L2W") &&
                        (l1pComponentToDisable || l2rComponentToDisable));
    }

    private synchronized boolean isCoastcolourL1PProduct(String productType) {
        return productType != null && productType.endsWith("CCL1P");
    }

    private synchronized boolean isCoastcolourL2RProduct(String productType) {
        return productType != null && productType.endsWith("CCL2R");
    }

    private boolean isComponentofL1PParameter(Component component) {
        for (int i = 0; i < CoastcolourConstants.L1P_PARAMETER_NAMES.length; i++) {
            String l1pParameterName = CoastcolourConstants.L1P_PARAMETER_NAMES[i];
            if (component.getName() != null && component.getName().equals(l1pParameterName)) {
                return true;
            }
        }
        if (component instanceof JLabel && ((JLabel) component).getText().contains("[L1P]")) {
            return true;
        }
        return false;
    }

    private boolean isComponentofL2RParameter(Component component) {
        for (int i = 0; i < CoastcolourConstants.L2R_PARAMETER_NAMES.length; i++) {
            String l2rParameterName = CoastcolourConstants.L2R_PARAMETER_NAMES[i];
            if (component.getName() != null && component.getName().equals(l2rParameterName)) {
                return true;
            }
        }
        if (component instanceof JLabel && ((JLabel) component).getText().contains("[L2R]")) {
            return true;
        }
        return false;
    }


    private class ProductChangedHandler extends AbstractSelectionChangeListener implements ProductNodeListener {

        private Product currentProduct;

        public void releaseProduct() {
            if (currentProduct != null) {
                currentProduct.removeProductNodeListener(this);
                currentProduct = null;
                updateSourceProduct();
            }
        }

        @Override
        public void selectionChanged(SelectionChangeEvent event) {
            Selection selection = event.getSelection();
            if (selection != null) {
                final Product selectedProduct = (Product) selection.getSelectedValue();
                if (selectedProduct != null) {
                    String productType = selectedProduct.getProductType();
                    if (selectedProduct != currentProduct) {
                        if (currentProduct != null) {
                            currentProduct.removeProductNodeListener(this);
                        }
                        currentProduct = selectedProduct;
                        currentProduct.addProductNodeListener(this);
                        updateTargetProductName();
                        updateValueSets(currentProduct);
                        updateSourceProduct();
                    }
                    checkComponentsToDisable(productType);
                }
            }
        }




        @Override
        public void nodeAdded(ProductNodeEvent event) {
            handleProductNodeEvent();
        }

        @Override
        public void nodeChanged(ProductNodeEvent event) {
            handleProductNodeEvent();
        }

        @Override
        public void nodeDataChanged(ProductNodeEvent event) {
            handleProductNodeEvent();
        }

        @Override
        public void nodeRemoved(ProductNodeEvent event) {
            handleProductNodeEvent();
        }

        private void updateTargetProductName() {
            String productName = "";
            if (currentProduct != null) {
                productName = currentProduct.getName();
            }
            final TargetProductSelectorModel targetProductSelectorModel = getTargetProductSelector().getModel();
            targetProductSelectorModel.setProductName(productName + getTargetProductNameSuffix());
        }

        private void handleProductNodeEvent() {
            updateValueSets(currentProduct);
        }

        private void updateValueSets(Product product) {
            if (rasterDataNodeTypeProperties != null) {
                for (PropertyDescriptor propertyDescriptor : rasterDataNodeTypeProperties) {
                    updateValueSet(propertyDescriptor, product);
                }
            }
        }
    }

    private static void updateValueSet(PropertyDescriptor propertyDescriptor, Product product) {
        String[] values = new String[0];
        if (product != null) {
            Object object = propertyDescriptor.getAttribute(RasterDataNodeValues.ATTRIBUTE_NAME);
            if (object != null) {
                @SuppressWarnings("unchecked")
                Class<? extends RasterDataNode> rasterDataNodeType = (Class<? extends RasterDataNode>) object;
                boolean includeEmptyValue = !propertyDescriptor.isNotNull() && !propertyDescriptor.isNotEmpty() &&
                        !propertyDescriptor.getType().isArray();
                values = RasterDataNodeValues.getNames(product, rasterDataNodeType, includeEmptyValue);
            }
        }
        propertyDescriptor.setValueSet(new ValueSet(values));
    }
}
