/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.idea.npwOld.assetstudio.assets;

import com.android.SdkConstants;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.Layer;
import com.android.tools.pixelprobe.PixelProbe;
import com.android.tools.pixelprobe.ShapeInfo;
import com.android.tools.pixelprobe.decoder.Decoder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Loads a layered image from a file and converts it to a Vector Drawable XML representation.
 */
class LayeredImageConverter {
  private DecimalFormat myFormat;
  private final DecimalFormat myMiterFormat = new DecimalFormat("#.####");
  private final DecimalFormat myOpacityFormat = new DecimalFormat("#.##");

  LayeredImageConverter() {
  }

  /**
   * Loads the specified file as a pixelprobe {@link Image}, finds its vector layers
   * and converts them to a Vector Drawable XML representation.
   *
   * @param path The file to convert to Vector Drawable
   * @return The XML representation of a Vector Drawable
   *
   * @throws IOException If an error occur while parsing the file
   */
  @NotNull
  String toVectorDrawableXml(@NotNull File path) throws IOException {
    FileInputStream in = new FileInputStream(path);
    Image image = PixelProbe.probe(in, new Decoder.Options()
      .decodeLayerImageData(false)
      .decodeLayerTextData(false)
      .decodeLayerAdjustmentData(false)
      .decodeGuides(false));

    Rectangle2D.Double bounds = new Rectangle2D.Double(0.0, 0.0, image.getWidth(), image.getHeight());
    myFormat = createDecimalFormat((float) bounds.getWidth(), (float) bounds.getHeight());

    Element vector = new Element(SdkConstants.TAG_VECTOR);
    extractPathLayers(vector, image.getLayers());

    vector
      .attribute("width", String.valueOf((int) bounds.getWidth()) + "dp")
      .attribute("height", String.valueOf((int) bounds.getHeight()) + "dp")
      .attribute("viewportWidth", String.valueOf((int) bounds.getWidth()))
      .attribute("viewportHeight", String.valueOf((int) bounds.getHeight()));

    String xml = toVectorDrawable(vector);
    in.close();
    return xml;
  }

  /**
   * Extracts all the vector layers from the specified list and transforms them into an
   * XML representation.
   * @param root Root element of the Vector Drawable XML representation
   * @param layers List of layers to traverse
   */
  private void extractPathLayers(@NotNull Element root, @NotNull List<Layer> layers) {
    for (int i = 0; i < layers.size(); i++) {
      Layer layer = layers.get(i);
      if (!layer.isVisible()) continue;

      Layer.Type type = layer.getType();
      if (type == Layer.Type.SHAPE) {
        if (layer.getShapeInfo().getStyle() == ShapeInfo.Style.NONE) continue;

        Shape path = getTransformedPath(layer);
        if (path.getBounds2D().isEmpty()) continue;

        Area clipPath = null;

        float opacityModifier = 1.0f;
        boolean fullyClipped = false;

        // The layer is clipped by the next clipping base
        // We only support shape clipping bases
        if (!layer.isClipBase()) {
          // The clipping base is only valid in the current group
          // (it might be another group)
          for (int j = i + 1; j < layers.size(); j++) {
            Layer clipBase = layers.get(j);
            if (clipBase.isClipBase()) {
              if (!clipBase.isVisible()) {
                fullyClipped = true;
                break;
              }

              // TODO: handle group clipping bases (take all their shapes)
              if (clipBase.getType() != Layer.Type.SHAPE) {
                break;
              }

              opacityModifier = clipBase.getOpacity();

              // TODO: use clip-path instead of areas
              Area source = new Area(path);
              clipPath = new Area(getTransformedPath(clipBase));
              source.intersect(clipPath);
              path = source;

              break;
            }
          }
        }

        if (!fullyClipped) {
          Element element = new Element("path");

          boolean hasFillOrStroke;
          hasFillOrStroke = extractFill(layer, element, opacityModifier);
          hasFillOrStroke |= extractStroke(layer, path, clipPath, root, element, opacityModifier);

          if (hasFillOrStroke) {
            element.attribute("name", StringUtil.escapeXml(layer.getName()));
            element.attribute("pathData", toPathData(path, myFormat));
            root.childAtFront(element);
          }
        }
      }
      else if (type == Layer.Type.GROUP) {
        extractPathLayers(root, layer.getChildren());
      }
    }
  }

  @NotNull
  private static Shape getTransformedPath(Layer layer) {
    List<ShapeInfo.Path> paths = layer.getShapeInfo().getPaths();
    if (paths.isEmpty()) return new Path2D.Float();

    Rectangle2D layerBounds = layer.getBounds();
    AffineTransform transform = AffineTransform.getTranslateInstance(layerBounds.getX(), layerBounds.getY());

    Area area;
    ShapeInfo.Path shapePath = paths.get(0);
    // Peek to see if the first path is a subtract operation
    if (shapePath.getOp() == ShapeInfo.PathOp.SUBTRACT) {
      area = new Area(new Rectangle2D.Double(0.0, 0.0, layerBounds.getWidth(), layerBounds.getHeight()));
      area.subtract(new Area(shapePath.getPath()));
    } else {
      Path2D path = shapePath.getPath();
      if (paths.size() == 1) {
        path.transform(transform);
        return path;
      }
      area = new Area(path);
    }

    for (int i = 1; i < paths.size(); i++) {
      shapePath = paths.get(i);
      switch (shapePath.getOp()) {
        case ADD:
          area.add(new Area(shapePath.getPath()));
          break;
        case SUBTRACT:
          area.subtract(new Area(shapePath.getPath()));
          break;
        case INTERSECT:
          area.intersect(new Area(shapePath.getPath()));
          break;
        case EXCLUSIVE_OR:
          area.exclusiveOr(new Area(shapePath.getPath()));
          break;
      }
    }

    area.transform(transform);

    return area;
  }

  private boolean extractStroke(@NotNull Layer layer, @NotNull Shape path, @Nullable Area clipPath,
                                @NotNull Element root, @NotNull Element element, float opacityModifier) {
    ShapeInfo shapeInfo = layer.getShapeInfo();
    if (shapeInfo.getStyle() != ShapeInfo.Style.FILL) {
      boolean isBasicStroke = shapeInfo.getStroke() instanceof BasicStroke;
      if (isBasicStroke) {
        BasicStroke stroke = (BasicStroke)shapeInfo.getStroke();
        if (stroke.getDashArray() != null || clipPath != null ||
            shapeInfo.getStrokeAlignment() != ShapeInfo.Alignment.CENTER) {
          extractStrokeAsPath(layer, path, clipPath, root, opacityModifier);
          return false;
        }
      }

      Paint strokePaint = shapeInfo.getStrokePaint();
      //noinspection UseJBColor
      Color color = Color.BLACK;
      if (strokePaint instanceof Color) color = (Color)strokePaint;
      float strokeAlpha = layer.getOpacity() * shapeInfo.getStrokeOpacity() * opacityModifier;

      element.attribute("strokeColor", "#" + optimizedHex(color));
      if (strokeAlpha < 1.0f) {
        element.attribute("strokeAlpha", myOpacityFormat.format(strokeAlpha));
      }

      if (isBasicStroke) {
        BasicStroke stroke = (BasicStroke)shapeInfo.getStroke();
        element.attribute("strokeWidth", myFormat.format(stroke.getLineWidth()));
        if (stroke.getLineJoin() != BasicStroke.JOIN_MITER) {
          element.attribute("strokeLineJoin", getJoinValue(stroke.getLineJoin()));
        } else {
          element.attribute("strokeMiterLimit", myMiterFormat.format(stroke.getMiterLimit()));
        }
        if (stroke.getEndCap() != BasicStroke.CAP_BUTT) {
          element.attribute("strokeLineCap", getCapValue(stroke.getEndCap()));
        }
      } else {
        element.attribute("strokeWidth", String.valueOf(0.0f));
      }

      return true;
    }

    return false;
  }

  private void extractStrokeAsPath(@NotNull Layer layer, @NotNull Shape path, @Nullable Area clipPath,
                                   @NotNull Element root, float opacityModifier) {
    ShapeInfo shapeInfo = layer.getShapeInfo();
    BasicStroke stroke = (BasicStroke)shapeInfo.getStroke();

    Shape strokedPath = null;
    switch (shapeInfo.getStrokeAlignment()) {
      case INSIDE: {
          strokedPath = copyStroke(stroke, 2.0f).createStrokedShape(path);
          strokedPath = new Area(strokedPath);
          ((Area) strokedPath).intersect(new Area(path));
        }
        break;
      case CENTER:
        strokedPath = stroke.createStrokedShape(path);
        if (clipPath != null) {
          strokedPath = new Area(strokedPath);
        }
        break;
      case OUTSIDE:
        strokedPath = copyStroke(stroke, 2.0f).createStrokedShape(path);
        strokedPath = new Area(strokedPath);
        ((Area) strokedPath).subtract(new Area(path));
        break;
    }

    // TODO: use clip-path instead of areas
    if (clipPath != null) {
      ((Area) strokedPath).intersect(new Area(clipPath));
    }

    Paint strokePaint = shapeInfo.getStrokePaint();
    //noinspection UseJBColor
    Color color = Color.BLACK;
    if (strokePaint instanceof Color) color = (Color)strokePaint;
    float strokeAlpha = layer.getOpacity() * shapeInfo.getStrokeOpacity() * opacityModifier;

    Element element = new Element("path")
      .attribute("name", StringUtil.escapeXml(layer.getName()))
      .attribute("pathData", toPathData(strokedPath, myFormat))
      .attribute("fillColor", "#" + optimizedHex(color));

    if (strokeAlpha < 1.0f) {
      element.attribute("fillAlpha", myOpacityFormat.format(strokeAlpha));
    }

    root.childAtFront(element);
  }

  private static String optimizedHex(Color color) {
    if (color.getRed() == color.getGreen() && color.getRed() == color.getBlue()) {
      char r = Integer.toHexString(color.getRed()).charAt(0);
      return new String(new char[] { r, r, r });
    }
    return ColorUtil.toHex(color);
  }

  @NotNull
  private static BasicStroke copyStroke(@NotNull BasicStroke stroke, float widthScale) {
    return new BasicStroke(stroke.getLineWidth() * widthScale,
                           stroke.getEndCap(),
                           stroke.getLineJoin(),
                           stroke.getMiterLimit(),
                           stroke.getDashArray(),
                           stroke.getDashPhase());
  }

  private boolean extractFill(@NotNull Layer layer, @NotNull Element element, float opacityModifier) {
    ShapeInfo shapeInfo = layer.getShapeInfo();
    if (shapeInfo.getStyle() != ShapeInfo.Style.STROKE) {
      Paint fillPaint = shapeInfo.getFillPaint();
      //noinspection UseJBColor
      Color color = Color.BLACK;
      if (fillPaint instanceof Color) color = (Color)fillPaint;
      float fillAlpha = layer.getOpacity() * shapeInfo.getFillOpacity() * opacityModifier;

      element.attribute("fillColor", "#" + optimizedHex(color));
      if (fillAlpha < 1.0f) {
        element.attribute("fillAlpha", myOpacityFormat.format(fillAlpha));
      }
      return true;
    }
    return false;
  }

  @NotNull
  private static String getCapValue(int endCap) {
    switch (endCap) {
      case BasicStroke.CAP_BUTT: return "butt";
      case BasicStroke.CAP_ROUND: return "round";
      case BasicStroke.CAP_SQUARE: return "square";
    }
    return "inherit";
  }

  @NotNull
  private static String getJoinValue(int lineJoin) {
    switch (lineJoin) {
      case BasicStroke.JOIN_BEVEL: return "bevel";
      case BasicStroke.JOIN_ROUND: return "round";
      case BasicStroke.JOIN_MITER: return "miter";
    }
    return "inherit";
  }

  @NotNull
  private static String toPathData(@NotNull Shape path, @NotNull DecimalFormat format) {
    StringBuilder buffer = new StringBuilder(1024);

    float[] coords = new float[6];
    PathIterator iterator = path.getPathIterator(null);

    float lastX = 0.0f;
    float lastY = 0.0f;
    float firstX = 0.0f;
    float firstY = 0.0f;

    boolean implicitLineTo = false;

    while (!iterator.isDone()) {
      int segment = iterator.currentSegment(coords);
      switch (segment) {
        case PathIterator.SEG_MOVETO:
          buffer.append('m');
          buffer.append(cleanup(coords[0] - lastX, format));
          buffer.append(' ');
          buffer.append(cleanup(coords[1] - lastY, format));
          firstX = lastX = coords[0];
          firstY = lastY = coords[1];
          implicitLineTo = true;
          break;
        case PathIterator.SEG_LINETO:
          if (coords[0] == lastX) {
            if (coords[1] != lastY) {
              buffer.append('v');
              buffer.append(cleanup(coords[1] - lastY, format));
              implicitLineTo = false;
            }
          } else if (coords[1] == lastY) {
            if (coords[0] != lastX) {
              buffer.append('h');
              buffer.append(cleanup(coords[0] - lastX, format));
              implicitLineTo = false;
            }
          } else if (coords[0] != lastX && coords[1] != lastY) {
            buffer.append(implicitLineTo ? ' ' : 'l');
            buffer.append(cleanup(coords[0] - lastX, format));
            buffer.append(' ');
            buffer.append(cleanup(coords[1] - lastY, format));
          }
          lastX = coords[0];
          lastY = coords[1];
          break;
        case PathIterator.SEG_CUBICTO:
          buffer.append('c');
          buffer.append(cleanup(coords[0] - lastX, format));
          buffer.append(' ');
          buffer.append(cleanup(coords[1] - lastY, format));
          buffer.append(' ');
          buffer.append(cleanup(coords[2] - lastX, format));
          buffer.append(' ');
          buffer.append(cleanup(coords[3] - lastY, format));
          buffer.append(' ');
          buffer.append(cleanup(coords[4] - lastX, format));
          buffer.append(' ');
          buffer.append(cleanup(coords[5] - lastY, format));
          implicitLineTo = false;
          lastX = coords[4];
          lastY = coords[5];
          break;
        case PathIterator.SEG_QUADTO:
          buffer.append('q');
          buffer.append(cleanup(coords[0] - lastX, format));
          buffer.append(' ');
          buffer.append(cleanup(coords[1] - lastY, format));
          buffer.append(' ');
          buffer.append(cleanup(coords[2] - lastX, format));
          buffer.append(' ');
          buffer.append(cleanup(coords[3] - lastY, format));
          implicitLineTo = false;
          lastX = coords[2];
          lastY = coords[3];
          break;
        case PathIterator.SEG_CLOSE:
          buffer.append('z');
          lastX = firstX;
          lastY = firstY;
          break;
      }

      iterator.next();
    }

    return buffer.toString();
  }

  @NotNull
  private static DecimalFormat createDecimalFormat(float viewportWidth, float viewportHeight) {
    float minSize = Math.min(viewportHeight, viewportWidth);
    float exponent = Math.round(Math.log10(minSize));

    int decimalPlace = (int) Math.floor(exponent - 4);
    String decimalFormatString = "#";
    if (decimalPlace < 0) {
      // Build a string with decimal places for "#.##...", and cap on 6 digits.
      if (decimalPlace < -6) {
        decimalPlace = -6;
      }
      decimalFormatString += ".";
      for (int i = 0 ; i < -decimalPlace; i++) {
        decimalFormatString += "#";
      }
    }

    DecimalFormatSymbols fractionSeparator = new DecimalFormatSymbols();
    fractionSeparator.setDecimalSeparator('.');

    DecimalFormat decimalFormat = new DecimalFormat(decimalFormatString, fractionSeparator);
    decimalFormat.setRoundingMode(RoundingMode.HALF_UP);

    return decimalFormat;
  }

  @NotNull
  private static String cleanup(float value, @NotNull DecimalFormat format) {
    if (value == (long) value) {
      return String.valueOf((long) value);
    } else {
      return format.format(value);
    }
  }

  @NotNull
  private static String toVectorDrawable(@NotNull Element element) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter out = new PrintWriter(stringWriter);
    outputElement(element, out, true, 0);
    out.flush();
    return stringWriter.toString();
  }

  private static void outputElement(@NotNull Element element, @NotNull PrintWriter out, boolean isRoot, int indent) {
    indent(out, indent);
    out.write("<");
    out.write(element.name);
    if (isRoot) out.write(" xmlns:android=\"http://schemas.android.com/apk/res/android\"");
    out.write("\n");

    boolean hasChildren = !element.children.isEmpty();

    indent++;
    outputAttributes(element, out, indent);
    if (hasChildren) {
      out.write(">\n");
      outputChildren(element, out, indent);
    } else {
      out.write(" />");
    }
    indent--;

    if (hasChildren) {
      indent(out, indent);
      out.write("</");
      out.write(element.name);
      out.write(">");
    }
    out.write("\n");
  }

  private static void outputChildren(@NotNull Element element, @NotNull PrintWriter out, int indent) {
    for (Element child : element.children) {
      outputElement(child, out, false, indent);
    }
  }

  private static void outputAttributes(@NotNull Element element, @NotNull PrintWriter out, int indent) {
    List<Attribute> attributes = element.attributes;
    int size = attributes.size();

    for (int i = 0; i < size; i++) {
      Attribute attribute = attributes.get(i);
      indent(out, indent);
      out.write("android:");
      out.write(attribute.name);
      out.write("=\"");
      out.write(attribute.value);
      out.write("\"");
      if (i != size - 1) out.write("\n");
    }
  }

  private static void indent(@NotNull PrintWriter out, int indent) {
    for (int i = 0; i < indent; i++) {
      out.write("    ");
    }
  }

  private static class Attribute {
    final String name;
    final String value;

    Attribute(@NotNull String name, @NotNull String value) {
      this.name = name;
      this.value = value;
    }
  }

  private static class Element {
    final String name;
    final List<Element> children = new ArrayList<>();
    final List<Attribute> attributes = new ArrayList<>();

    Element(@NotNull String name) {
      this.name = name;
    }

    Element attribute(@NotNull String name, @NotNull String value) {
      attributes.add(new Attribute(name, value));
      return this;
    }

    Element childAtFront(@NotNull Element child) {
      children.add(0, child);
      return this;
    }
  }
}
