/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.ide.common.vectordrawable.VdPreview;
import com.android.utils.Pair;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The context used for graphic generation.
 */
public class GraphicGeneratorContext {
  private final Cache<Object, Future<BufferedImage>> myImageCache;
  private final DrawableRenderer myDrawableRenderer;

  /**
   * @param maxCacheSize the maximum size of the image cache
   */
  public GraphicGeneratorContext(int maxCacheSize) {
    this(maxCacheSize, null);
  }

  /**
   * @param maxCacheSize the maximum size of the image cache
   * @param drawableRenderer the renderer used to convert XML drawables into raster images
   */
  public GraphicGeneratorContext(int maxCacheSize, @Nullable DrawableRenderer drawableRenderer) {
    myImageCache = CacheBuilder.newBuilder().maximumSize(maxCacheSize).build();
    myDrawableRenderer = drawableRenderer;
  }

  /**
   * Returns the image from the cache or creates a new image, puts it in the cache, and returns it.
   *
   * @param key the key for the cache lookup
   * @param creator the image creator that is called if the image was not found in the cache
   * @return the cached or the newly created image
   */
  @NotNull
  public final Future<BufferedImage> getFromCacheOrCreate(@NotNull Object key, @NotNull Callable<? extends Future<BufferedImage>> creator) {
    try {
      return myImageCache.get(key, creator);
    }
    catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause == null) {
        cause = e;
      }
      getLog().error(cause);
      return Futures.immediateFailedFuture(cause);
    }
  }

  /**
   * Loads the given image resource, as requested by the graphic generator.
   *
   * @param path the path to the resource, relative to the general "resources" path
   * @return the loaded image resource, or null if there was an error
   */
  @Nullable
  public BufferedImage loadImageResource(@NotNull String path) {
    try {
      Future<BufferedImage> imageFuture = getFromCacheOrCreate(path, () -> getStencilImage(path));
      return imageFuture.get();
    }
    catch (ExecutionException | InterruptedException e) {
      getLog().error(e);
      return null;
    }
  }

  /**
   * Renders the given drawable to a raster image.
   *
   * @param xmlDrawableText the text of an XML drawable
   * @param size the size of the raster image
   * @return the raster image that is created asynchronously
   * @throws IllegalStateException if a drawable renderer was not provided to the constructor
   */
  @NotNull
  public Future<BufferedImage> renderDrawable(@NotNull String xmlDrawableText, @NotNull Dimension size) {
    Pair<String, Dimension> key = Pair.of(xmlDrawableText, size);
    Callable<Future<BufferedImage>> renderer = myDrawableRenderer == null ?
                                               () -> renderVectorDrawable(xmlDrawableText, size) :
                                               () -> myDrawableRenderer.renderDrawable(xmlDrawableText, size);
    return getFromCacheOrCreate(key, renderer);
  }

  @NotNull
  private static Future<BufferedImage> renderVectorDrawable(@NotNull String vectorDrawableText, @NotNull Dimension size) {
    VdPreview.TargetSize targetSize = VdPreview.TargetSize.createFromMaxDimension(Math.max(size.width, size.height));
    BufferedImage image = VdPreview.getPreviewFromVectorXml(targetSize, vectorDrawableText, null);
    if (image == null) {
      image = AssetStudioUtils.createDummyImage();
    }
    return Futures.immediateFuture(image);
  }

  @NotNull
  private static Future<BufferedImage> getStencilImage(@NotNull String path) throws IOException {
    BufferedImage image = BuiltInImages.getStencilImage(path);
    if (image == null) {
      image = AssetStudioUtils.createDummyImage();
    }

    return Futures.immediateFuture(image);
  }

  @NotNull
  private Logger getLog() {
    return Logger.getInstance(getClass());
  }
}
