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
package com.android.tools.idea.npw.assetstudio

import com.android.annotations.VisibleForTesting
import com.google.common.base.Verify.verify
import com.intellij.util.ui.JBUI
import java.awt.Image

/**
 * Utility for proportionally scaling a collection of images so that none of them
 * exceeds a given height constraint. When possible (depending on the height
 * constraint), the images will be scaled so that none of their heights is smaller
 * than that of the shortest image used to construct the scaler.
 */
class ProportionalImageScaler private constructor(private val minHeight: Int, private val maxHeight: Int) {
  /**
   * Returns the given image, scaled relative to the other images so that its height does not
   * exceed [maxAllowedHeight]. The given [image] should be one of the images used to create
   * the scaler.
   */
  fun scale(image: Image, maxAllowedHeight: Int): Image {
    if (maxAllowedHeight < 0) {
      throw IllegalArgumentException("Height constraint $maxAllowedHeight is invalid because image heights can't be negative.")
    }

    val width = image.getWidth(null)
    val height = image.getHeight(null)

    if (height == 0 || maxHeight < maxAllowedHeight) return image

    val scaleFactor = determineScaleFactor(height, minHeight, maxHeight, maxAllowedHeight)

    val newWidth = JBUI.scale((width * scaleFactor).toFloat()).toInt()
    val newHeight = JBUI.scale((height * scaleFactor).toFloat()).toInt()
    return image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
  }

  companion object {
    @JvmStatic
    fun forImages(images: Collection<Image>): ProportionalImageScaler {
      var minHeight = Int.MAX_VALUE
      var maxHeight = 0

      for (image in images) {
        val height = image.getHeight(null)
        minHeight = minOf(minHeight, height)
        maxHeight = maxOf(maxHeight, height)
      }

      return ProportionalImageScaler(minHeight, maxHeight)
    }
  }
}

@VisibleForTesting
fun determineScaleFactor(height: Int, minHeight: Int, maxHeight: Int, maxAllowedHeight: Int): Double {
  verify(height != 0)
  verify(maxHeight != 0)

  // If all images are the same height or we can't use minHeight as a lower bound because it's larger
  // than what's allowed, then just downscale everything by the same factor so that the largest
  // image satisfies the height constraint.
  if (maxHeight == minHeight || maxAllowedHeight <= minHeight) {
    return maxAllowedHeight / maxHeight.toDouble()
  }

  // From minHeight <= height <= maxObservedHeight, interpolate to minHeight <= scaledHeight <= maxAllowedHeight
  val heightDeltaRatio = (height - minHeight) / (maxHeight - minHeight).toDouble()
  val maxAllowedHeightDelta = maxAllowedHeight - minHeight

  val scaledHeight = minHeight + (maxAllowedHeightDelta * heightDeltaRatio)
  return scaledHeight / height
}
