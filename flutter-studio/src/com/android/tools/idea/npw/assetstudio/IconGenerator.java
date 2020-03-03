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

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.scaleDimension;

import com.android.SdkConstants;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.lint.LintIdeClient;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.icon.CategoryIconMap;
import com.android.tools.idea.npw.assetstudio.icon.IconGeneratorResult;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.android.tools.lint.checks.ApiLookup;
import com.android.utils.CharSequences;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Common base class for icon generators.
 */
public abstract class IconGenerator implements Disposable {
  protected static final ImmutableSet<Density> DENSITIES =
      ImmutableSet.of(Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH);
  private static final Map<Density, Pattern> DENSITY_PATTERNS;

  static {
    // Create regex patterns that search an icon path and find a valid density
    // Paths look like: /mipmap-hdpi/, /drawable-xxdpi/, /drawable-xxxdpi-v9/
    // Therefore, we search for the density value surrounded by symbols (especially to distinguish
    // xdpi, xxdpi, and xxxdpi).
    ImmutableMap.Builder<Density, Pattern> builder = ImmutableMap.builder();
    for (Density density : Density.values()) {
      builder.put(density, Pattern.compile(String.format(".*[^a-z]%s[^a-z].*", density.getResourceValue()), Pattern.CASE_INSENSITIVE));
    }
    DENSITY_PATTERNS = builder.build();
  }

  protected static final BufferedImage PLACEHOLDER_IMAGE = AssetStudioUtils.createDummyImage();

  private final OptionalProperty<BaseAsset> mySourceAsset = new OptionalValueProperty<>();
  private final StringProperty myOutputName = new StringValueProperty();

  protected final int myMinSdkVersion;

  @NotNull private final GraphicGeneratorContext myContext;

  @NotNull private final AtomicNullableLazyValue<ApiLookup> myApiLookup;

  @NotNull protected final String myLineSeparator;

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   * @param context the content used to render vector drawables
   */
  public IconGenerator(@NotNull Project project,
                       int minSdkVersion,
                       @NotNull GraphicGeneratorContext context) {
    myMinSdkVersion = minSdkVersion;
    myContext = context;
    myApiLookup = new AtomicNullableLazyValue<ApiLookup>() {
      @Override
      @Nullable
      protected ApiLookup compute() {
        return LintIdeClient.getApiLookup(project);
      }
    };
    myLineSeparator = CodeStyle.getSettings(project).getLineSeparator();
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public GraphicGeneratorContext getGraphicGeneratorContext() {
    return myContext;
  }

  @NotNull
  public final OptionalProperty<BaseAsset> sourceAsset() {
    return mySourceAsset;
  }

  @NotNull
  public final StringProperty outputName() {
    return myOutputName;
  }

  @NotNull
  public IconGeneratorResult generateIcons(Options options) {
    return new IconGeneratorResult(generateIcons(myContext, options, myOutputName.get()), options);
  }

  /**
   * Generates icons into a map in memory. This is useful for generating previews.
   *
   * {@link #sourceAsset()} must both be set prior to calling this method or an exception will be
   * thrown.
   */
  @NotNull
  private CategoryIconMap generateIntoMemory() {
    if (!mySourceAsset.get().isPresent()) {
      throw new IllegalStateException("Can't generate icons without a source asset set first");
    }

    Options options = createOptions(false);
    return generateIntoMemory(options);
  }

  @NotNull
  private CategoryIconMap generateIntoMemory(Options options) {
    Map<String, Map<String, BufferedImage>> categoryMap = new HashMap<>();
    generateRasterImage(null, categoryMap, myContext, options, myOutputName.get());
    return new CategoryIconMap(categoryMap);
  }

  /**
   * Like {@link #generateIntoMemory()} but returned in a format where it's easy to see which files
   * will be created / overwritten if {@link #generateIconsToDisk(File)} is called.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  public final Map<File, BufferedImage> generateIntoFileMap(@NotNull File resDirectory) {
    if (myOutputName.get().isEmpty()) {
      throw new IllegalStateException("Can't save icons to disk if a filename isn't set first");
    }

    if (resDirectory.getParentFile() == null) {
      throw new IllegalArgumentException("Invalid paths used when trying to generate an icon");
    }

    CategoryIconMap categoryIconMap = generateIntoMemory();
    return categoryIconMap.toFileMap(resDirectory.getParentFile());
  }

  /**
   * Like {@link #generateIntoMemory()} but returned in a format where it's easy to see which files
   * will be created / overwritten if {@link #generateIconsToDisk(File)} is called.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  public Map<File, GeneratedIcon> generateIntoIconMap(@NotNull File resDirectory) {
    Options options = createOptions(false);
    return generateIntoIconMap(resDirectory, options);
  }

  /**
   * Similar to {@link ##generateIntoIconMap(AndroidModuleTemplate)} but instead of generating real icons
   * uses placeholders that are much faster to produce.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  public final Map<File, GeneratedIcon> generateIconPlaceholders(@NotNull File resDirectory) {
    if (myOutputName.get().isEmpty()) {
      return Collections.emptyMap(); // May happen during initialization.
    }
    Options options = createOptions(false);
    options.usePlaceholders = true;
    return generateIntoIconMap(resDirectory, options);
  }

  /**
   * Like {@link #generateIntoMemory()} but returned in a format where it's easy to see which files
   * will be created / overwritten if {@link #generateIconsToDisk(File)} is called.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  private Map<File, GeneratedIcon> generateIntoIconMap(@NotNull File resDirectory, Options options) {
    if (myOutputName.get().isEmpty()) {
      throw new IllegalStateException("Can't save icons to disk if a filename isn't set first");
    }

    if (resDirectory.getParentFile() == null) {
      throw new IllegalArgumentException("Invalid paths used when trying to generate an icon");
    }

    IconGeneratorResult icons = generateIcons(options);
    Map<File, GeneratedIcon> outputMap = new HashMap<>();
    icons.getIcons().forEach(icon -> {
      if (icon.getOutputPath() != null && icon.getCategory() != IconCategory.PREVIEW) {
        File path = new File(resDirectory.getParentFile(), icon.getOutputPath().getNativePath());
        outputMap.put(path, icon);
      }
    });
    return outputMap;
  }

  /**
   * Generates icons and writes them to disk.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  public void generateIconsToDisk(@NotNull AndroidModuleTemplate moduleTemplate) {
    File resDirectory = getResDirectory(moduleTemplate);
    if (resDirectory != null) {
      generateIconsToDisk(resDirectory);
    }
  }

  /**
   * Generates icons and writes them to disk.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  public void generateIconsToDisk(@NotNull File resDirectory) {
    Map<File, GeneratedIcon> pathIconMap = generateIntoIconMap(resDirectory);

    ApplicationManager.getApplication().runWriteAction(() -> {
      for (Map.Entry<File, GeneratedIcon> fileImageEntry : pathIconMap.entrySet()) {
        File file = fileImageEntry.getKey();
        GeneratedIcon icon = fileImageEntry.getValue();

        if (icon instanceof GeneratedImageIcon) {
          if (FileUtilRt.extensionEquals(file.getName(), "png")) {
            writePngToDisk(file, ((GeneratedImageIcon)icon).getImage());
          }
          else {
            getLog().error("Please report this error. Unable to create icon for invalid file: " + file.getAbsolutePath());
          }
        }
        else if (icon instanceof GeneratedXmlResource) {
          if (FileUtilRt.extensionEquals(file.getName(), "xml")) {
            writeTextToDisk(file, ((GeneratedXmlResource)icon).getXmlText());
          }
          else {
            getLog().error("Please report this error. Unable to create icon for invalid file: " + file.getAbsolutePath());
          }
        }
        else {
          getLog().error("Please report this error. Unable to create icon for invalid file: " + file.getAbsolutePath());
        }
      }
    });
  }

  private void writePngToDisk(@NotNull File file, @NotNull BufferedImage image) {
    try {
      VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
      VirtualFile virtualFile = directory.findChild(file.getName());
      if (virtualFile == null || !virtualFile.exists()) {
        virtualFile = directory.createChildData(this, file.getName());
      }
      try (OutputStream outputStream = virtualFile.getOutputStream(this)) {
        ImageIO.write(image, "PNG", outputStream);
      }
    }
    catch (IOException e) {
      getLog().error(e);
    }
  }

  private void writeTextToDisk(@NotNull File file, @NotNull String text) {
    try {
      VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
      VirtualFile virtualFile = directory.findChild(file.getName());
      if (virtualFile == null || !virtualFile.exists()) {
        virtualFile = directory.createChildData(this, file.getName());
      }
      try (OutputStream outputStream = virtualFile.getOutputStream(this)) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        outputStream.write(bytes);
      }
    }
    catch (IOException e) {
      getLog().error(e);
    }
  }

  @NotNull
  public Collection<GeneratedIcon> generateIcons(@NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    List<Callable<GeneratedIcon>> tasks = createIconGenerationTasks(context, options, name);

    // Execute tasks in parallel and wait for results.
    WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();
    Disposable taskCanceler = () -> executor.cancelAllTasks();
    Disposer.register(this, taskCanceler);
    tasks.forEach(executor::execute);

    try {
      return executor.waitForTasksWithQuickFail(true);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      Disposer.dispose(taskCanceler);
    }
  }

  /**
   * Creates icon generation tasks to be executed in parallel.
   * Subclasses must override this method unless they override {@link #generateIcons(GraphicGeneratorContext, Options, String)}.
   */
  @NotNull
  protected List<Callable<GeneratedIcon>> createIconGenerationTasks(@NotNull GraphicGeneratorContext context,
                                                                    @NotNull Options options,
                                                                    @NotNull String name) {
    TransformedImageAsset image = options.image;
    if (image == null) {
      return Collections.emptyList();
    }

    List<Callable<GeneratedIcon>> tasks = new ArrayList<>();

    // Generate tasks for raster icons in different densities and a vector drawable
    // if the input can be converted to a vector drawable.
    for (Density density : DENSITIES) {
      Options localOptions = options.clone();
      localOptions.density = density;
      Density outputDensity = density == Density.XXXHIGH && image.isDrawable() ? Density.ANYDPI : density;
      if (options.generateOutputIcons) {
        if (outputDensity == Density.ANYDPI) {
          // Generate a vector drawable.
          tasks.add(() -> {
            Options iconOptions = options.clone();
            iconOptions.density = Density.ANYDPI;
            String xmlDrawableText = image.getTransformedDrawable();
            assert xmlDrawableText != null;
            iconOptions.apiVersion = calculateMinRequiredApiLevel(xmlDrawableText, myMinSdkVersion);
            return new GeneratedXmlResource(name,
                                            new PathString(getIconPath(iconOptions, name)),
                                            IconCategory.REGULAR,
                                            xmlDrawableText);
          });
        } else {
          // Generate a bitmap drawable.
          tasks.add(() -> {
            BufferedImage foregroundImage = generateRasterImage(context, localOptions);
            return new GeneratedImageIcon(name,
                                          new PathString(getIconPath(localOptions, name)),
                                          IconCategory.REGULAR,
                                          density,
                                          foregroundImage);
          });
        }
      }
      if (options.generatePreviewIcons) {
        // Generate tasks for preview images.
        tasks.add(() -> {
          BufferedImage rasterImage = generateRasterImage(context, localOptions);
          return new GeneratedImageIcon(outputDensity.getResourceValue(),
                                        null, // No path for preview icons.
                                        IconCategory.PREVIEW,
                                        density,
                                        rasterImage);
        });
      }
    }

    return tasks;
  }

  /**
   * Generates a single raster image using the given options.
   *
   * @param context render context to use for looking up resources etc
   * @param options options controlling the appearance of the icon
   * @return a {@link BufferedImage} with the generated icon
   */
  @NotNull
  public abstract BufferedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull Options options);

  /**
   * Generates a single raster image of the given size using the given options.
   *
   * @param iconSize the size of the image to produce
   * @param options options controlling the appearance of the icon
   * @return a {@link BufferedImage} with the generated icon
   */
  @NotNull
  protected BufferedImage generateRasterImage(@NotNull Dimension iconSize, @NotNull Options options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    double scaleFactor = getMdpiScaleFactor(options.density);
    Dimension imageSize = scaleDimension(iconSize, scaleFactor);
    TransformedImageAsset imageAsset = options.image;
    return imageAsset == null ? PLACEHOLDER_IMAGE : imageAsset.getTransformedImage(imageSize);
  }


  @NotNull
  public abstract Options createOptions(boolean forPreview);

  /**
   * Computes the target filename (relative to the Android project folder) where an icon rendered
   * with the given options should be stored. This is also used as the map keys in the result map
   * used by {@link #generateRasterImage(String, Map, GraphicGeneratorContext, Options, String)}.
   *
   * @param options the options object used by the generator for the current image
   * @param iconName the base name to use when creating the path
   * @return a platform-independent path relative to the project folder where the image should be stored
   */
  @NotNull
  protected String getIconPath(@NotNull Options options, @NotNull String iconName) {
    return getIconFolder(options) + '/' + getIconFileName(options, iconName);
  }

  /**
   * Returns the name of an icon file.
   */
  @NotNull
  private static String getIconFileName(@NotNull Options options, @NotNull String iconName) {
    if (options.density == Density.ANYDPI) {
      return iconName + SdkConstants.DOT_XML;
    }
    return iconName + SdkConstants.DOT_PNG;
  }

  /**
   * Returns the name of the folder to contain the resource. It usually includes the density, but is also
   * sometimes modified by options. For example, in some notification icons we add in -v9 or -v11.
   */
  @NotNull
  protected String getIconFolder(@NotNull Options options) {
    switch (options.iconFolderKind) {
      case DRAWABLE:
        return getIconFolder(ResourceFolderType.DRAWABLE, options.density, options.apiVersion);
      case MIPMAP:
        return getIconFolder(ResourceFolderType.MIPMAP, options.density, options.apiVersion);
      case DRAWABLE_NO_DPI:
        return getIconFolder(ResourceFolderType.DRAWABLE, Density.NODPI, options.apiVersion);
      case VALUES:
        return getIconFolder(ResourceFolderType.VALUES, Density.NODPI, options.apiVersion);
      default:
        throw new IllegalArgumentException("Unexpected folder kind: " + options.iconFolderKind);
    }
  }

  @NotNull
  private static String getIconFolder(@NotNull ResourceFolderType folderType, @NotNull Density density, int apiVersion) {
    StringBuilder buf = new StringBuilder(50);
    buf.append(SdkConstants.FD_RES);
    buf.append('/');
    buf.append(folderType.getName());
    if (density != Density.NODPI) {
      buf.append('-');
      buf.append(density.getResourceValue());
    }
    if (apiVersion > 1) {
      buf.append("-v");
      buf.append(apiVersion);
    }
    return buf.toString();
  }

  /**
   * Generates a full set of icons into the given map. The values in the map will be the generated
   * images, and each value is keyed by the corresponding relative path of the image, which is
   * determined by the {@link #getIconPath(Options, String)} method.
   *
   * @param category the current category to place images into (if null the density name will be used)
   * @param categoryMap the map to put images into, should not be null. The map is a map from a category name,
   *                   to a map from file path to image.
   * @param context a generator context which for example can load resources
   * @param options options to apply to this generator
   * @param name the base name of the icons to generate
   */
  public void generateRasterImage(@Nullable String category, @NotNull Map<String, Map<String, BufferedImage>> categoryMap,
                                  @NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    // Vector image only need to generate one preview image, so we bypass all the other image densities.
    if (options.density == Density.ANYDPI) {
      generateImageAndUpdateMap(category, categoryMap, context, options, name);
      return;
    }
    Density[] densityValues = Density.values();
    // Sort density values into ascending order.
    Arrays.sort(densityValues, Comparator.comparingInt(Density::getDpiValue));
    for (Density density : densityValues) {
      if (!density.isValidValueForDevice()) {
        continue;
      }
      if (!includeDensity(density)) {
        // Not yet supported -- missing stencil image.
        // TODO don't manually check and instead gracefully handle missing stencils.
        continue;
      }
      options.density = density;
      generateImageAndUpdateMap(category, categoryMap, context, options, name);
    }
  }

  private void generateImageAndUpdateMap(@Nullable String category, @NotNull Map<String, Map<String, BufferedImage>> categoryMap,
                                         @NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    BufferedImage image = generateRasterImage(context, options);
    // The category key is either the "category" parameter or the density if not present.
    String mapCategory = category;
    if (mapCategory == null) {
      mapCategory = options.density.getResourceValue();
    }
    Map<String, BufferedImage> imageMap = categoryMap.computeIfAbsent(mapCategory, k -> new LinkedHashMap<>());

    // Store image in map, where the key is the relative path to the image.
    imageMap.put(getIconPath(options, name), image);
  }

  protected boolean includeDensity(@NotNull Density density) {
    return density.isRecommended() && density != Density.LOW;
  }

  /**
   * Returns the scale factor to apply for a given MDPI density to compute the absolute pixel count
   * to use to draw an icon of the given target density.
   *
   * @param density the density
   * @return a factor to multiple mdpi distances with to compute the target density
   */
  public static double getMdpiScaleFactor(@NotNull Density density) {
    if (density == Density.ANYDPI) {
      density = Density.XXXHIGH;
    }
    if (density == Density.NODPI) {
      density = Density.MEDIUM;
    }
    return density.getDpiValue() / (double)Density.MEDIUM.getDpiValue();
  }

  /**
   * Converts the path to a density, if possible. Output paths don't always map cleanly to density
   * values, such as the path for the "web" icon, so in those cases, {@code null} is returned.
   */
  @Nullable
  public static Density pathToDensity(@NotNull String iconPath) {
    iconPath = FileUtils.toSystemIndependentPath(iconPath);
    // Strip off the filename, in case the user names their icon "xxxhdpi" etc.
    // but leave the trailing slash, as it's used in the regex pattern.
    iconPath = iconPath.substring(0, iconPath.lastIndexOf('/') + 1);

    for (Density density : Density.values()) {
      if (DENSITY_PATTERNS.get(density).matcher(iconPath).matches()) {
        return density;
      }
    }

    return null;
  }

  /**
   * Determines the minimal API level required to display the given XML drawable.
   * See <a href="https://issuetracker.google.com/68259550">bug 68259550</a>.
   *
   * @param xmlDrawableText the text of the XML drawable
   * @param minSdk the minimal API level supported by the app
   * @return the required minimal API level if greater than {@code minSdk}, otherwise zero
   */
  protected int calculateMinRequiredApiLevel(@NotNull String xmlDrawableText, int minSdk) {
    ApiLookup apiLookup = myApiLookup.getValue();
    if (apiLookup == null) {
      return 0;
    }
    KXmlParser parser = new KXmlParser();
    int requiredApiLevel = 0;
    try {
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      parser.setInput(CharSequences.getReader(xmlDrawableText, true));
      int type;
      // Iterate over the XML document and check all attributes to determine the required minimal API level.
      while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
        if (type == XmlPullParser.START_TAG) {
          // See similar logic in com.android.tools.lint.checks.ApiDetector.visitAttribute().
          for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (SdkConstants.ANDROID_URI.equals(parser.getAttributeNamespace(i))) {
              String attributeName = parser.getAttributeName(i);
              if (!attributeName.equals("fillType")) { // Exclude android:fillType since it is supported by AppCompat.
                int attributeApiLevel = apiLookup.getFieldVersion("android/R$attr", attributeName);
                if (requiredApiLevel < attributeApiLevel) {
                  requiredApiLevel = attributeApiLevel;
                }
              }
            }
          }
        }
      }
    }
    catch (XmlPullParserException | IOException e) {
      // Ignore.
    }

    // Do not report anything below API 21 or minSdk, whichever is higher.
    return requiredApiLevel > minSdk && requiredApiLevel > 21 ? requiredApiLevel : 0;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(IconGenerator.class);
  }

  /**
   * Returns the most specific resource directory of the given template.
   *
   * @see AndroidModuleTemplate#getResDirectories()
   */
  @Nullable
  public static File getResDirectory(@NotNull AndroidModuleTemplate template) {
    return Iterables.getLast(template.getResDirectories(), null);
  }

  /**
   * Options used for all generators.
   */
  public static class Options implements Cloneable {
    /** Whether to actual icons to be written to disk. */
    public boolean generateOutputIcons;

    /** Whether to actual preview icons. */
    public boolean generatePreviewIcons;

    /** The contents of the source image and scaling parameters. */
    @Nullable public TransformedImageAsset image;

    /** Source image to use as a basis for the icon. */
    @Nullable public ListenableFuture<BufferedImage> sourceImageFuture;

    /** Indicated whether the source image should be trimmed or not. */
    public boolean isTrimmed;

    /** Percent of padding for the source image. */
    public int paddingPercent;

    /** Controls the directory where to store the icon/resource. */
    @NotNull public IconFolderKind iconFolderKind = IconFolderKind.DRAWABLE;

    /** The density to generate the icon with. Web icons use {@link Density#NODPI}. */
    @NotNull public Density density = Density.XHIGH;

    /**
     * Controls the API version suffix, e.g. "-v23", of the directory where to store the icon/resource.
     * A value less than 2 means no suffix.
     */
    public int apiVersion;

    /** Indicates that the graphic generator may use placeholders instead of real images. */
    public boolean usePlaceholders;

    public Options(boolean forPreview) {
      generatePreviewIcons = forPreview;
      generateOutputIcons = !forPreview;
    }

    @Override
    @NotNull
    public Options clone() {
      try {
        return (Options)super.clone();
      }
      catch (CloneNotSupportedException e) {
        throw new Error(e); // Not possible.
      }
    }
  }

  public enum IconFolderKind {
    DRAWABLE,
    DRAWABLE_NO_DPI,
    MIPMAP,
    VALUES,
  }

  /** Shapes that can be used for icon backgrounds. */
  public enum Shape {
    /** No background. */
    NONE("none"),
    /** Circular background. */
    CIRCLE("circle"),
    /** Square background. */
    SQUARE("square"),
    /** Vertical rectangular background. */
    VRECT("vrect"),
    /** Horizontal rectangular background. */
    HRECT("hrect"),
    /** Square background with Dog-ear effect. */
    SQUARE_DOG("square_dogear"),
    /** Vertical rectangular background with Dog-ear effect. */
    VRECT_DOG("vrect_dogear"),
    /** Horizontal rectangular background with Dog-ear effect. */
    HRECT_DOG("hrect_dogear");

    /** Id, used in filenames to identify associated stencils. */
    public final String id;

    Shape(String id) {
      this.id = id;
    }
  }

  /** Foreground effects styles. */
  public enum Style {
    /** No effects */
    SIMPLE("fore1");

    /** Id, used in filenames to identify associated stencils */
    public final String id;

    Style(String id) {
      this.id = id;
    }
  }
}
