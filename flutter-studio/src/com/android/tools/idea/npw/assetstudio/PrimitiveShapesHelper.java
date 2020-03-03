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

import java.awt.Graphics2D;

/**
 * Utility class to help with drawing primitives shapes (lines, rectangles, etc.) when
 *
 * <ol>
 *   <li>The pen stroke is assumed to be 1 pixel wide and expand to the "inside" of the shape
 *       outline, instead of the default which it expand to the "right".
 *       <p>For example, when drawing a rectangle at coordinates (0, 0) with a width/height of (4,
 *       4), we draw a line from (0, 0) to (3, 0), then to (3, 3) then to (3, 0) then to (0, 0).
 *       <pre>
 * oooo    ==>     xxxx
 * oooo    ==>     xoox
 * oooo    ==>     xoox
 * oooo    ==>     xxxx
 * </pre>
 *       <p>This also applies to lines. Drawing a line from (0,0) to (4, 4) ends up filling pixels
 *       (0,0), (1,1), (2,2), (3,3)
 *   <li>(X, Y) coordinates are scaled with the given {@link #scaleFactor}
 *       <p>For example, when drawing a rectangle at coordinates (0, 0) with a width/height of (4,
 *       4), and a scaling factor of 1.5, we draw a line from (0, 0) to (5, 0), then to (5, 5) then
 *       to (5, 0) then to (0, 0).
 *       <pre>
 * oooooo    ==>     xxxxxx
 * oooooo    ==>     xoooox
 * oooooo    ==>     xoooox
 * oooooo    ==>     xoooox
 * oooooo    ==>     xoooox
 * oooooo    ==>     xxxxxx
 * </pre>
 * </ol>
 *
 * <p>The purpose of this class is to make it easy to compose drawing of multiple primitive shapes
 * without having to take into account the "-1" adjustment required when specifying width/height (or
 * right/bottom) coordinates.
 *
 * <p>For example, to draw a 4x4 rectangle with a "+" cross inside:
 *
 * <pre>
 *     Graphics2D gOut = ...
 *     PrimitiveShapesHelper out = new PrimitiveShapesHelper(gOut, scaleFactor);
 *     out.drawRect(0, 0, 4, 4);
 *     out.drawLine(0, 2, 4, 2);
 *     out.drawLine(2, 0, 2, 4);
 * </pre>
 */
public class PrimitiveShapesHelper {
    private static final int STROKE_WIDTH = 1;
    private final Graphics2D out;
    private final double scaleFactor;

    public PrimitiveShapesHelper(Graphics2D out, double scaleFactor) {
        this.out = out;
        this.scaleFactor = scaleFactor;
    }

    @SuppressWarnings("SameParameterValue")
    public void drawRect(int x, int y, int width, int height) {
        this.out.drawRect(scaleX(x), scaleY(y), scaleWidth(x, width), scaleHeight(y, height));
    }

    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        this.out.drawRoundRect(
                scaleX(x),
                scaleY(y),
                scaleWidth(x, width),
                scaleHeight(y, height),
                scale(arcWidth),
                scale(arcHeight));
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        this.out.drawLine(
                scaleX(x1, x1, x2), scaleY(y1, y1, y2), scaleX(x2, x1, x2), scaleY(y2, y1, y2));
    }

    public void drawOval(int x, int y, int width, int height) {
        this.out.drawOval(scaleX(x), scaleY(y), scaleWidth(x, width), scaleHeight(y, height));
    }

    public void drawCenteredCircle(int xCenter, int yCenter, int radius) {
        drawOval(xCenter - radius, yCenter - radius, radius * 2, radius * 2);
    }

    public int scale(int value) {
        return (int)Math.round(value * this.scaleFactor);
    }

    public int scaleWidth(int x, int width) {
        int scaled = (int)Math.round(width * this.scaleFactor);
        return scaled - STROKE_WIDTH;
    }

    public int scaleHeight(int y, int height) {
        int scaled = (int)Math.round(height * this.scaleFactor);
        return scaled - STROKE_WIDTH;
    }

    public int scaleX(int x) {
        return scale(x);
    }

    public int scaleX(int x, int x1, int x2) {
        int scaled = scale(x);
        int max = Math.max(x1, x2);
        if (x == max) return scaled - STROKE_WIDTH;
        return scaled;
    }

    public int scaleY(int y) {
        return scale(y);
    }

    public int scaleY(int y, int y1, int y2) {
        int scaled = scale(y);
        int max = Math.max(y1, y2);
        if (y == max) return scaled - STROKE_WIDTH;
        return scaled;
    }
}
