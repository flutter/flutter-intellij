/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.assets;

import com.android.ide.common.vectordrawable.Svg2Vector;
import com.android.ide.common.vectordrawable.VdOverrideInfo;
import com.android.ide.common.vectordrawable.VdPreview;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.Validator.Severity;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.core.IntValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.utils.SdkUtils;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

/**
 * An asset which represents a vector graphics image. This can be loaded either from an SVG file,
 * a layered image supported by the pixelprobe library, or a Vector Drawable file.
 * <p>
 * After setting {@link #path()}, call one of the {@link #parse()} to attempt to read it and
 * generate a result.
 */
public final class VectorAsset extends BaseAsset {
  private static final String ERROR_EMPTY_PREVIEW = "Could not generate a preview";

  private final ObjectProperty<File> myPath = new ObjectValueProperty<>(new File(System.getProperty("user.home")));
  private final BoolProperty myAutoMirrored = new BoolValueProperty();
  private final IntProperty myOutputWidth = new IntValueProperty();
  private final IntProperty myOutputHeight = new IntValueProperty();

  public VectorAsset() {
  }

  @NotNull
  public ObjectProperty<File> path() {
    return myPath;
  }

  @NotNull
  public BoolProperty autoMirrored() {
    return myAutoMirrored;
  }

  /**
   * Since vector assets can be rendered at any size, set this width to a positive value if you
   * want to override the final output width.
   * <p>
   * Otherwise, the asset's default width (as parsed from the file) will be used.
   *
   * @see #outputHeight()
   */
  @NotNull
  public IntProperty outputWidth() {
    return myOutputWidth;
  }

  /**
   * Since vector assets can be rendered at any size, set this height to a positive value if you
   * want to override the final output height.
   *
   * Otherwise, the asset's default height (as parsed from the file) will be used.
   *
   * @see #outputWidth()
   */
  @NotNull
  public IntProperty outputHeight() {
    return myOutputHeight;
  }

  /**
   * Parses the file specified by the {@link #path()} property, overriding its final width which is
   * useful for previewing this vector asset in some UI component of the same width.
   *
   * @param previewWidth width of the display component
   * @param allowPropertyOverride true if this method can override some properties of the original file
   *                              (e.g. size ratio, opacity)
   */
  @NotNull
  public ParseResult parse(int previewWidth, boolean allowPropertyOverride) {
    File path = myPath.get();
    if (!path.exists()) {
      return new ParseResult("File " + path.getName() + " does not exist");
    }
    if (path.isDirectory()) {
      return new ParseResult(new Validator.Result(Severity.WARNING, "Please select a file"));
    }

    String xmlFileContent = null;
    FileType fileType = FileType.fromFile(path);

    StringBuilder errorBuffer = new StringBuilder();

    try {
      switch (fileType) {
        case SVG: {
          OutputStream outStream = new ByteArrayOutputStream();
          String errorLog = Svg2Vector.parseSvgToXml(path, outStream);
          errorBuffer.append(errorLog);
          xmlFileContent = outStream.toString();
          break;
        }

        case LAYERED_IMAGE:
          xmlFileContent = new LayeredImageConverter().toVectorDrawableXml(path);
          break;

        case VECTOR_DRAWABLE:
          xmlFileContent = Files.toString(path, StandardCharsets.UTF_8);
          break;
      }
    } catch (IOException e) {
      errorBuffer.append(e.getMessage());
    }

    BufferedImage image = null;
    float originalWidth = 0;
    float originalHeight = 0;
    if (!Strings.isNullOrEmpty(xmlFileContent)) {
      Document document = VdPreview.parseVdStringIntoDocument(xmlFileContent, errorBuffer.length() == 0 ? errorBuffer : null);
      if (document != null) {
        VdPreview.SourceSize originalSize = VdPreview.getVdOriginalSize(document);
        originalWidth = originalSize.getWidth();
        originalHeight = originalSize.getHeight();

        if (allowPropertyOverride) {
          String overriddenXml = overrideXmlFileContent(document, originalSize, errorBuffer);
          if (overriddenXml != null) {
            xmlFileContent = overriddenXml;
          }
        }

        if (previewWidth <= 0) {
          previewWidth = myOutputWidth.get() > 0 ? myOutputWidth.get() : Math.round(originalWidth);
        }

        VdPreview.TargetSize imageTargetSize = VdPreview.TargetSize.createFromMaxDimension(previewWidth);
        try {
          image = VdPreview.getPreviewFromVectorXml(imageTargetSize, xmlFileContent, errorBuffer);
        } catch (Throwable e) {
          Logger.getInstance(getClass()).error(e);
        }
      }
    }

    if (image == null) {
      if (errorBuffer.length() == 0) {
        errorBuffer.append(ERROR_EMPTY_PREVIEW);
      }
      return new ParseResult(errorBuffer.toString());
    }

    boolean valid = (originalWidth > 0 && originalHeight > 0);
    if (!valid && errorBuffer.length() == 0) {
      errorBuffer.append("The specified asset could not be parsed. Please choose another asset.");
    }
    Severity severity = !valid ? Severity.ERROR : errorBuffer.length() == 0 ? Severity.OK : Severity.WARNING;
    Validator.Result messages = new Validator.Result(severity, errorBuffer.toString());
    return new ParseResult(messages, image, originalWidth, originalHeight, xmlFileContent);
  }

  /**
   * Parses the file specified by the {@link #path()} property.
   */
  @NotNull
  public ParseResult parse() {
    return parse(0, true);
  }

  @Override
  @Nullable
  public ListenableFuture<BufferedImage> toImage() {
    return Futures.immediateFuture(parse().getImage());
  }

  /**
   * Modifies the source XML content with custom values set by the user, such as final output size
   * and opacity.
   */
  @Nullable
  private String overrideXmlFileContent(@NotNull Document document, @NotNull VdPreview.SourceSize originalSize,
                                        @NotNull StringBuilder errorBuffer) {
    float finalWidth = originalSize.getWidth();
    float finalHeight = originalSize.getHeight();

    int outputWidth = myOutputWidth.get();
    int outputHeight = myOutputHeight.get();
    if (outputWidth > 0) {
      finalWidth = outputWidth;
    }
    if (outputHeight > 0) {
      finalHeight = outputHeight;
    }

    finalWidth = Math.max(VdPreview.MIN_PREVIEW_IMAGE_SIZE, finalWidth);
    finalHeight = Math.max(VdPreview.MIN_PREVIEW_IMAGE_SIZE, finalHeight);

    VdOverrideInfo overrideInfo =
        new VdOverrideInfo(finalWidth, finalHeight, color().getValueOrNull(), opacityPercent().get() / 100.f, myAutoMirrored.get());
    return VdPreview.overrideXmlContent(document, overrideInfo, errorBuffer);
  }

  /**
   * A parse result returned after calling {@link #parse()}. Check {@link #isValid()} to see if
   * the parsing was successful.
   */
  public static final class ParseResult {
    @NotNull private final Validator.Result myValidityState;
    @Nullable private final BufferedImage myImage;
    private final float myOriginalWidth;
    private final float myOriginalHeight;
    private final boolean myIsValid;
    @NotNull private final String myXmlContent;

    public ParseResult(@NotNull String errorMessage) {
      this(Validator.Result.fromNullableMessage(errorMessage));
    }

    public ParseResult(@NotNull Validator.Result validityState) {
      this(validityState, null, 0, 0, "");
    }

    public ParseResult(@NotNull Validator.Result validityState, @Nullable BufferedImage image, float originalWidth, float originalHeight,
                       @NotNull String xmlContent) {
      myValidityState = validityState;
      myImage = image;
      myOriginalWidth = originalWidth;
      myOriginalHeight = originalHeight;
      myXmlContent = xmlContent;
      myIsValid = (originalWidth > 0 && originalHeight > 0);
    }

    /**
     * Returns true if the content of the target file was successfully converted to a vector drawable.
     * <p>
     * Note that a result can still be valid even with errors. See {@link #getValidityState()} for more
     * information.
     */
    public boolean isValid() {
      return myIsValid;
    }

    /**
     * The preferred width specified in the SVG file (although a vector drawable file can be rendered to any
     * width).
     */
    public float getOriginalWidth() {
      return myOriginalWidth;
    }

    /**
     * The preferred height specified in the SVG file (although a vector drawable file can be rendered to
     * any height).
     */
    public float getOriginalHeight() {
      return myOriginalHeight;
    }

    /**
     * Returns errors, warnings or informational messages produced during parsing.
     */
    @NotNull
    public Validator.Result getValidityState() {
      return myValidityState;
    }

    /**
     * An image preview of the final vector asset.
     */
    @Nullable
    public BufferedImage getImage() {
      return myImage;
    }

    /**
     * The XML that represents the final Android vector resource. It will be different from
     * the source file as some values may be overridden based on user values.
     */
    @NotNull
    public String getXmlContent() {
      return myXmlContent;
    }
  }

  /** The format of the file. */
  private enum FileType {
    SVG,
    LAYERED_IMAGE,
    VECTOR_DRAWABLE;

    @NotNull
    static FileType fromFile(@NotNull File file) {
      String path = file.getPath();
      if (SdkUtils.endsWithIgnoreCase(path,".svg")) {
        return SVG;
      }
      if (SdkUtils.endsWithIgnoreCase(path,".psd")) {
        return LAYERED_IMAGE;
      }
      return VECTOR_DRAWABLE;
    }
  }
}
