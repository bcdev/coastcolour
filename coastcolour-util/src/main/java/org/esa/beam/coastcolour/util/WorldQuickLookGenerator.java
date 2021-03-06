package org.esa.beam.coastcolour.util;

import com.bc.ceres.glayer.support.ImageLayer;
import com.bc.ceres.glayer.support.ShapeLayer;
import com.bc.ceres.grender.Rendering;
import com.bc.ceres.grender.Viewport;
import com.bc.ceres.grender.support.BufferedImageRendering;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.ProductUtils;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WorldQuickLookGenerator {

    private final Color lineColor;
    private final Color fillColor;
    private final List<Path2D> pathList;

    public WorldQuickLookGenerator() {
        this(Color.BLACK, Color.WHITE);
    }

    public WorldQuickLookGenerator(Color lineColor, Color fillColor) {
        this.lineColor = lineColor;
        this.fillColor = fillColor;
        pathList = new ArrayList<Path2D>();
    }

    public void addProduct(Product product) {
        final Path2D[] paths = ProductUtils.createGeoBoundaryPaths(product);
        pathList.addAll(Arrays.asList(paths));
    }

    public BufferedImage createQuickLookImage(BufferedImage worldImage) {
        final int w = worldImage.getWidth();
        final int h = worldImage.getHeight();
        final AffineTransform s2i = new AffineTransform();
        s2i.translate(0.0, h);
        s2i.scale(1.0, -1.0);
        s2i.scale(w / 360.0, h / 180.0);
        s2i.translate(180.0, 90.0);
        final ShapeLayer shapeLayer = new ShapeLayer(pathList.toArray(new Path2D[pathList.size()]), s2i) {
            @Override
            protected void renderLayer(Rendering rendering) {
                final Graphics2D g = rendering.getGraphics();
                final Viewport vp = rendering.getViewport();
                final AffineTransform transformSave = g.getTransform();
                try {
                    final AffineTransform transform = new AffineTransform();
                    transform.concatenate(vp.getModelToViewTransform());
                    transform.concatenate(getShapeToModelTransform());
                    g.setTransform(transform);
                    for (Shape shape : getShapeList()) {
                        g.setPaint(fillColor);
                        g.fill(shape);
                        g.setPaint(lineColor);
                        g.draw(shape);
                    }
                } finally {
                    g.setTransform(transformSave);
                }
            }

        };
        shapeLayer.setTransparency(0.5);
        final ImageLayer imageLayer = new ImageLayer(worldImage);
        imageLayer.getChildren().add(shapeLayer);

        final BufferedImage quickLookImage = new BufferedImage(w, h, worldImage.getType());
        final Rendering rendering = new BufferedImageRendering(quickLookImage);
        imageLayer.render(rendering);

        return quickLookImage;
    }
}
