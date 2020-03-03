/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio;

import com.android.ide.common.util.AssetUtil;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * A set of utility classes for rendering text to a {@link BufferedImage}, suitable for use as a
 * source image to {@link IconGenerator} objects.
 */
public class TextRenderUtil {
    /**
     * Renders the given string with the provided {@link Options} to a {@link BufferedImage}.
     *
     * @param text The text to render.
     * @param paddingPercentage If nonzero, a percentage of the width or height
     *            (whichever is smaller) to add as padding around the text
     * @param options The optional parameters for rendering the text.
     * @return An image, suitable for use as an input to a {@link IconGenerator}.
     */
    public static BufferedImage renderTextImage(String text, int paddingPercentage, Options options) {
        if (options == null) {
            options = new Options();
        }

        BufferedImage tempImage = AssetUtil.newArgbBufferedImage(1, 1);
        if (text == null || text.isEmpty()) {
            return tempImage;
        }

        Graphics2D tempG = (Graphics2D) tempImage.getGraphics();

        Font font = options.font;
        if (font == null) {
            font = new Font(options.fontName, options.fontStyle, options.fontSize);
            // Map<TextAttribute, Object> map = new Hashtable<TextAttribute, Object>();
            // map.put(TextAttribute.TRACKING, 0.3);
            // font = font.deriveFont(map);
        }

        FontRenderContext frc = tempG.getFontRenderContext();

        TextLayout layout = new TextLayout(text, font, frc);
        Rectangle2D bounds = layout.getBounds();

        // The padding is a percentage relative to the overall minimum of the width or height
        if (paddingPercentage != 0) {
            double minDimension = Math.min(bounds.getWidth(), bounds.getHeight());
            double delta = minDimension * paddingPercentage / 100;
            bounds.setRect(bounds.getMinX() - delta, bounds.getMinY() - delta,
                    bounds.getWidth() + 2 * delta, bounds.getHeight() + 2 * delta);
        }

        BufferedImage image = AssetUtil.newArgbBufferedImage(Math.max(1, (int)bounds.getWidth()),
                                                             Math.max(1, (int)bounds.getHeight()));
        Graphics2D g = (Graphics2D) image.getGraphics();
        g.setColor(new Color(options.foregroundColor, true));
        g.setFont(font);

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.drawString(text, (float) -bounds.getX(), (float) -bounds.getY());

        g.dispose();
        tempG.dispose();

        return image;
    }

    /**
     * The parameters for text rendering. There are no required values so a <code>new
     * Options()</code> object is considered valid.
     */
    public static class Options {
        // We use a large default font size to reduce the need to scale generated images up.
        // TODO: Instead, a graphic generator should use a different source image for each density.
        private static final int DEFAULT_FONT_SIZE = 512;

        /** Foreground color to render text with, as an AARRGGBB packed integer */
        public int foregroundColor = 0xFFFFFFFF;

        /**
         * The optional {@link Font} to use. If null, a {@link Font} object will be generated using
         * the other options.
         */
        public Font font = null;

        /**
         * The optional font name. Defaults to {@link Font#SERIF}.
         *
         * @see Font#Font(String, int, int)
         */
        public String fontName = Font.SERIF;

        /**
         * The optional font styling (bold and/or italic). Defaults to no styling.
         *
         * @see Font#Font(String, int, int)
         */
        public int fontStyle = 0;

        /**
         * The optional font size, in points. Defaults to a very large font size, to prevent
         * up-scaling rendered text.
         *
         * @see Font#Font(String, int, int)
         */
        public int fontSize = DEFAULT_FONT_SIZE;
    }
}
