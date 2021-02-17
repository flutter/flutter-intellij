/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import static io.flutter.dart.DartPsiUtil.getNamedArgumentExpression;
import static io.flutter.dart.DartPsiUtil.getNewExprFromType;
import static io.flutter.dart.DartPsiUtil.getValueOfNamedArgument;
import static io.flutter.dart.DartPsiUtil.getValueOfPositionalArgument;
import static io.flutter.dart.DartPsiUtil.parseLiteralNumber;
import static io.flutter.dart.DartPsiUtil.topmostReferenceExpression;

import com.intellij.codeInsight.daemon.GutterName;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.AstBufferUtil;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.psi.DartArguments;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartNewExpression;
import com.jetbrains.lang.dart.psi.DartReference;
import com.jetbrains.lang.dart.util.DartPsiImplUtil;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import io.flutter.FlutterBundle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterIconLineMarkerProvider extends LineMarkerProviderDescriptor {

  static final private Map<String, String> KnownPaths = new HashMap<>();
  static {
    KnownPaths.put("Icons", "packages/flutter/lib/src/material");
    KnownPaths.put("IconData", "packages/flutter/lib/src/widgets");
    KnownPaths.put("CupertinoIcons", "packages/flutter/lib/src/cupertino");
  }

  @Nullable("null means disabled")
  @Override
  public @GutterName String getName() {
    return FlutterBundle.message("fluitter.icon.preview.title");
  }

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    if (element.getNode().getElementType() != DartTokenTypes.IDENTIFIER) return null;

    final String name = element.getText();
    if (!(name.equals("Icons") || name.equals("CupertinoIcons") || name.equals("IconData"))) return null;

    final PsiElement refExpr = topmostReferenceExpression(element);
    if (refExpr == null) return null;
    PsiElement parent = refExpr.getParent();
    if (parent == null) return null;

    // Resolve the class reference and check that it is one of the known, cached classes.
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      final PsiElement symbol = "IconData".equals(name) ? refExpr : refExpr.getFirstChild();
      if (!(symbol instanceof DartReference)) return null;
      final PsiElement result = ((DartReference)symbol).resolve();
      if (result == null) return null;
      final List<VirtualFile> library = DartResolveUtil.findLibrary(result.getContainingFile());
      boolean found = false;
      for (VirtualFile file : library) {
        VirtualFile dir = file.getParent();
        if (dir.isInLocalFileSystem()) {
          final String path = dir.getPath();
          if (path.endsWith(KnownPaths.get(name))) {
            found = true;
            break;
          }
        }
      }
      if (!found) return null;
    }

    if (parent.getNode().getElementType() == DartTokenTypes.CALL_EXPRESSION) {
      // Check font family and package
      final DartArguments arguments = DartPsiImplUtil.getArguments((DartCallExpression)parent);
      if (arguments == null) return null;
      final String family = getValueOfNamedArgument(arguments, "fontFamily");
      if (family != null) {
        // TODO https://github.com/flutter/flutter-intellij/issues/2334
        if (!"MaterialIcons".equals(family)) return null;
      }
      final PsiElement fontPackage = getNamedArgumentExpression(arguments, "fontPackage");
      if (fontPackage != null) return null; // See previous TODO
      final String argument = getValueOfPositionalArgument(arguments, 0);
      if (argument == null) return null;
      final Icon icon = getIconFromCode(argument);
      if (icon != null) {
        return createLineMarker(element, icon);
      }
    }
    else if (parent.getNode().getElementType() == DartTokenTypes.SIMPLE_TYPE) {
      parent = getNewExprFromType(parent);
      if (parent == null) return null;
      final DartArguments arguments = DartPsiImplUtil.getArguments((DartNewExpression)parent);
      if (arguments == null) return null;
      final String argument = getValueOfPositionalArgument(arguments, 0);
      if (argument == null) return null;
      final Icon icon = getIconFromCode(argument);
      if (icon != null) {
        return createLineMarker(element, icon);
      }
    }
    else {
      final PsiElement idNode = refExpr.getFirstChild();
      if (idNode == null) return null;
      if (name.equals(idNode.getText())) {
        final PsiElement selectorNode = refExpr.getLastChild();
        if (selectorNode == null) return null;
        final String selector = AstBufferUtil.getTextSkippingWhitespaceComments(selectorNode.getNode());
        final Icon icon;
        if (name.equals("Icons")) {
          icon = FlutterMaterialIcons.getIconForName(selector);
        }
        else {
          icon = FlutterCupertinoIcons.getIconForName(selector);
        }
        if (icon != null) {
          return createLineMarker(element, icon);
        }
      }
    }
    return null;
  }

  private Icon getIconFromCode(@NotNull String value) {
    final int code = parseLiteralNumber(value);
    final String hex = Long.toHexString(code);
    // We look for the codepoint for material icons, and fall back on those for Cupertino.
    Icon icon = FlutterMaterialIcons.getIconForHex(hex);
    if (icon == null) {
      icon = FlutterCupertinoIcons.getIconForHex(hex);
    }
    return icon;
  }

  private LineMarkerInfo<PsiElement> createLineMarker(@Nullable PsiElement element, @NotNull Icon icon) {
    if (element == null) return null;
    return new LineMarkerInfo<>(element, element.getTextRange(), icon, null, null, GutterIconRenderer.Alignment.LEFT);
  }
}
