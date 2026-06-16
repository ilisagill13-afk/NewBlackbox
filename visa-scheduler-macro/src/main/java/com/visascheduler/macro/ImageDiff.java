package com.visascheduler.macro;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Cheap perceptual difference score between two screenshots of the same
 * region. Both images are downscaled to a fixed small size first so minor
 * anti-aliasing / font-rendering jitter doesn't trigger false positives.
 */
public final class ImageDiff {

    private static final int SAMPLE_SIZE = 64;

    private ImageDiff() {}

    /**
     * @return average per-pixel RGB difference, range 0 (identical) to 765
     *         (completely different, i.e. black vs white on all 3 channels).
     */
    public static double score(BufferedImage a, BufferedImage b) {
        BufferedImage sa = downscale(a);
        BufferedImage sb = downscale(b);

        long total = 0;
        int count = 0;
        for (int y = 0; y < SAMPLE_SIZE; y++) {
            for (int x = 0; x < SAMPLE_SIZE; x++) {
                total += pixelDiff(sa.getRGB(x, y), sb.getRGB(x, y));
                count++;
            }
        }
        return total / (double) count;
    }

    private static int pixelDiff(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF, g1 = (rgb1 >> 8) & 0xFF, b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF, g2 = (rgb2 >> 8) & 0xFF, b2 = rgb2 & 0xFF;
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }

    private static BufferedImage downscale(BufferedImage img) {
        BufferedImage out = new BufferedImage(SAMPLE_SIZE, SAMPLE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE, null);
        g.dispose();
        return out;
    }
}
