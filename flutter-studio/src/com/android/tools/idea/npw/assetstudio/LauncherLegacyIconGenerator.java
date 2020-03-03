/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio;

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.scaleRectangle;

import com.android.ide.common.util.AssetUtil;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.project.Project;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generator of legacy Android launcher icons.
 *
 * Defaults from https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here.
public class LauncherLegacyIconGenerator extends IconGenerator {
  public static final Color DEFAULT_FOREGROUND_COLOR = Color.BLACK;
  public static final Color DEFAULT_BACKGROUND_COLOR = Color.WHITE;
  public static final Shape DEFAULT_ICON_SHAPE = Shape.SQUARE;

  private static final Rectangle IMAGE_SIZE_WEB = new Rectangle(0, 0, 512, 512);
  private static final Rectangle IMAGE_SIZE_MDPI = new Rectangle(0, 0, 48, 48);

  private static final Map<Pair<Shape, Density>, Rectangle> TARGET_RECTS = buildTargetRectangles();

  private final ObjectProperty<Color> myBackgroundColor = new ObjectValueProperty<>(DEFAULT_BACKGROUND_COLOR);
  private final ObjectProperty<Shape> myShape = new ObjectValueProperty<>(DEFAULT_ICON_SHAPE);
  private final BoolProperty myCropped = new BoolValueProperty();
  private final BoolProperty myDogEared = new BoolValueProperty();

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public LauncherLegacyIconGenerator(@NotNull Project project, int minSdkVersion, @Nullable DrawableRenderer renderer) {
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
   * If {@code true}, any extra part of the source asset that doesn't fit on the final icon will
   * be cropped. Otherwise, the source asset will be shrunk to fit.
   */
  @NotNull
  public BoolProperty cropped() {
    return myCropped;
  }

  /**
   * A shape which will be used as the icon's backdrop.
   */
  @NotNull
  public ObjectProperty<Shape> shape() {
    return myShape;
  }

  /**
   * If true and the backdrop shape supports it, add a fold to the top-right corner of the backdrop shape.
   */
  @NotNull
  public BoolProperty dogEared() {
    return myDogEared;
  }

  @Override
  @NotNull
  public LauncherLegacyOptions createOptions(boolean forPreview) {
    LauncherLegacyOptions options = new LauncherLegacyOptions(forPreview);
    BaseAsset asset = sourceAsset().getValueOrNull();
    if (asset != null) {
      double paddingFactor = asset.paddingPercent().get() / 100.;
      double scaleFactor = 1. / (1 + paddingFactor * 2);
      options.useForegroundColor = asset.isColorable();
      Color color = asset.isColorable() ? asset.color().getValueOrNull() : null;
      if (color != null) {
        options.foregroundColor = color.getRGB();
      }
      options.image = new TransformedImageAsset(asset, IMAGE_SIZE_MDPI.getSize(), scaleFactor, color, getGraphicGeneratorContext());
    }

    options.shape = myShape.get();
    options.crop = myCropped.get();
    options.style = Style.SIMPLE;
    options.backgroundColor = myBackgroundColor.get().getRGB();
    options.generateWebIcon = true;
    options.isDogEar = myDogEared.get();

    return options;
  }

  /**
   * Modifies the value of the option to take into account the dog-ear effect. This effect only
   * applies to Square, Hrect and Vrect shapes.
   *
   * @param shape shape of the icon before applying dog-ear effect
   * @return shape with dog-ear effect on
   */
  private static Shape applyDog(Shape shape) {
    if (shape == Shape.SQUARE) {
      return Shape.SQUARE_DOG;
    } else if (shape == Shape.HRECT) {
      return Shape.HRECT_DOG;
    } else if (shape == Shape.VRECT) {
      return Shape.VRECT_DOG;
    } else {
      return shape;
    }
  }

  /**
   * Loads a pref-defined image file given a {@link Shape}, {@link Density} and fileName.
   *
   * <p>Pass a {@link Density#NODPI} to get the {@link Rectangle} corresponding to the "Web" image size.
   */
  @Nullable
  private static BufferedImage loadImage(@NotNull GraphicGeneratorContext context, @NotNull Shape shape, @NotNull Density density,
                                         @NotNull String fileName) {
    String densityValue = density == Density.NODPI ? "web" : density.getResourceValue();
    String name = String.format("/images/launcher_stencil/%s/%s/%s.png", shape.id, densityValue, fileName);
    return context.loadImageResource(name);
  }

  /**
   * Loads a pref-defined mask image file given a {@link Shape}, {@link Density} and fileName.
   *
   * <p>Pass a <code>null</code> {@link Density} to get the {@link Rectangle} corresponding to the
   * "Web" image size.
   */
  @Nullable
  public static BufferedImage loadMaskImage(@NotNull GraphicGeneratorContext context, @NotNull Shape shape, @NotNull Density density) {
    return loadImage(context, shape, density, "mask");
  }

  /**
   * Loads a pref-defined background image file given a {@link Shape}, {@link Density} and fileName.
   *
   * <p>Pass a {@link Density#NODPI} to get the {@link Rectangle} corresponding to the "Web" image size.
   */
  @Nullable
  public static BufferedImage loadBackImage(@NotNull GraphicGeneratorContext context, @NotNull Shape shape, @NotNull Density density) {
    return loadImage(context, shape, density, "back");
  }

  /**
   * Loads a pref-defined style image file given a {@link Shape}, {@link Density} and fileName.
   *
   * <p>Pass a {@link Density#NODPI} to get the {@link Rectangle} corresponding to the "Web" image size.
   */
  @Nullable
  public static BufferedImage loadStyleImage(@NotNull GraphicGeneratorContext context, @NotNull Shape shape, @NotNull Density density,
                                             @NotNull Style style) {
    return loadImage(context, shape, density, style.id);
  }

  /**
   * Returns the {@link Rectangle} (in pixels) where the foreground image of a legacy icon should
   * be rendered. The {@link Rectangle} value depends on the {@link Shape} of the background, as
   * different shapes have different sizes.
   *
   * <p>Pass a {@link Density#NODPI} to get the {@link Rectangle} corresponding to the "Web" image size.
   */
  @NotNull
  public static Rectangle getTargetRect(@Nullable Shape shape, @NotNull Density density) {
    Rectangle targetRect = TARGET_RECTS.get(Pair.of(shape, density));
    if (targetRect == null) {
      // Scale up from MDPI if no density-specific target rectangle is defined.
      targetRect = scaleRectangle(TARGET_RECTS.get(Pair.of(shape, Density.MEDIUM)), getMdpiScaleFactor(density));
    }
    return targetRect;
  }

  @Override
  @NotNull
  public BufferedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull Options options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    LauncherLegacyOptions launcherOptions = (LauncherLegacyOptions)options;
    Rectangle imageRect =
        launcherOptions.generateWebIcon ? IMAGE_SIZE_WEB : scaleRectangle(IMAGE_SIZE_MDPI, getMdpiScaleFactor(launcherOptions.density));

    if (launcherOptions.isDogEar) {
      launcherOptions.shape = applyDog(launcherOptions.shape);
    }

    BufferedImage shapeImageBack = null;
    BufferedImage shapeImageFore = null;
    BufferedImage shapeImageMask = null;
    if (launcherOptions.shape != Shape.NONE && launcherOptions.shape != null && launcherOptions.renderShape) {
      Density loadImageDensity = launcherOptions.generateWebIcon ? Density.NODPI : launcherOptions.density;
      shapeImageBack = loadBackImage(context, launcherOptions.shape, loadImageDensity);
      shapeImageFore = loadStyleImage(context, launcherOptions.shape, loadImageDensity, launcherOptions.style);
      shapeImageMask = loadMaskImage(context, launcherOptions.shape, loadImageDensity);
    }

    Rectangle targetRect = getTargetRect(launcherOptions.shape, launcherOptions.density);

    // outImage will be our final image. Many intermediate textures will be rendered, in
    // layers, onto this image.
    BufferedImage outImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D gOut = (Graphics2D) outImage.getGraphics();
    if (shapeImageBack != null) {
      gOut.drawImage(shapeImageBack, 0, 0, null);
    }

    // Render the background shape into an intermediate buffer. This lets us set a fill color.
    BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D gTemp = (Graphics2D) tempImage.getGraphics();
    if (shapeImageMask != null) {
      gTemp.drawImage(shapeImageMask, 0, 0, null);
      gTemp.setComposite(AlphaComposite.SrcAtop);
      gTemp.setPaint(new Color(launcherOptions.backgroundColor));
      gTemp.fillRect(0, 0, imageRect.width, imageRect.height);
    }

    BufferedImage sourceImage = generateRasterImage(targetRect.getSize(), options);

    // Render the foreground icon onto an intermediate buffer and then render over the
    // background shape. This lets us override the color of the icon.
    BufferedImage iconImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D gIcon = (Graphics2D) iconImage.getGraphics();
    if (launcherOptions.crop) {
      AssetUtil.drawCenterCrop(gIcon, sourceImage, targetRect);
    } else {
      AssetUtil.drawCenterInside(gIcon, sourceImage, targetRect);
    }
    AssetUtil.Effect[] effects;
    if (launcherOptions.useForegroundColor) {
      effects = new AssetUtil.Effect[] { new AssetUtil.FillEffect(new Color(launcherOptions.foregroundColor), 1.0) };
    } else {
      effects = AssetUtil.NO_EFFECTS;
    }
    AssetUtil.drawEffects(gTemp, iconImage, 0, 0, effects);

    // Finally, render all layers to the output image
    gOut.drawImage(tempImage, 0, 0, null);
    if (shapeImageFore != null) {
      // Useful for some shape effects, like dogear (e.g. folded top right corner)
      gOut.drawImage(shapeImageFore, 0, 0, null);
    }

    gOut.dispose();
    gTemp.dispose();
    gIcon.dispose();

    return outImage;
  }

  @Override
  protected boolean includeDensity(@NotNull Density density) {
    // Launcher icons should include xxxhdpi as well.
    return super.includeDensity(density) || density == Density.XXXHIGH;
  }

  @Override
  public void generateRasterImage(@Nullable String category, @NotNull Map<String, Map<String, BufferedImage>> categoryMap,
                                  @NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    LauncherLegacyOptions launcherOptions = (LauncherLegacyOptions)options;
    boolean generateWebImage = launcherOptions.generateWebIcon;
    launcherOptions.generateWebIcon = false;
    super.generateRasterImage(category, categoryMap, context, options, name);

    if (generateWebImage) {
      launcherOptions.generateWebIcon = true;
      launcherOptions.density = Density.NODPI;
      BufferedImage image = generateRasterImage(context, options);
      Map<String, BufferedImage> imageMap = new HashMap<>();
      categoryMap.put("Web", imageMap);
      imageMap.put(getIconPath(options, name), image);
    }
  }

  @Override
  @NotNull
  public Collection<GeneratedIcon> generateIcons(@NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    Map<String, Map<String, BufferedImage>> categoryMap = new HashMap<>();
    generateRasterImage(null, categoryMap, context, options, name);

    // Category map is a map from category name to a map from relative path to image.
    List<GeneratedIcon> icons = new ArrayList<>();
    categoryMap.forEach(
      (category, images) ->
        images.forEach(
          (path, image) -> {
            Density density = pathToDensity(path);
            // Could be a "Web" image
            if (density == null) {
              density = Density.NODPI;
            }
            GeneratedImageIcon icon = new GeneratedImageIcon(path, new PathString(path), IconCategory.REGULAR, density, image);
            icons.add(icon);
          }));
    return icons;
  }

  @Override
  @NotNull
  protected String getIconPath(@NotNull Options options, @NotNull String iconName) {
    if (((LauncherLegacyOptions)options).generateWebIcon) {
      return iconName + "-web.png"; // Store at the root of the project
    }

    return super.getIconPath(options, iconName);
  }

  private static Map<Pair<Shape, Density>, Rectangle> buildTargetRectangles() {
    ImmutableMap.Builder<Pair<Shape, Density>, Rectangle> targetRects = new ImmutableMap.Builder<>();
    // None, Web
    targetRects.put(Pair.of(Shape.NONE, Density.NODPI), new Rectangle(32, 32, 448, 448));
    // None, HDPI
    targetRects.put(Pair.of(Shape.NONE, Density.HIGH), new Rectangle(4, 4, 64, 64));
    // None, MDPI
    targetRects.put(Pair.of(Shape.NONE, Density.MEDIUM), new Rectangle(3, 3, 42, 42));

    // Circle, Web
    targetRects.put(Pair.of(Shape.CIRCLE, Density.NODPI), new Rectangle(21, 21, 470, 470));
    // Circle, HDPI
    targetRects.put(Pair.of(Shape.CIRCLE, Density.HIGH), new Rectangle(3, 3, 66, 66));
    // Circle, MDPI
    targetRects.put(Pair.of(Shape.CIRCLE, Density.MEDIUM), new Rectangle(2, 2, 44, 44));

    // Square, Web
    targetRects.put(Pair.of(Shape.SQUARE, Density.NODPI), new Rectangle(53, 53, 406, 406));
    // Square, HDPI
    targetRects.put(Pair.of(Shape.SQUARE, Density.HIGH), new Rectangle(7, 7, 57, 57));
    // Square, MDPI
    targetRects.put(Pair.of(Shape.SQUARE, Density.MEDIUM), new Rectangle(5, 5, 38, 38));

    // Vertical Rectangle, Web
    targetRects.put(Pair.of(Shape.VRECT, Density.NODPI), new Rectangle(85, 21, 342, 470));
    // Vertical Rectangle, HDPI
    targetRects.put(Pair.of(Shape.VRECT, Density.HIGH), new Rectangle(12, 3, 48, 66));
    // Vertical Rectangle, MDPI
    targetRects.put(Pair.of(Shape.VRECT, Density.MEDIUM), new Rectangle(8, 2, 32, 44));

    // Horizontal Rectangle, Web
    targetRects.put(Pair.of(Shape.HRECT, Density.NODPI), new Rectangle(21, 85, 470, 342));
    // Horizontal Rectangle, HDPI
    targetRects.put(Pair.of(Shape.HRECT, Density.HIGH), new Rectangle(3, 12, 66, 48));
    // Horizontal Rectangle, MDPI
    targetRects.put(Pair.of(Shape.HRECT, Density.MEDIUM), new Rectangle(2, 8, 44, 32));

    // Square Dog-ear, Web
    targetRects.put(Pair.of(Shape.SQUARE_DOG, Density.NODPI), new Rectangle(53, 149, 406, 312));
    // Square Dog-ear, HDPI
    targetRects.put(Pair.of(Shape.SQUARE_DOG, Density.HIGH), new Rectangle(7, 21, 57, 43));
    // Square Dog-ear, MDPI
    targetRects.put(Pair.of(Shape.SQUARE_DOG, Density.MEDIUM), new Rectangle(5, 14, 38, 29));

    // Vertical Rectangle Dog-ear, Web
    targetRects.put(Pair.of(Shape.VRECT_DOG, Density.NODPI), new Rectangle(85, 117, 342, 374));
    // Vertical Rectangle Dog-ear, HDPI
    targetRects.put(Pair.of(Shape.VRECT_DOG, Density.HIGH), new Rectangle(12, 17, 48, 52));
    // Vertical Rectangle Dog-ear, MDPI
    targetRects.put(Pair.of(Shape.VRECT_DOG, Density.MEDIUM), new Rectangle(8, 11, 32, 35));

    // Horizontal Rectangle Dog-ear, Web
    targetRects.put(Pair.of(Shape.HRECT_DOG, Density.NODPI), new Rectangle(21, 85, 374, 342));
    // Horizontal Rectangle Dog-ear, HDPI
    targetRects.put(Pair.of(Shape.HRECT_DOG, Density.HIGH), new Rectangle(3, 12, 52, 48));
    // Horizontal Rectangle Dog-ear, MDPI
    targetRects.put(Pair.of(Shape.HRECT_DOG, Density.MEDIUM), new Rectangle(2, 8, 35, 32));
    return targetRects.build();
  }

  /** Options specific to generating launcher icons */
  public static class LauncherLegacyOptions extends Options {
    /**
     * Whether to use the foreground color. If we are using images as the source asset for our icons,
     * you shouldn't apply the foreground color, which would paint over it and obscure the image.
     */
    public boolean useForegroundColor = true;

    /** Foreground color, as an RRGGBB packed integer. */
    public int foregroundColor;

    /** Background color, as an RRGGBB packed integer. */
    public int backgroundColor;

    /** Whether the image should be cropped or not. */
    public boolean crop = true;

    /** The shape to use for the background. */
    public Shape shape = Shape.SQUARE;

    /** The effects to apply to the foreground. */
    public Style style = Style.SIMPLE;

    /** Whether or not to use the dog-ear effect. */
    public boolean isDogEar;

    /**
     * Whether to render the background shape. If false, the shape is still used to
     * scale the image to the target rectangle associated to the shape.
     */
    public boolean renderShape = true;

    /**
     * Whether a web graphic should be generated (will ignore normal density setting).
     * The {@link #generateRasterImage(GraphicGeneratorContext, Options)} method will use this to
     * decide whether to generate a normal density icon or a high res web image.
     * The {@link #generateRasterImage(String, Map, GraphicGeneratorContext, Options, String)} method
     * will use this flag to determine whether it should include a web graphic in its iteration.
     */
    public boolean generateWebIcon;

    public LauncherLegacyOptions(boolean forPreview) {
      super(forPreview);
      iconFolderKind = IconFolderKind.MIPMAP;
    }
  }
}
