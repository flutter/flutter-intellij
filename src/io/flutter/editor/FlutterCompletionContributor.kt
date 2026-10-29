/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.EmptyIcon
import io.flutter.dart.DartPsiUtil
import javax.swing.Icon

class FlutterCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val refExp = DartPsiUtil.topmostReferenceExpression(parameters.position) ?: return
    if (refExp.firstChild == refExp.lastChild) return

    val prefix = refExp.firstChild?.text
    if (prefix !in SUPPORTED_TYPES) return

    result.runRemainingContributors(parameters) { otherResult ->
      val lookupString = otherResult.lookupElement.lookupString
      val icon = findIcon(prefix, lookupString)
      if (icon != null) {
        val decorated = FlutterIconLookupElementDecorator(otherResult.lookupElement, icon)
        result.passResult(otherResult.withLookupElement(decorated))
      }
      else {
        result.passResult(otherResult)
      }
    }
  }

  private class FlutterIconLookupElementDecorator(
    delegate: LookupElement,
    private val icon: Icon,
  ) : LookupElementDecorator<LookupElement>(delegate) {
    override fun renderElement(presentation: LookupElementPresentation) {
      super.renderElement(presentation)
      applyIcon(presentation)
    }

    override fun getExpensiveRenderer(): LookupElementRenderer<out LookupElement>? {
      val delegateRenderer = delegate.expensiveRenderer ?: return null
      return object : LookupElementRenderer<FlutterIconLookupElementDecorator>() {
        override fun renderElement(
          element: FlutterIconLookupElementDecorator,
          presentation: LookupElementPresentation,
        ) {
          @Suppress("UNCHECKED_CAST")
          (delegateRenderer as LookupElementRenderer<LookupElement>).renderElement(element.delegate, presentation)
          applyIcon(presentation)
        }
      }
    }

    private fun applyIcon(presentation: LookupElementPresentation) {
      presentation.setTypeText("", icon)
      presentation.isTypeIconRightAligned = true
    }
  }

  companion object {
    private const val ICON_SIZE = 16
    private val EMPTY_ICON: Icon = EmptyIcon.create(ICON_SIZE)
    private val SUPPORTED_TYPES = setOf("Colors", "CupertinoColors", "Icons", "CupertinoIcons")

    @JvmStatic
    fun findIcon(declaringType: String?, name: String?): Icon? {
      if (name == null) return null
      return when (declaringType) {
        "Colors" -> FlutterColors.getColor(name)?.let { ColorIcon(ICON_SIZE, it.getAWTColor()) }
        "CupertinoColors" -> FlutterCupertinoColors.getColor(name)?.let { ColorIcon(ICON_SIZE, it.getAWTColor()) }
        "Icons" -> FlutterMaterialIcons.getIconForName(name) ?: EMPTY_ICON
        "CupertinoIcons" -> FlutterCupertinoIcons.getIconForName(name) ?: EMPTY_ICON
        else -> null
      }
    }
  }
}