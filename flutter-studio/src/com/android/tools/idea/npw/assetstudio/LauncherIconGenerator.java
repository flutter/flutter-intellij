/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio;

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.roundToInt;
import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.scaleRectangle;
import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.scaleRectangleAroundCenter;

import com.android.ide.common.util.AssetUtil;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.tools.idea.concurrent.FutureUtils;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ExceptionUtilRt;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generator of Android launcher icons.
 *
 * Defaults from https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here.
public class LauncherIconGenerator extends IconGenerator {
  public static final Color DEFAULT_FOREGROUND_COLOR = Color.BLACK;
  public static final Color DEFAULT_BACKGROUND_COLOR = new Color(0x26A69A);
  public static final Rectangle IMAGE_SIZE_FULL_BLEED_DP = new Rectangle(0, 0, 108, 108);
  public static final Dimension SIZE_FULL_BLEED_DP = IMAGE_SIZE_FULL_BLEED_DP.getSize();
  private static final Rectangle IMAGE_SIZE_SAFE_ZONE_DP = new Rectangle(0, 0, 66, 66);
  private static final Rectangle IMAGE_SIZE_VIEWPORT_DP = new Rectangle(0, 0, 72, 72);
  private static final Rectangle IMAGE_SIZE_LEGACY_DP = new Rectangle(0, 0, 48, 48);
  private static final Rectangle IMAGE_SIZE_VIEW_PORT_WEB_PX = new Rectangle(0, 0, 512, 512);
  private static final Rectangle IMAGE_SIZE_FULL_BLEED_WEB_PX = new Rectangle(0, 0, 768, 768);

  private final ObjectProperty<Color> myBackgroundColor = new ObjectValueProperty<>(DEFAULT_BACKGROUND_COLOR);
  private final BoolProperty myGenerateLegacyIcon = new BoolValueProperty(true);
  private final BoolProperty myGenerateRoundIcon = new BoolValueProperty(true);
  private final BoolProperty myGenerateWebIcon = new BoolValueProperty(true);
  private final ObjectProperty<Shape> myLegacyIconShape = new ObjectValueProperty<>(Shape.SQUARE);
  private final ObjectProperty<Shape> myWebIconShape = new ObjectValueProperty<>(Shape.SQUARE);
  private final BoolProperty myShowGrid = new BoolValueProperty();
  private final BoolProperty myShowSafeZone = new BoolValueProperty(true);
  private final ObjectValueProperty<Density> myPreviewDensity = new ObjectValueProperty<>(Density.XHIGH);
  private final OptionalProperty<ImageAsset> myBackgroundImageAsset = new OptionalValueProperty<>();
  private final StringProperty myForegroundLayerName = new StringValueProperty();
  private final StringProperty myBackgroundLayerName = new StringValueProperty();

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public LauncherIconGenerator(@NotNull Project project, int minSdkVersion, @Nullable DrawableRenderer renderer) {
    super(project, minSdkVersion, new GraphicGeneratorContext(40, renderer));
  }

  /**
   * A color for rendering the background shape.
   */
  @NotNull
  public ObjectProperty<Color> backgroundColor() {
    return myBackgroundColor;
  }

  /**
   * If {@code true}, generate the "Legacy" icon (API 24 and earlier)
   */
  @NotNull
  public BoolProperty generateLegacyIcon() {
    return myGenerateLegacyIcon;
  }

  /**
   * If {@code true}, generate the "Round" icon (API 25)
   */
  @NotNull
  public BoolProperty generateRoundIcon() {
    return myGenerateRoundIcon;
  }

  /**
   * If {@code true}, generate the "Web" icon for PlayStore
   */
  @NotNull
  public BoolProperty generateWebIcon() {
    return myGenerateWebIcon;
  }

  /**
   * A shape which will be used as the "Legacy" icon's backdrop.
   */
  @NotNull
  public ObjectProperty<Shape> legacyIconShape() {
    return myLegacyIconShape;
  }

  /**
   * A shape which will be used as the "Web" icon's backdrop.
   */
  @NotNull
  public ObjectProperty<Shape> webIconShape() {
    return myWebIconShape;
  }

  @NotNull
  public OptionalProperty<ImageAsset> backgroundImageAsset() {
    return myBackgroundImageAsset;
  }

  @NotNull
  public BoolProperty showGrid() {
    return myShowGrid;
  }

  @NotNull
  public BoolProperty showSafeZone() {
    return myShowSafeZone;
  }

  @NotNull
  public ObjectValueProperty<Density> previewDensity() {
    return myPreviewDensity;
  }

  @NotNull
  public StringProperty foregroundLayerName() {
    return myForegroundLayerName;
  }

  @NotNull
  public StringProperty backgroundLayerName() {
    return myBackgroundLayerName;
  }

  @Override
  @NotNull
  public LauncherIconOptions createOptions(boolean forPreview) {
    LauncherIconOptions options = new LauncherIconOptions(forPreview);
    // Set foreground image.
    BaseAsset foregroundAsset = sourceAsset().getValueOrNull();
    if (foregroundAsset != null) {
      options.useForegroundColor = foregroundAsset.isColorable();
      Color color = foregroundAsset.isColorable() ? foregroundAsset.color().getValueOrNull() : null;
      if (color != null) {
        options.foregroundColor = color.getRGB();
      }
      double scaleFactor = foregroundAsset.scalingPercent().get() / 100.;
      if (foregroundAsset instanceof ImageAsset && ((ImageAsset)foregroundAsset).isClipart()) {
        scaleFactor *= 0.58;  // Scale correction for clip art to more or less fit into the safe zone.
      }
      else if (foregroundAsset instanceof TextAsset) {
        scaleFactor *= 0.46;  // Scale correction for text to more or less fit into the safe zone.
      }
      else if (foregroundAsset.trimmed().get()) {
        // Scale correction for images to fit into the safe zone.
        // Finding the smallest circle containing the image is not trivial (see https://en.wikipedia.org/wiki/Smallest-circle_problem).
        // For simplicity we treat the safe zone as a square.
        scaleFactor *= IMAGE_SIZE_SAFE_ZONE_DP.getWidth() / SIZE_FULL_BLEED_DP.getWidth();
      }
      options.foregroundImage =
          new TransformedImageAsset(foregroundAsset, SIZE_FULL_BLEED_DP, scaleFactor, color, getGraphicGeneratorContext());
    }
    // Set background image.
    ImageAsset backgroundAsset = myBackgroundImageAsset.getValueOrNull();
    if (backgroundAsset != null) {
      double scaleFactor = backgroundAsset.scalingPercent().get() / 100.;
      options.backgroundImage =
          new TransformedImageAsset(backgroundAsset, SIZE_FULL_BLEED_DP, scaleFactor, null, getGraphicGeneratorContext());
    }

    options.backgroundColor = myBackgroundColor.get().getRGB();
    options.showGrid = myShowGrid.get();
    options.showSafeZone = myShowSafeZone.get();
    options.previewDensity = myPreviewDensity.get();
    options.foregroundLayerName = myForegroundLayerName.get();
    options.backgroundLayerName = myBackgroundLayerName.get();
    options.generateLegacyIcon = myGenerateLegacyIcon.get();
    options.legacyIconShape = myLegacyIconShape.get();
    options.webIconShape = myWebIconShape.get();
    options.generateRoundIcon = myGenerateRoundIcon.get();
    options.generateWebIcon = myGenerateWebIcon.get();
    return options;
  }

  @Override
  @NotNull
  protected List<Callable<GeneratedIcon>> createIconGenerationTasks(@NotNull GraphicGeneratorContext context,
                                                                    @NotNull Options options,
                                                                    @NotNull String name) {
    LauncherIconOptions launcherIconOptions = (LauncherIconOptions)options;

    List<Callable<GeneratedIcon>> tasks = new ArrayList<>();

    // Generate tasks for icons (background, foreground, legacy) in all densities.
    createOutputIconsTasks(context, name, launcherIconOptions, tasks);

    // Generate tasks for drawable xml resource.
    createXmlDrawableResourcesTasks(name, launcherIconOptions, tasks);

    // Generate tasks for preview images.
    createPreviewImagesTasks(context, launcherIconOptions, tasks);
    return tasks;
  }

  private void createOutputIconsTasks(@NotNull GraphicGeneratorContext context, @NotNull String name, @NotNull LauncherIconOptions options,
                                      @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generateOutputIcons) {
      return;
    }

    for (Density density : DENSITIES) {
      LauncherIconOptions localOptions = options.clone();
      localOptions.density = density;
      localOptions.showGrid = false;
      localOptions.showSafeZone = false;

      createOutputIconsForSingleDensityTasks(context, name, localOptions, density, tasks);
    }

    if (options.generateWebIcon) {
      tasks.add(() -> {
        LauncherIconOptions localOptions = options.clone();
        localOptions.showGrid = false;
        localOptions.showSafeZone = false;
        localOptions.generateWebIcon = true;
        localOptions.generateOutputIcons = true;
        localOptions.generatePreviewIcons = false;
        localOptions.legacyIconShape = localOptions.webIconShape;
        BufferedImage image = generateLegacyImage(context, localOptions);
        return new GeneratedImageIcon(name,
                                      new PathString(getIconPath(localOptions, name)),
                                      IconCategory.WEB,
                                      Density.NODPI,
                                      image);
      });
    }
  }

  private void createOutputIconsForSingleDensityTasks(@NotNull GraphicGeneratorContext context, @NotNull String name,
                                                      @NotNull LauncherIconOptions options, @NotNull Density density,
                                                      @NotNull List<Callable<GeneratedIcon>> tasks) {
    // Generate foreground mipmap only if the foreground is a raster image.
    if (options.foregroundImage != null && options.foregroundImage.isRasterImage()) {
      tasks.add(() -> {
        LauncherIconOptions foregroundOptions = options.clone();
        foregroundOptions.generateWebIcon = false;
        foregroundOptions.generatePreviewIcons = false;
        foregroundOptions.generateOutputIcons = true;
        BufferedImage foregroundImage = generateIconForegroundLayer(context, foregroundOptions);
        return new GeneratedImageIcon(foregroundOptions.foregroundLayerName,
                                      new PathString(getIconPath(foregroundOptions, options.foregroundLayerName)),
                                      IconCategory.ADAPTIVE_FOREGROUND_LAYER,
                                      density,
                                      foregroundImage);
      });
    }

    // Generate background mipmap only if the background is a raster image.
    if (options.backgroundImage != null && options.backgroundImage.isRasterImage()) {
      tasks.add(() -> {
        LauncherIconOptions backgroundOptions = options.clone();
        backgroundOptions.generateWebIcon = false;
        backgroundOptions.generatePreviewIcons = false;
        backgroundOptions.generateOutputIcons = true;
        BufferedImage backgroundImage = generateIconBackgroundLayer(context, backgroundOptions);
        return new GeneratedImageIcon(backgroundOptions.backgroundLayerName,
                                      new PathString(getIconPath(backgroundOptions, options.backgroundLayerName)),
                                      IconCategory.ADAPTIVE_BACKGROUND_LAYER,
                                      density,
                                      backgroundImage);
      });
    }

    if (options.generateLegacyIcon) {
      tasks.add(() -> {
        LauncherIconOptions legacyOptions = options.clone();
        legacyOptions.previewShape = PreviewShape.LEGACY;
        legacyOptions.generateWebIcon = false;
        legacyOptions.generatePreviewIcons = false;
        legacyOptions.generateOutputIcons = true;
        BufferedImage legacy = generateLegacyImage(context, legacyOptions);
        return new GeneratedImageIcon(name,
                                      new PathString(getIconPath(legacyOptions, name)),
                                      IconCategory.LEGACY,
                                      density,
                                      legacy);
      });
    }

    if (options.generateRoundIcon) {
      tasks.add(() -> {
        LauncherIconOptions legacyOptions = options.clone();
        legacyOptions.previewShape = PreviewShape.LEGACY_ROUND;
        legacyOptions.generateWebIcon = false;
        legacyOptions.generatePreviewIcons = false;
        legacyOptions.generateOutputIcons = true;
        legacyOptions.legacyIconShape = Shape.CIRCLE;
        BufferedImage legacyRound = generateLegacyImage(context, legacyOptions);
        return new GeneratedImageIcon(name + "_round",
                                      new PathString(getIconPath(legacyOptions, name + "_round")),
                                      IconCategory.ROUND_API_25,
                                      density,
                                      legacyRound);
      });
    }
  }

  private void createXmlDrawableResourcesTasks(@NotNull String name, @NotNull LauncherIconOptions options,
                                               @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generateOutputIcons) {
      return;
    }
    {
      LauncherIconOptions iconOptions = options.clone();
      iconOptions.density = Density.ANYDPI;
      iconOptions.generateWebIcon = false;
      iconOptions.iconFolderKind = IconFolderKind.MIPMAP;
      iconOptions.apiVersion = 26; // Temporary until http://b/62316340 is fixed.

      tasks.add(() -> {
        String xmlAdaptiveIcon = getAdaptiveIconXml(iconOptions);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, name)),
                                        IconCategory.XML_RESOURCE,
                                        xmlAdaptiveIcon);
      });

      tasks.add(() -> {
        String xmlAdaptiveIcon = getAdaptiveIconXml(iconOptions);
        return new GeneratedXmlResource(name + "_round",
                                        new PathString(getIconPath(iconOptions, name + "_round")),
                                        IconCategory.XML_RESOURCE,
                                        xmlAdaptiveIcon);
      });
    }

    if (options.foregroundImage != null && options.foregroundImage.isDrawable()) {
      // Generate foreground drawable.
      TransformedImageAsset image = options.foregroundImage;
      tasks.add(() -> {
        LauncherIconOptions iconOptions = options.clone();
        iconOptions.generateWebIcon = false;
        iconOptions.density = Density.ANYDPI;
        iconOptions.iconFolderKind = IconFolderKind.DRAWABLE_NO_DPI;

        if (!image.isDrawable()) {
          getLog().error("Background image is not drawable!", new Throwable());
        }
        String xmlDrawableText = image.getTransformedDrawable();
        if (xmlDrawableText == null) {
          getLog().error("Transformed foreground drawable is null" + (image.isDrawable() ? " but the image is drawable" : ""),
                         new Throwable());
          xmlDrawableText = "<vector/>"; // Use an empty image. It will be recomputed again soon.
        }
        iconOptions.apiVersion = calculateMinRequiredApiLevel(xmlDrawableText, myMinSdkVersion);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, iconOptions.foregroundLayerName)),
                                        IconCategory.ADAPTIVE_FOREGROUND_LAYER,
                                        xmlDrawableText);
      });
    }

    if (options.backgroundImage != null && options.backgroundImage.isDrawable()) {
      // Generate background drawable.
      TransformedImageAsset image = options.backgroundImage;
      tasks.add(() -> {
        LauncherIconOptions iconOptions = options.clone();
        iconOptions.generateWebIcon = false;
        iconOptions.density = Density.ANYDPI;
        iconOptions.iconFolderKind = IconFolderKind.DRAWABLE_NO_DPI;

        if (!image.isDrawable()) {
          getLog().error("Background image is not drawable!", new Throwable());
        }
        String xmlDrawableText = image.getTransformedDrawable();
        if (xmlDrawableText == null) {
          getLog().error("Transformed background drawable is null" + (image.isDrawable() ? " but the image is drawable" : ""),
                         new Throwable());
          xmlDrawableText = "<vector/>"; // Use an empty image. It will be recomputed again soon.
        }
        iconOptions.apiVersion = calculateMinRequiredApiLevel(xmlDrawableText, myMinSdkVersion);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, iconOptions.backgroundLayerName)),
                                        IconCategory.ADAPTIVE_BACKGROUND_LAYER,
                                        xmlDrawableText);
      });
    } else if (options.backgroundImage == null) {
      // Generate background color value.
      tasks.add(() -> {
        LauncherIconOptions iconOptions = options.clone();
        iconOptions.generateWebIcon = false;
        iconOptions.density = Density.ANYDPI;
        iconOptions.iconFolderKind = IconFolderKind.VALUES;

        String format = ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>%1$s"
            + "<resources>%1$s"
            + "    <color name=\"%2$s\">#%3$06X</color>%1$s"
            + "</resources>";
        String xmlColor = String.format(format, myLineSeparator, iconOptions.backgroundLayerName, iconOptions.backgroundColor & 0xFFFFFF);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, iconOptions.backgroundLayerName)),
                                        IconCategory.XML_RESOURCE,
                                        xmlColor);
      });
    }
  }

  @NotNull
  private String getAdaptiveIconXml(@NotNull LauncherIconOptions options) {
    String backgroundType = options.backgroundImage == null ? "color" : options.backgroundImage.isDrawable() ? "drawable" : "mipmap";
    String foregroundType = options.foregroundImage != null && options.foregroundImage.isDrawable() ? "drawable" : "mipmap";
    String format = ""
        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>%1$s"
        + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">%1$s"
        + "    <background android:drawable=\"@%2$s/%3$s\"/>%1$s"
        + "    <foreground android:drawable=\"@%4$s/%5$s\"/>%1$s"
        + "</adaptive-icon>";
    return String.format(format, myLineSeparator, backgroundType, options.backgroundLayerName, foregroundType, options.foregroundLayerName);
  }

  private static void createPreviewImagesTasks(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options,
                                               @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generatePreviewIcons) {
      return;
    }

    List<PreviewShape> previewShapes = new ArrayList<>();
    previewShapes.add(PreviewShape.FULL_BLEED);
    previewShapes.add(PreviewShape.SQUIRCLE);
    previewShapes.add(PreviewShape.CIRCLE);
    previewShapes.add(PreviewShape.SQUARE);
    previewShapes.add(PreviewShape.ROUNDED_SQUARE);
    if (options.generateLegacyIcon) {
      previewShapes.add(PreviewShape.LEGACY);
    }
    if (options.generateRoundIcon) {
      previewShapes.add(PreviewShape.LEGACY_ROUND);
    }
    if (options.generateWebIcon) {
      previewShapes.add(PreviewShape.WEB);
    }

    for (PreviewShape previewShape : previewShapes) {
      tasks.add(() -> {
        LauncherIconOptions localOptions = options.clone();
        localOptions.density = options.previewDensity;
        localOptions.previewShape = previewShape;
        localOptions.generateLegacyIcon = (previewShape == PreviewShape.LEGACY);
        localOptions.generateRoundIcon = (previewShape == PreviewShape.LEGACY_ROUND);
        localOptions.generateWebIcon = (previewShape == PreviewShape.WEB);

        BufferedImage image;
        String errorMessage = null;
        try {
          image = generatePreviewImage(context, localOptions);
        } catch (Throwable e) {
          errorMessage = e.getMessage();
          Rectangle imageRect = getFullBleedRectangle(localOptions);
          image = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        }
        GeneratedImageIcon icon = new GeneratedImageIcon(previewShape.id,
                                                         null, // No path for preview icons.
                                                         IconCategory.PREVIEW,
                                                         localOptions.density,
                                                         image);
        if (errorMessage != null) {
          icon.setErrorMessage(errorMessage);
        }
        return icon;
      });
    }
  }

  @Override
  public void generateRasterImage(@Nullable String category, @NotNull Map<String, Map<String, BufferedImage>> categoryMap,
                                  @NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    LauncherIconOptions launcherIconOptions = (LauncherIconOptions) options;
    LauncherIconOptions localOptions = launcherIconOptions.clone();
    localOptions.generateWebIcon = false;

    Collection<GeneratedIcon> icons = generateIcons(context, options, name);
    icons.stream()
        .filter(x -> x instanceof GeneratedImageIcon)
        .map(x -> (GeneratedImageIcon) x)
        .filter(x -> x.getOutputPath() != null)
        .forEach(
            x -> {
              assert x.getOutputPath() != null;

              Map<String, BufferedImage> imageMap = categoryMap.computeIfAbsent(x.getCategory().toString(), k -> new LinkedHashMap<>());

              // Store image in a map, where the key is the relative path to the image.
              LauncherIconOptions iconOptions = localOptions.clone();
              iconOptions.density = x.getDensity();
              iconOptions.iconFolderKind = IconFolderKind.MIPMAP;
              iconOptions.generateWebIcon = (x.getCategory() == IconCategory.WEB);
              imageMap.put(x.getOutputPath().toString(), x.getImage());
            });
  }

  @Override
  @NotNull
  public BufferedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull Options options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    return generatePreviewImage(context, (LauncherIconOptions)options);
  }

  @NotNull
  private static BufferedImage generatePreviewImage(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options) {
    switch (options.previewShape) {
      case CIRCLE:
      case SQUIRCLE:
      case ROUNDED_SQUARE:
      case SQUARE:
        return generateViewportPreviewImage(context, options);

      case LEGACY:
        options.generatePreviewIcons = true;
        options.generateWebIcon = false;
        return generateLegacyImage(context, options);

      case LEGACY_ROUND:
        options.generatePreviewIcons = true;
        options.generateWebIcon = false;
        options.legacyIconShape = Shape.CIRCLE;
        return generateLegacyImage(context, options);

      case FULL_BLEED: {
        BufferedImage image = generateFullBleedPreviewImage(context, options);
        // For preview, scale image down so that it does not display relatively
        // too big compared to the other preview icons.
        return scaledPreviewImage(image, 0.8f);
      }

      case WEB: {
        options.generatePreviewIcons = true;
        options.generateWebIcon = true;
        options.legacyIconShape = options.webIconShape;
        BufferedImage image = generateLegacyImage(context, options);
        image = AssetUtil.trimmedImage(image);
        // For preview, scale image down so that it does not display relatively
        // too big compared to the other preview icons.
        double scale = getMdpiScaleFactor(options.previewDensity);
        return scaledPreviewImage(image, 0.25 * scale);
      }

      case NONE:
      default:
        throw new IllegalArgumentException();
    }
  }

  @SuppressWarnings("UseJBColor")
  @NotNull
  private static BufferedImage generateFullBleedPreviewImage(@NotNull GraphicGeneratorContext context,
                                                             @NotNull LauncherIconOptions options) {
    Layers layers = generateIconLayers(context, options);
    BufferedImage result = mergeLayers(layers, Color.BLACK);
    drawGrid(options, result);
    return result;
  }

  /**
   * Generates a {@link BufferedImage} for either a "Legacy", "Round" or "Web" icon. The created
   * image consists of both background and foreground layer images merge together, then a shape
   * (e.g. circle, square) mask is applied, and finally the image is scaled to the appropriate
   * size (48x48 legacy or 512x512px web).
   */
  @NotNull
  private static BufferedImage generateLegacyImage(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options) {
    // The viewport rectangle (72x72dp) scaled according to density.
    Rectangle viewportRect = getViewportRectangle(options);

    // The "Legacy" icon rectangle (48x48dp) scaled according to density.
    Rectangle legacyRect = getLegacyRectangle(options);

    // The "Web" density does not exist in the "Density" enum. Various "Legacy" icon APIs use
    // "null" as a placeholder for "Web".
    Density legacyOrWebDensity = options.generateWebIcon ? Density.NODPI : options.density;

    // The sub-rectangle of the 48x48dp "Legacy" icon that corresponds to the "Legacy" icon
    // shape, scaled according to the density.
    Rectangle legacyShapeRect = LauncherLegacyIconGenerator.getTargetRect(options.legacyIconShape, legacyOrWebDensity);

    // Generate full bleed and viewport images.
    Layers layers = generateIconLayers(context, options);
    BufferedImage fullBleed = mergeLayers(layers);

    // Scale the "Full Bleed" icon so that it is contained in the "Legacy" shape rectangle.
    //
    // Note that even though we scale the "Full Bleed" image, we use the ratio of the
    // Viewport rectangle (72x72dp) to Legacy shape (sub-rectangle of 48x48dp) as the
    // scaling factor, because the Viewport rectangle is the visible part of Adaptive icons,
    // whereas the "Full Bleed" icon is never entirely visible.
    float viewportScale = getRectangleInsideScale(viewportRect, legacyShapeRect);
    BufferedImage scaledFullBleed =
        options.generatePreviewIcons ? scaledPreviewImage(fullBleed, viewportScale) : scaledImage(fullBleed, viewportScale);

    // Load shadow and mask corresponding to legacy shape.
    BufferedImage shapeImageBack = null;
    BufferedImage shapeImageFore = null;
    BufferedImage shapeImageMask = null;
    if (options.legacyIconShape != Shape.NONE) {
      shapeImageBack = LauncherLegacyIconGenerator.loadBackImage(context, options.legacyIconShape, legacyOrWebDensity);
      shapeImageFore = LauncherLegacyIconGenerator.loadStyleImage(context, options.legacyIconShape, legacyOrWebDensity, Style.SIMPLE);
      shapeImageMask = LauncherLegacyIconGenerator.loadMaskImage(context, options.legacyIconShape, legacyOrWebDensity);
    }

    // Generate legacy image by merging shadow, mask and (scaled) adaptive icon
    BufferedImage legacyImage = AssetUtil.newArgbBufferedImage(legacyRect.width, legacyRect.height);
    Graphics2D gLegacy = (Graphics2D)legacyImage.getGraphics();

    // Start with backdrop image (semi-transparent shadow).
    if (shapeImageBack != null) {
      AssetUtil.drawCentered(gLegacy, shapeImageBack, legacyRect);
    }

    // Apply the mask to the scaled adaptive icon.
    if (shapeImageMask != null) {
      scaledFullBleed = applyMask(scaledFullBleed, shapeImageMask);
    }

    // Draw the scaled adaptive icon on top of shadow effect.
    AssetUtil.drawCentered(gLegacy, scaledFullBleed, legacyRect);

    // Finish with the foreground effect (shadow outline).
    if (shapeImageFore != null) {
      gLegacy.drawImage(shapeImageFore, 0, 0, null);
    }
    gLegacy.dispose();
    return legacyImage;
  }

  /**
   * Returns the scaling factor to apply to the {@code source} rectangle so that its width or
   * height is equal to the width or height of {@code destination} rectangle, while remaining
   * contained within {@code destination}.
   */
  public static float getRectangleInsideScale(@NotNull Rectangle source, @NotNull Rectangle destination) {
    float scaleWidth = (float) destination.width / (float) source.width;
    float scaleHeight = (float) destination.height / (float) source.height;
    return Math.min(scaleWidth, scaleHeight);
  }

  /** Scale an image given a scale factor. */
  @NotNull
  private static BufferedImage scaledImage(@NotNull BufferedImage image, float scale) {
    int width = Math.round(image.getWidth() * scale);
    int height = Math.round(image.getHeight() * scale);
    return AssetUtil.scaledImage(image, width, height);
  }

  /**
   * For performance reason, we use a lower quality (but faster) image scaling algorithm when
   * generating preview images.
   */
  @NotNull
  private static BufferedImage scaledPreviewImage(@NotNull BufferedImage image, double scale) {
    int width = (int)Math.round(image.getWidth() * scale);
    int height = (int)Math.round(image.getHeight() * scale);
    return scaledPreviewImage(image, width, height);
  }

  /**
   * For performance reason, we use a lower qualitty (but faster) image scaling algorithm when
   * generating preview images.
   */
  @NotNull
  private static BufferedImage scaledPreviewImage(@NotNull BufferedImage source, int width, int height) {
    // Common case optimization: scaling to the same (width, height) is a no-op.
    if (source.getWidth() == width && source.getHeight() == height) {
      return source;
    }

    BufferedImage scaledBufImage = AssetUtil.newArgbBufferedImage(width, height);
    Graphics2D g = scaledBufImage.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(source, 0, 0, width, height, null);
    g.dispose();
    return scaledBufImage;
  }

  /** Generate a preview image with a Shape mask applied (e.g. Square, Squircle). */
  @NotNull
  private static BufferedImage generateViewportPreviewImage(@NotNull GraphicGeneratorContext context,
                                                            @NotNull LauncherIconOptions options) {
    Layers layers = generateIconLayers(context, options);
    BufferedImage result = mergeLayers(layers);
    BufferedImage mask = generateMaskLayer(context, options, options.previewShape);
    result = cropImageToViewport(options, result);
    result = applyMask(result, mask);
    drawGrid(options, result);

    return result;
  }

  private static BufferedImage cropImageToViewport(@NotNull LauncherIconOptions options, @NotNull BufferedImage image) {
    return cropImage(image, getViewportRectangle(options));
  }

  private static BufferedImage cropImage(@NotNull BufferedImage image, @NotNull Rectangle targetRect) {
    Rectangle imageRect = new Rectangle(0, 0, image.getWidth(), image.getHeight());

    BufferedImage subImage = image.getSubimage((imageRect.width - targetRect.width) / 2, (imageRect.height - targetRect.height) / 2,
                                               targetRect.width, targetRect.height);

    BufferedImage viewportImage = AssetUtil.newArgbBufferedImage(targetRect.width, targetRect.height);

    Graphics2D gViewport = (Graphics2D) viewportImage.getGraphics();
    gViewport.drawImage(subImage, 0, 0, null);
    gViewport.dispose();

    return viewportImage;
  }

  @NotNull
  private static BufferedImage mergeLayers(@NotNull Layers layers) {
    return mergeLayers(layers, null);
  }

  @NotNull
  private static BufferedImage mergeLayers(@NotNull Layers layers, @Nullable Color fillColor) {
    int width = Math.max(layers.background.getWidth(), layers.foreground.getWidth());
    int height = Math.max(layers.background.getHeight(), layers.foreground.getHeight());

    BufferedImage outImage = AssetUtil.newArgbBufferedImage(width, height);
    Graphics2D gOut = (Graphics2D) outImage.getGraphics();
    if (fillColor != null) {
      gOut.setPaint(fillColor);
      gOut.fillRect(0, 0, width, height);
    }
    gOut.drawImage(layers.background, 0, 0, null);
    gOut.drawImage(layers.foreground, 0, 0, null);
    gOut.dispose();

    return outImage;
  }

  @NotNull
  private static Layers generateIconLayers(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options) {
    BufferedImage backgroundImage = generateIconBackgroundLayer(context, options);
    BufferedImage foregroundImage = generateIconForegroundLayer(context, options);

    return new Layers(backgroundImage, foregroundImage);
  }

  @Nullable
  private static BufferedImage generateMaskLayer(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options,
                                                 @NotNull PreviewShape shape) {
    String maskName;
    switch (shape) {
      case CIRCLE:
        maskName = "circle";
        break;
      case SQUARE:
        maskName = "square";
        break;
      case ROUNDED_SQUARE:
        maskName = "rounded_corner";
        break;
      case SQUIRCLE:
        //noinspection SpellCheckingInspection
        maskName = "squircle";
        break;
      default:
        maskName = null;
    }
    if (maskName == null) {
      return null;
    }

    if (options.generateWebIcon) {
      String resourceName = String.format("/images/adaptive_icons_masks/adaptive_%s-%s.png", maskName, Density.XXXHIGH.getResourceValue());

      BufferedImage mask = context.loadImageResource(resourceName);
      if (mask == null) {
        return null;
      }
      Rectangle maskRect = new Rectangle(0, 0, mask.getWidth(), mask.getHeight());
      float scale = getRectangleInsideScale(maskRect, getViewportRectangle(options));
      return options.generatePreviewIcons ? scaledPreviewImage(mask, scale) : scaledImage(mask, scale);
    } else {
      String resourceName = String.format("/images/adaptive_icons_masks/adaptive_%s-%s.png", maskName, options.density.getResourceValue());

      return context.loadImageResource(resourceName);
    }
  }

  @NotNull
  private static Rectangle getFullBleedRectangle(@NotNull LauncherIconOptions options) {
    if (options.generateWebIcon) {
      return IMAGE_SIZE_FULL_BLEED_WEB_PX;
    }
    return scaleRectangle(IMAGE_SIZE_FULL_BLEED_DP, getMdpiScaleFactor(options.density));
  }

  @NotNull
  private static Rectangle getViewportRectangle(@NotNull LauncherIconOptions options) {
    if (options.generateWebIcon) {
      return IMAGE_SIZE_VIEW_PORT_WEB_PX;
    }
    return scaleRectangle(IMAGE_SIZE_VIEWPORT_DP, getMdpiScaleFactor(options.density));
  }

  @NotNull
  private static Rectangle getLegacyRectangle(@NotNull LauncherIconOptions options) {
    if (options.generateWebIcon) {
      return IMAGE_SIZE_VIEW_PORT_WEB_PX;
    }
    return scaleRectangle(IMAGE_SIZE_LEGACY_DP, getMdpiScaleFactor(options.density));
  }

  @NotNull
  private static BufferedImage generateIconBackgroundLayer(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    Rectangle imageRect = getFullBleedRectangle(options);
    if (options.backgroundImage != null) {
      return generateIconLayer(context, options.backgroundImage, imageRect, false, 0, !options.generateOutputIcons);
    }

    //noinspection UseJBColor
    return generateFlatColorRectangle(new Color(options.backgroundColor), imageRect);
  }

  @NotNull
  private static BufferedImage generateIconForegroundLayer(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    Rectangle imageRect = getFullBleedRectangle(options);
    if (options.foregroundImage != null) {
      return generateIconLayer(context, options.foregroundImage, imageRect, options.useForegroundColor, options.foregroundColor,
                                       !options.generateOutputIcons);
    }

    return AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
  }

  @NotNull
  private static BufferedImage generateFlatColorRectangle(@NotNull Color color, @NotNull Rectangle imageRect) {
    BufferedImage result = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D gTemp = (Graphics2D) result.getGraphics();
    gTemp.setPaint(color);
    gTemp.fillRect(0, 0, imageRect.width, imageRect.height);
    gTemp.dispose();
    return result;
  }

  @NotNull
  private static BufferedImage applyMask(@NotNull BufferedImage image, @Nullable BufferedImage mask) {
    if (mask == null) {
      return image;
    }

    Rectangle imageRect = new Rectangle(0, 0, image.getWidth(), image.getHeight());
    BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);

    Graphics2D gTemp = (Graphics2D)tempImage.getGraphics();
    AssetUtil.drawCentered(gTemp, mask, imageRect);
    gTemp.setComposite(AlphaComposite.SrcIn);
    AssetUtil.drawCentered(gTemp, image, imageRect);
    gTemp.dispose();

    return tempImage;
  }

  @NotNull
  private static BufferedImage generateIconLayer(@NotNull GraphicGeneratorContext context, @NotNull TransformedImageAsset sourceImage,
                                                 @NotNull Rectangle imageRect, boolean useFillColor, int fillColor, boolean forPreview) {
    String scaledDrawable = sourceImage.getTransformedDrawable();
    if (scaledDrawable != null) {
      return generateIconLayer(context, scaledDrawable, imageRect);
    }

    BufferedImage trimmedImage = sourceImage.getTrimmedImage();
    if (trimmedImage != null) {
      return generateIconLayer(context, trimmedImage, imageRect, sourceImage.getScaleFactor(), useFillColor, fillColor, forPreview);
    }

    return AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
  }

  @NotNull
  private static BufferedImage generateIconLayer(@NotNull GraphicGeneratorContext context, @NotNull String xmlDrawable,
                                                 @NotNull Rectangle imageRect) {
    Future<BufferedImage> imageFuture = context.renderDrawable(xmlDrawable, imageRect.getSize());
    try {
      BufferedImage image = imageFuture.get();
      if (image != null) {
        return image;
      }
    }
    catch (ExecutionException e) {
      ExceptionUtilRt.rethrow(e.getCause());
    }
    catch (InterruptedException ignore) {
    }

    return AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
  }

  @NotNull
  private static BufferedImage generateIconLayer(@NotNull GraphicGeneratorContext context, @NotNull BufferedImage sourceImage,
                                                 @NotNull Rectangle imageRect, double scaleFactor, boolean useFillColor, int fillColor,
                                                 boolean forPreview) {
    if (forPreview && Math.max(sourceImage.getWidth(), sourceImage.getHeight()) > IMAGE_SIZE_FULL_BLEED_WEB_PX.getWidth() * 1.2) {
      // The source image is pretty large. Scale it down in preview mode to make generation of subsequent images faster.
      sourceImage = generateIconLayer(context, sourceImage, IMAGE_SIZE_FULL_BLEED_WEB_PX, 1, false, 0);
    }

    return generateIconLayer(context, sourceImage, imageRect, scaleFactor, useFillColor, fillColor);
  }

  @NotNull
  private static BufferedImage generateIconLayer(@NotNull GraphicGeneratorContext context, @NotNull BufferedImage sourceImage,
                                                 @NotNull Rectangle imageRect, double scaleFactor, boolean useFillColor, int fillColor) {
    Callable<Future<BufferedImage>> generator = () -> FutureUtils.executeOnPooledThread(() -> {
      // Scale the image.
      BufferedImage iconImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
      Graphics2D gIcon = (Graphics2D)iconImage.getGraphics();
      Rectangle rect = scaleRectangleAroundCenter(imageRect, scaleFactor);
      AssetUtil.drawCenterInside(gIcon, sourceImage, rect);
      gIcon.dispose();

      if (!useFillColor) {
        return iconImage;
      }
      // Fill with fillColor.
      BufferedImage effectImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
      Graphics2D gEffect = (Graphics2D)effectImage.getGraphics();
      //noinspection UseJBColor
      AssetUtil.Effect[] effects = new AssetUtil.Effect[] { new AssetUtil.FillEffect(new Color(fillColor), 1) };
      AssetUtil.drawEffects(gEffect, iconImage, 0, 0, effects);
      gEffect.dispose();
      return effectImage;
    });

    class CacheKey {
      @NotNull private final Object mySource;
      @NotNull private final Rectangle myImageRect;
      private final int myScaleFactorTimes1000;
      private final boolean myUseFillColor;
      private final int myFillColor;

      CacheKey(@NotNull Object source, @NotNull Rectangle imageRect, double scaleFactor, boolean useFillColor, int fillColor) {
        mySource = source;
        myImageRect = imageRect;
        myScaleFactorTimes1000 = roundToInt(scaleFactor * 1000);
        myUseFillColor = useFillColor;
        myFillColor = fillColor;
      }

      @Override
      public int hashCode() {
        return Objects.hash(mySource, myImageRect, myScaleFactorTimes1000, myUseFillColor, myFillColor);
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof CacheKey)) {
          return false;
        }
        CacheKey other = (CacheKey) obj;
        return Objects.equals(mySource, other.mySource)
               && Objects.equals(myImageRect, other.myImageRect)
               && myScaleFactorTimes1000 == other.myScaleFactorTimes1000
               && myUseFillColor == other.myUseFillColor
               && myFillColor == other.myFillColor;
      }
    }

    CacheKey cacheKey = new CacheKey(sourceImage, imageRect, scaleFactor, useFillColor, fillColor);
    Future<BufferedImage> imageFuture = context.getFromCacheOrCreate(cacheKey, generator);
    return Futures.getUnchecked(imageFuture);
  }

  private static void drawGrid(@NotNull LauncherIconOptions launcherIconOptions, @NotNull BufferedImage image) {
    Graphics2D gOut = (Graphics2D) image.getGraphics();
    drawGrid(launcherIconOptions, gOut);
    gOut.dispose();
  }

  private static void drawGrid(@NotNull LauncherIconOptions launcherIconOptions, @NotNull Graphics2D gOut) {
    if (launcherIconOptions.generateWebIcon) {
      return;
    }

    if (launcherIconOptions.previewShape == PreviewShape.FULL_BLEED) {
      if (launcherIconOptions.showGrid || launcherIconOptions.showSafeZone) {
        drawFullBleedIconGrid(launcherIconOptions, gOut);
      }
      return;
    }

    if (launcherIconOptions.previewShape == PreviewShape.LEGACY || launcherIconOptions.previewShape == PreviewShape.LEGACY_ROUND) {
      if (launcherIconOptions.showGrid) {
        drawLegacyIconGrid(launcherIconOptions, gOut);
      }
      return;
    }

    if (launcherIconOptions.showGrid || launcherIconOptions.showSafeZone) {
      drawAdaptiveIconGrid(launcherIconOptions, gOut);
    }
  }

  private static void drawAdaptiveIconGrid(@NotNull LauncherIconOptions options, @NotNull Graphics2D out) {
    double scaleFactor = getMdpiScaleFactor(options.density);

    // 72x72
    int size = IMAGE_SIZE_VIEWPORT_DP.width;
    int center = size / 2;

    //noinspection UseJBColor
    Color c = new Color(0f, 0f, 0f, 0.20f);
    out.setColor(c);
    out.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    PrimitiveShapesHelper g = new PrimitiveShapesHelper(out, scaleFactor);
    if (options.showGrid) {
      g.drawRect(0, 0, size, size);

      // "+" and "x" cross
      g.drawLine(0, 0, size, size);
      g.drawLine(size, 0, 0, size);
      g.drawLine(0, center, size, center);
      g.drawLine(center, 0, center, size);

      // 3 keyline rectangles (36x52, 44x44, 52x36)
      int arcSize = 4;
      int rect1 = 36;
      int rect2 = 44;
      int rect3 = 52;
      g.drawRoundRect((size - rect1) / 2, (size - rect3) / 2, rect1, rect3, arcSize, arcSize);
      g.drawRoundRect((size - rect2) / 2, (size - rect2) / 2, rect2, rect2, arcSize, arcSize);
      g.drawRoundRect((size - rect3) / 2, (size - rect1) / 2, rect3, rect1, arcSize, arcSize);

      // 2 keyline circles: 36dp and 52dp
      g.drawCenteredCircle(center, center, 18);
      g.drawCenteredCircle(center, center, 26);
    }

    if (options.showSafeZone) {
      // Safe zone: 66dp
      g.drawCenteredCircle(center, center, 33);
    }
  }

  private static void drawFullBleedIconGrid(@NotNull LauncherIconOptions options, @NotNull Graphics2D out) {
    double scaleFactor = getMdpiScaleFactor(options.density);

    // 108x108
    int size = IMAGE_SIZE_FULL_BLEED_DP.width;
    int center = size / 2;

    //noinspection UseJBColor
    Color c = new Color(0f, 0f, 0f, 0.20f);
    out.setColor(c);
    out.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    PrimitiveShapesHelper g = new PrimitiveShapesHelper(out, scaleFactor);
    if (options.showGrid) {
      g.drawRect(0, 0, size, size);

      // Viewport
      g.drawRect(18, 18, IMAGE_SIZE_VIEWPORT_DP.width, IMAGE_SIZE_VIEWPORT_DP.height);

      // "+" and "x" cross
      g.drawLine(0, 0, size, size);
      g.drawLine(size, 0, 0, size);
      g.drawLine(0, center, size, center);
      g.drawLine(center, 0, center, size);
    }

    if (options.showSafeZone) {
      // Safe zone: 66dp
      g.drawCenteredCircle(center, center, IMAGE_SIZE_SAFE_ZONE_DP.width / 2);
    }
  }

  private static void drawLegacyIconGrid(@NotNull LauncherIconOptions options, @NotNull Graphics2D out) {
    double scaleFactor = getMdpiScaleFactor(options.density);

    // 48x48
    int size = IMAGE_SIZE_LEGACY_DP.width;
    int center = size / 2;

    //noinspection UseJBColor
    Color c = new Color(0f, 0f, 0f, 0.20f);
    out.setColor(c);
    out.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    PrimitiveShapesHelper g = new PrimitiveShapesHelper(out, scaleFactor);
    g.drawRect(0, 0, size, size);

    // "+" and "x" cross
    g.drawLine(0, 0, size, size);
    g.drawLine(size, 0, 0, size);
    g.drawLine(0, center, size, center);
    g.drawLine(center, 0, center, size);

    // 2 keyline rectangles (32x44, 38x38, 44x32)
    int arcSize = 3;
    int rect1 = 32;
    //int rect2 = 38;
    int rect3 = 44;
    g.drawRoundRect((size - rect1) / 2, (size - rect3) / 2, rect1, rect3, arcSize, arcSize);
    //g.drawRoundRect((size - rect2) / 2, (size - rect2) / 2, rect2, rect2, arcSize, arcSize);
    g.drawRoundRect((size - rect3) / 2, (size - rect1) / 2, rect3, rect1, arcSize, arcSize);

    // 2 keyline circles: 20dp and 44dp
    g.drawCenteredCircle(center, center, 10);
    g.drawCenteredCircle(center, center, 22);
  }

  @Override
  protected boolean includeDensity(@NotNull Density density) {
    // Launcher icons should include xxxhdpi as well.
    return super.includeDensity(density) || density == Density.XXXHIGH;
  }

  @Override
  @NotNull
  protected String getIconPath(@NotNull Options options, @NotNull String iconName) {
    if (((LauncherIconOptions) options).generateWebIcon) {
      return iconName + "-web.png"; // Store at the root of the project.
    }

    return super.getIconPath(options, iconName);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(LauncherIconGenerator.class);
  }

  /** Options specific to generating launcher icons. */
  public static class LauncherIconOptions extends Options implements Cloneable {
    /** The foreground layer name, used to generate resource paths. */
    public String foregroundLayerName;

    /** The background layer name, used to generate resource paths. */
    public String backgroundLayerName;

    /**
     * Whether to use the foreground color. If we are using images as the source asset for our
     * icons, you shouldn't apply the foreground color, which would paint over it and obscure
     * the image.
     */
    public boolean useForegroundColor = true;

    /** Foreground color, as an RRGGBB packed integer */
    public int foregroundColor = 0;

    /** If foreground is a drawable, the contents of the drawable file and scaling parameters. */
    @Nullable public TransformedImageAsset foregroundImage;

    /**
     * Background color, as an RRGGBB packed integer. The background color is used only if
     * {@link #backgroundImage} is null.
     */
    public int backgroundColor = 0;

    /** If background is a drawable, the contents of the drawable file and scaling parameters. */
    @Nullable public TransformedImageAsset backgroundImage;

    /** Whether to generate the "Legacy" icon (API <= 24). */
    public boolean generateLegacyIcon = true;

    /** Whether to generate the "Round" icon (API 25). */
    public boolean generateRoundIcon = true;

    /**
     * Whether a web graphic should be generated (will ignore normal density setting). The
     * {@link #generateRasterImage(GraphicGeneratorContext, Options)} method will use this to decide
     * whether to generate a normal density icon or a high res web image. The {@link
     * IconGenerator#generateRasterImage(String, Map, GraphicGeneratorContext, Options, String)} method
     * will use this flag to determine whether it should include a web graphic in its iteration.
     */
    public boolean generateWebIcon;

    /** If set, generate a preview image. */
    public PreviewShape previewShape = PreviewShape.NONE;

    /** The density of the preview images. */
    public Density previewDensity;

    /** The shape to use for the "Legacy" icon. */
    public Shape legacyIconShape = Shape.SQUARE;

    /** The shape to use for the "Web" icon. */
    public Shape webIconShape = Shape.SQUARE;

    /** Whether to draw the keyline shapes. */
    public boolean showGrid;

    /** Whether to draw the safe zone circle. */
    public boolean showSafeZone;

    public LauncherIconOptions(boolean forPreview) {
      super(forPreview);
      iconFolderKind = IconFolderKind.MIPMAP;
    }

    @NotNull
    @Override
    public LauncherIconOptions clone() {
      return (LauncherIconOptions)super.clone();
    }
  }

  public enum PreviewShape {
    NONE("none", "none"),
    CIRCLE("circle", "Circle"),
    SQUIRCLE("squircle", "Squircle"),
    ROUNDED_SQUARE("rounded-square", "Rounded Square"),
    SQUARE("square", "Square"),
    FULL_BLEED("full-bleed-layers", "Full Bleed Layers"),
    LEGACY("legacy", "Legacy Icon"),
    LEGACY_ROUND("legacy-round", "Round Icon"),
    WEB("web", "Google Play Store");

    /** Id, used when shape is converted to a string */
    public final String id;
    /** Display name, when shape is displayed to the end-user */
    public final String displayName;

    PreviewShape(String id, String displayName) {
      this.id = id;
      this.displayName = displayName;
    }
  }

  private static class Layers {
    @NotNull public BufferedImage background;
    @NotNull public BufferedImage foreground;

    Layers(@NotNull BufferedImage background, @NotNull BufferedImage foreground) {
      this.background = background;
      this.foreground = foreground;
    }
  }
}
