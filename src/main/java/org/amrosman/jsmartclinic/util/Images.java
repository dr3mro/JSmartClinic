package org.amrosman.jsmartclinic.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/** Image helpers used for patient photos (kept toolkit-independent). */
public final class Images {

    private Images() {
    }

    public static byte[] resizeToPng(File file, int maxDimension) {
        try {
            BufferedImage source = ImageIO.read(file);
            if (source == null) {
                throw new IllegalArgumentException("Unsupported image file");
            }
            int width = source.getWidth();
            int height = source.getHeight();
            double scale = Math.min(1.0, maxDimension / (double) Math.max(width, height));
            int targetW = Math.max(1, (int) Math.round(width * scale));
            int targetH = Math.max(1, (int) Math.round(height * scale));
            BufferedImage target = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = target.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, targetW, targetH, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(target, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read image: " + e.getMessage(), e);
        }
    }
}
