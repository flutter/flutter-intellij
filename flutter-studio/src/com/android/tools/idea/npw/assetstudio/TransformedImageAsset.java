/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.ide.common.util.AssetUtil.NO_EFFECTS;
import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.roundToInt;

import com.android.ide.common.util.AssetUtil;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.google.common.util.concurrent.Futures;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A raster image or an XML drawable with transformation parameters. Thread safe.
 */
public final class TransformedImageAsset {
  @Nullable private final Future<BufferedImage> myImageFuture;
  @Nullable private final Future<String> myDrawableFuture;
  @NotNull private final GraphicGeneratorContext myContext;
  @Nullable private final Color myTint;
  private final double myOpacity;
  private final boolean myIsTrimmed;
  @NotNull private final Dimension myTargetSize;
  private final double myScaleFactor;
  @Nullable private Rectangle2D myTrimRectangle;
  @GuardedBy("myLock")
  @Nullable private String myTransformedDrawable;
  @GuardedBy("myLock")
  @Nullable private BufferedImage myTrimmedImage;
  private final Object myLock = new Object();

  /**
   * Initializes a new transformed image asset.
   *
   * @param asset the source image asset, also supplies opacity factor, and trimming flag
   * @param targetSize the size of the transformed image
   * @param scaleFactor the scale factor to be applied to the image
   * @param tint the tint to apply to the image, or null to preserve original colors
   * @param context the trim rectangle calculator
   */
  public TransformedImageAsset(@NotNull BaseAsset asset,
                               @NotNull Dimension targetSize,
                               double scaleFactor,
                               @Nullable Color tint,
                               @NotNull GraphicGeneratorContext context) {
    myDrawableFuture = asset instanceof ImageAsset ? ((ImageAsset)asset).getXmlDrawable() : null;
    myImageFuture = myDrawableFuture == null ? asset.toImage() : null;
    myTint = tint;
    myOpacity = asset.opacityPercent().get() / 100.;
    myIsTrimmed = asset.trimmed().get();
    myTargetSize = targetSize;
    myScaleFactor = scaleFactor;
    myContext = context;
  }

  public boolean isDrawable() {
    return myDrawableFuture != null;
  }

  public boolean isRasterImage() {
    return myImageFuture != null;
  }

  /**
   * Returns the text of an XML drawable suitable for use in an icon, or null if this object doesn't represent a drawable.
   * <p>
   * This method is potentially long running. Avoid calling on the UI thread.
   */
  @Nullable
  public String getTransformedDrawable() {
    if (myDrawableFuture == null) {
      return null;
    }
    try {
      synchronized (myLock) {
        String xmlDrawable = myDrawableFuture.get();
        if (xmlDrawable == null) {
          return null;
        }
        if (myTransformedDrawable == null) {
          Rectangle2D clipRectangle = myIsTrimmed ? getTrimRectangle(xmlDrawable) : null;
          myTransformedDrawable =
              VectorDrawableTransformer.transform(xmlDrawable, myTargetSize, myScaleFactor, clipRectangle, myTint, myOpacity);
        }
        return myTransformedDrawable;
      }
    }
    catch (InterruptedException | ExecutionException e) {
      return null;
    }
  }

  /**
   * Returns the raster image of the given size. If the image cannot be rendered, the returned image is empty.
   * <p>
   * This method is potentially long running. Avoid calling on the UI thread.
   */
  @NotNull
  public BufferedImage getTransformedImage(@NotNull Dimension imageSize) {
    if (isDrawable()) {
      String drawable = getTransformedDrawable();
      if (drawable != null) {
        Future<BufferedImage> future = myContext.renderDrawable(drawable, imageSize);
        try {
          return future.get();
        }
        catch (InterruptedException | ExecutionException e) {
          // Ignore to fall through to the dummy image creation.
        }
      }
    }

    // Transform bitmap image.
    BufferedImage sourceImage = getTrimmedImage();
    if (sourceImage == null) {
      sourceImage = AssetStudioUtils.createDummyImage();
    }

    double scaleFactor = Math.min(imageSize.getWidth() * myScaleFactor / sourceImage.getWidth(),
                                  imageSize.getHeight() * myScaleFactor / sourceImage.getHeight());
    int width = roundToInt(sourceImage.getWidth() * scaleFactor);
    int height = roundToInt(sourceImage.getHeight() * scaleFactor);
    BufferedImage scaledImage = AssetUtil.scaledImage(sourceImage, width, height);

    int x = roundToInt( (imageSize.width - width) / 2.);
    int y = roundToInt( (imageSize.height - height) / 2.);
    BufferedImage outImage = AssetUtil.newArgbBufferedImage(imageSize.width, imageSize.height);
    Graphics2D g = (Graphics2D)outImage.getGraphics();
    AssetUtil.Effect[] effects =
        myTint == null || myOpacity == 0 ? NO_EFFECTS : new AssetUtil.FillEffect[] {new AssetUtil.FillEffect(myTint, myOpacity)};
    AssetUtil.drawEffects(g, scaledImage, x, y, effects);

    g.dispose();

    return outImage;
  }

  /**
   * Returns the scale factor.
   */
  public double getScaleFactor() {
    return myScaleFactor;
  }

  /**
   * Returns the scaled image, or null if this object doesn't represent a raster image.
   */
  @Nullable
  public BufferedImage getTrimmedImage() {
    if (myImageFuture == null) {
      return null;
    }
    synchronized (myLock) {
      if (myTrimmedImage == null) {
        try {
          BufferedImage image = myImageFuture.get();
          myTrimmedImage = myIsTrimmed ? AssetStudioUtils.trim(image) : image;
        }
        catch (InterruptedException | ExecutionException e) {
          return null;
        }
      }
      return myTrimmedImage;
    }
  }

  @NotNull
  private Rectangle2D getTrimRectangle(@NotNull String xmlDrawable) {
    if (myTrimRectangle == null) {
      myTrimRectangle = calculateTrimRectangle(xmlDrawable);
    }
    return myTrimRectangle;
  }

  @NotNull
  private Rectangle2D calculateTrimRectangle(@NotNull String xmlDrawable) {
    Future<BufferedImage> futureImage = myContext.renderDrawable(xmlDrawable, myTargetSize);
    Future<Rectangle2D> rectangleFuture = Futures.lazyTransform(futureImage, (BufferedImage image) -> {
      Rectangle bounds = ImageUtils.getCropBounds(image, ImageUtils.TRANSPARENCY_FILTER, null);
      if (bounds == null) {
        return new Rectangle(myTargetSize);  // Do not trim a completely transparent image.
      }
      double width = myTargetSize.getWidth();
      double height = myTargetSize.getHeight();
      return new Rectangle2D.Double(bounds.getX() / width, bounds.getY() / height,
                                    bounds.getWidth() / width, bounds.getHeight() / height);
    });

    try {
      return rectangleFuture.get();
    }
    catch (InterruptedException | ExecutionException e) {
      return new Rectangle(myTargetSize);
    }
  }
}
