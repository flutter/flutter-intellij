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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.utils.XmlUtils.formatFloatAttribute;

import com.android.SdkConstants;
import com.android.tools.idea.res.ResourceHelper;
import com.android.utils.CharSequences;
import com.google.common.collect.ImmutableSet;
import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.LineSeparator;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Methods for manipulating vector drawables.
 */
public class VectorDrawableTransformer {
  private static final ImmutableSet<String> NAMES_OF_HANDLED_ATTRIBUTES =
      ImmutableSet.of("width", "height", "viewportWidth", "viewportHeight", "tint", "alpha");
  private static final String INDENT = "  ";
  private static final String DOUBLE_INDENT = INDENT + INDENT;

  /** Do not instantiate. All methods are static. */
  private VectorDrawableTransformer() {}

  /**
   * Transforms a vector drawable to fit in a rectangle with the {@code targetSize} dimensions and optionally
   * applies tint and opacity to it.
   * Conceptually, the scaling transformation includes of the following steps:
   * <ul>
   *   <li>The drawable is resized and centered in a rectangle of the target size</li>
   *   <li>If {@code clipRectangle} is not null, the drawable is clipped, resized and re-centered again</li>
   *   <li>The drawable is scaled according to {@code scaleFactor}</li>
   *   <li>The drawable is either padded or clipped to fit into the target rectangle</li>
   * </ul>
   *
   * @param originalDrawable the original drawable, preserved intact by the method
   * @param targetSize the size of the target rectangle
   * @param scaleFactor a scale factor to apply
   * @param clipRectangle an optional clip rectangle in coordinates expressed as fraction of the {@code targetSize}
   * @param tint an optional tint to apply to the drawable
   * @param opacity opacity to apply to the drawable
   * @return the transformed drawable; may be the same as the original if no transformation was
   *     required, or if the drawable is not a vector one
   */
  @NotNull
  public static String transform(@NotNull String originalDrawable, @NotNull Dimension targetSize, double scaleFactor,
                                 @Nullable Rectangle2D clipRectangle, @Nullable Color tint, double opacity) {
    KXmlParser parser = new KXmlParser();

    try {
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      parser.setInput(CharSequences.getReader(originalDrawable, true));
      int startLine = 1;
      int startColumn = 1;
      int token;
      while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT && token != XmlPullParser.START_TAG) {
        startLine = parser.getLineNumber();
        startColumn = parser.getColumnNumber();
      }
      // Skip to the first tag.
      if (parser.getEventType() != XmlPullParser.START_TAG || !"vector".equals(parser.getName()) || parser.getPrefix() != null) {
        return originalDrawable; // Not a vector drawable.
      }

      String originalTintValue = parser.getAttributeValue(ANDROID_URI, "tint");
      String tintValue = tint == null ? originalTintValue : ResourceHelper.colorToString(tint);

      String originalAlphaValue = parser.getAttributeValue(ANDROID_URI, "alpha");
      if (originalAlphaValue != null) {
        opacity *= parseDoubleValue(originalAlphaValue, "");
      }
      String alphaValue = formatFloatAttribute(opacity);
      if (alphaValue.equals("1")) {
        alphaValue = null; // No need to set the default opacity.
      }

      double targetWidth = targetSize.getWidth();
      double targetHeight = targetSize.getHeight();
      double width = targetWidth;
      double height = targetHeight;
      String widthValue = parser.getAttributeValue(ANDROID_URI, "width");
      if (widthValue != null) {
        String suffix = getSuffix(widthValue);
        width = getDoubleAttributeValue(parser, ANDROID_URI, "width", suffix);
        height = getDoubleAttributeValue(parser, ANDROID_URI, "height", suffix);

        //noinspection FloatingPointEquality -- safe in this context since all integer values are representable as double.
        if (suffix.equals("dp") && width == targetWidth && height == targetHeight && scaleFactor == 1 && clipRectangle == null &&
            Objects.equals(tintValue, originalTintValue) && Objects.equals(alphaValue, originalAlphaValue)) {
          return originalDrawable; // No transformation is needed.
        }
        if (Double.isNaN(width) || Double.isNaN(height)) {
          width = targetWidth;
          height = targetHeight;
        }
      }

      double originalViewportWidth = getDoubleAttributeValue(parser, ANDROID_URI, "viewportWidth", "");
      double originalViewportHeight = getDoubleAttributeValue(parser, ANDROID_URI, "viewportHeight", "");
      if (Double.isNaN(originalViewportWidth) || Double.isNaN(originalViewportHeight)) {
        originalViewportWidth = width;
        originalViewportHeight = height;
      }
      double viewportWidth = originalViewportWidth;
      double viewportHeight = originalViewportHeight;
      // Components of the translation vector in viewport coordinates.
      double x = 0;
      double y = 0;
      double ratio = targetWidth * height / (targetHeight * width);
      // Adjust viewport to compensate for the difference between the original and the target aspect ratio.
      if (ratio > 1) {
        viewportWidth *= ratio;
      }
      else if (ratio < 1) {
        viewportHeight /= ratio;
      }

      // Apply scaleFactor.
      viewportWidth /= scaleFactor;
      viewportHeight /= scaleFactor;

      if (clipRectangle != null) {
        // Adjust viewport.
        double s = Math.max(clipRectangle.getWidth(), clipRectangle.getHeight());
        viewportWidth *= s;
        viewportHeight *= s;
        // Re-center the image relative to the clip rectangle.
        x = (0.5 - clipRectangle.getCenterX()) * viewportWidth;
        y = (0.5 - clipRectangle.getCenterY()) * viewportHeight;
      }

      // Compensate for the shift of the viewport center due to scaling.
      x += (viewportWidth - originalViewportWidth) / 2;
      y += (viewportHeight - originalViewportHeight) / 2;

      StringBuilder result = new StringBuilder(originalDrawable.length() + originalDrawable.length() / 8);

      Indenter indenter = new Indenter(originalDrawable);
      // Copy contents before the first element.
      indenter.copy(1, 1, startLine, startColumn, "", result);
      String lineSeparator = detectLineSeparator(originalDrawable);
      // Output the "vector" element with the xmlns:android attribute.
      result.append(String.format("<vector %s:%s=\"%s\"", SdkConstants.XMLNS, SdkConstants.ANDROID_NS_NAME, ANDROID_URI));
      // Copy remaining namespace attributes.
      for (int i = 0; i < parser.getNamespaceCount(1); i++) {
        String prefix = parser.getNamespacePrefix(i);
        String uri = parser.getNamespaceUri(i);
        if (!SdkConstants.ANDROID_NS_NAME.equals(prefix) || !ANDROID_URI.equals(uri)) {
          result.append(String.format("%s%s%s:%s=\"%s\"", lineSeparator, DOUBLE_INDENT, SdkConstants.XMLNS, prefix, uri));
        }
      }

      result.append(String.format("%s%sandroid:width=\"%sdp\"", lineSeparator, DOUBLE_INDENT, formatFloatAttribute(targetWidth)));
      result.append(String.format("%s%sandroid:height=\"%sdp\"", lineSeparator, DOUBLE_INDENT, formatFloatAttribute(targetHeight)));
      result.append(String.format("%s%sandroid:viewportWidth=\"%s\"", lineSeparator, DOUBLE_INDENT, formatFloatAttribute(viewportWidth)));
      result.append(String.format("%s%sandroid:viewportHeight=\"%s\"", lineSeparator, DOUBLE_INDENT, formatFloatAttribute(viewportHeight)));
      if (tintValue != null) {
        result.append(String.format("%s%sandroid:tint=\"%s\"", lineSeparator, DOUBLE_INDENT, tintValue));
      }
      if (alphaValue != null) {
        result.append(String.format("%s%sandroid:alpha=\"%s\"", lineSeparator, DOUBLE_INDENT, alphaValue));
      }

      // Copy remaining attributes.
      for (int i = 0; i < parser.getAttributeCount(); i++) {
        String prefix = parser.getAttributePrefix(i);
        String name = parser.getAttributeName(i);
        if (!SdkConstants.ANDROID_NS_NAME.equals(prefix) || !NAMES_OF_HANDLED_ATTRIBUTES.contains(name)) {
          if (prefix != null) {
            name = prefix + ':' + name;
          }
          result.append(String.format("%s%s%s=\"%s\"", lineSeparator, DOUBLE_INDENT, name, parser.getAttributeValue(i)));
        }
      }
      result.append('>');

      String indent = "";
      String translateX = isSignificantlyDifferentFromZero(x / viewportWidth) ? formatFloatAttribute(x) : null;
      String translateY = isSignificantlyDifferentFromZero(y / viewportHeight) ? formatFloatAttribute(y) : null;
      if (translateX != null || translateY != null) {
        // Wrap the contents of the drawable into a translation group.
        result.append(lineSeparator).append(INDENT);
        result.append("<group");
        String delimiter = " ";
        if (translateX != null) {
          result.append(String.format("%sandroid:translateX=\"%s\"", delimiter, translateX));
          delimiter = lineSeparator + INDENT + DOUBLE_INDENT;
        }
        if (translateY != null) {
          result.append(String.format("%sandroid:translateY=\"%s\"", delimiter, translateY));
        }
        result.append('>');
        indent = INDENT;
      }

      // Copy the contents before the </vector> tag.
      startLine = parser.getLineNumber();
      startColumn = parser.getColumnNumber();
      while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT && token != XmlPullParser.END_TAG || parser.getDepth() > 1) {
        int endLineNumber = parser.getLineNumber();
        int endColumnNumber = parser.getColumnNumber();
        indenter.copy(startLine, startColumn, endLineNumber, endColumnNumber, token == XmlPullParser.CDSECT ? "" : indent, result);
        startLine = endLineNumber;
        startColumn = endColumnNumber;
      }
      if (startColumn != 1) {
        result.append(lineSeparator);
      }
      if (translateX != null || translateY != null) {
        result.append(INDENT).append(String.format("</group>%s", lineSeparator));
      }
      // Copy the closing </vector> tag and the remainder of the document.
      while (parser.nextToken() != XmlPullParser.END_DOCUMENT) {
        int endLineNumber = parser.getLineNumber();
        int endColumnNumber = parser.getColumnNumber();
        indenter.copy(startLine, startColumn, endLineNumber, endColumnNumber, "", result);
        startLine = endLineNumber;
        startColumn = endColumnNumber;
      }

      return result.toString();
    }
    catch (XmlPullParserException | IOException e) {
      return originalDrawable;  // Ignore and return the original drawable.
    }
  }

  private static String detectLineSeparator(CharSequence str) {
    LineSeparator separator = StringUtil.detectSeparators(str);
    if (separator != null) {
      return separator.getSeparatorString();
    }
    return CodeStyle.getDefaultSettings().getLineSeparator();
  }

  @SuppressWarnings("SameParameterValue")
  private static double getDoubleAttributeValue(@NotNull KXmlParser parser, @NotNull String namespaceUri, @NotNull String attributeName,
                                                @NotNull String expectedSuffix) {
    String value = parser.getAttributeValue(namespaceUri, attributeName);
    return parseDoubleValue(value, expectedSuffix);
  }

  private static double parseDoubleValue(String value, @NotNull String expectedSuffix) {
    if (value == null || !value.endsWith(expectedSuffix)) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(value.substring(0, value.length() - expectedSuffix.length()));
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  @NotNull
  private static String getSuffix(@NotNull String value) {
    int i = value.length();
    while (--i >= 0) {
      if (Character.isDigit(value.charAt(i))) {
        break;
      }
    }
    ++i;
    return value.substring(i);
  }

  private static boolean isSignificantlyDifferentFromZero(double value) {
    return Math.abs(value) >= 1.e-6;
  }

  private static class Indenter {
    private int myLine;
    private int myColumn;
    private int myOffset;
    private final CharSequence myText;

    Indenter(CharSequence text) {
      myText = text;
      myLine = 1;
      myColumn = 1;
    }

    void copy(int fromLine, int fromColumn, int toLine, int toColumn, String indent, StringBuilder out) {
      if (myLine != fromLine) {
        if (myLine > fromLine) {
          myLine = 1;
          myColumn = 1;
          myOffset = 0;
        }
        while (myLine < fromLine) {
          char c = myText.charAt(myOffset);
          if (c == '\n') {
            myLine++;
            myColumn = 1;
          } else {
            if (myLine != 1 || myColumn != 1 || c != '\uFEFF') {  // Byte order mark doesn't occupy a column.
              myColumn++;
            }
          }
          myOffset++;
        }
      }
      myOffset += fromColumn - myColumn;
      myColumn = fromColumn;
      while (myLine < toLine || myLine == toLine && myColumn < toColumn) {
        char c = myText.charAt(myOffset);
        if (c == '\n') {
          myLine++;
          myColumn = 1;
        } else {
          if (myLine != 1 || myColumn != 1 || c != '\uFEFF') {  // Byte order mark doesn't occupy a column.
            if (myColumn == 1) {
              out.append(indent);
            }
            myColumn++;
          }
        }
        myOffset++;
        out.append(c);
      }
    }
  }
}
