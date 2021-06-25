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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.AstBufferUtil;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.psi.DartArgumentList;
import com.jetbrains.lang.dart.psi.DartArguments;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartExpression;
import com.jetbrains.lang.dart.psi.DartNewExpression;
import com.jetbrains.lang.dart.psi.DartRecursiveVisitor;
import com.jetbrains.lang.dart.psi.DartReference;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import com.jetbrains.lang.dart.psi.DartType;
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration;
import com.jetbrains.lang.dart.psi.DartVarInit;
import com.jetbrains.lang.dart.util.DartPsiImplUtil;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import io.flutter.FlutterBundle;
import io.flutter.utils.IconPreviewGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLCompoundValue;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YamlRecursivePsiElementVisitor;

public class FlutterIconLineMarkerProvider extends LineMarkerProviderDescriptor {

  public static final Map<String, String> KnownPaths = new HashMap<>();
  private static final Logger LOG = Logger.getInstance(FlutterIconLineMarkerProvider.class);

  static {
    KnownPaths.put("Icons", "packages/flutter/lib/src/material");
    KnownPaths.put("IconData", "packages/flutter/lib/src/widgets");
    KnownPaths.put("CupertinoIcons", "packages/flutter/lib/src/cupertino");
  }

  @Nullable("null means disabled")
  @Override
  public @GutterName String getName() {
    return FlutterBundle.message("flutter.icon.preview.title");
  }

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    if (element.getNode().getElementType() != DartTokenTypes.IDENTIFIER) return null;

    final String name = element.getText();
    if (!KnownPaths.containsKey(name)) return null;

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
          final String knownPath = KnownPaths.get(name);
          if (path.endsWith(knownPath) || knownPath.contains(path)) {
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
        else if (name.equals("CupertinoIcons")) {
          icon = FlutterCupertinoIcons.getIconForName(selector);
        }
        else {
          // Note: I want to keep this code until I'm sure we won't use pubspec.yaml.
          //final DartComponent result = DartResolveUtil.findReferenceAndComponentTarget(idNode);
          //if (result != null) {
          //  final VirtualFile map = IconPreviewGenerator.findAssetMapFor(result);
          //  if (map == null) {
          //    return null;
          //  }
          //  final FileViewProvider provider = PsiManager.getInstance(result.getProject()).findViewProvider(map);
          //  if (provider != null) {
          //    final PsiFile psi = provider.getPsi(YAMLLanguage.INSTANCE);
          //    final YamlAssetMapVisitor visitor = new YamlAssetMapVisitor();
          //    psi.accept(visitor);
          //    final HashMap<String, String> assetMap = visitor.assetMap;
          //  }
          //}
          final PsiElement symbol = refExpr.getLastChild();
          if (symbol == null) return null; // TODO check for instance creation with codepoint
          final String iconName = symbol.getText();
          final IconInfo iconDef = findDefinition(name, iconName, element.getProject());
          if (iconDef == null) return null;
          icon = findIconFromDef(name, iconDef);
        }
        if (icon != null) {
          return createLineMarker(element, icon);
        }
      }
    }
    return null;
  }

  private Icon getIconFromCode(@NotNull String value) {
    final int code;
    try {
      code = parseLiteralNumber(value);
    }
    catch (NumberFormatException ignored) {
      return null;
    }
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
    return new LineMarkerInfo<>(element, element.getTextRange(), icon, null, null,
                                GutterIconRenderer.Alignment.LEFT, () -> "");
  }

  private IconInfo findDefinition(@NotNull String className, @NotNull String iconName, @NotNull Project project) {
    final String path = KnownPaths.get(className);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null) return null;
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (psiFile == null) {
      return null;
    }
    IconInfoVisitor visitor = new IconInfoVisitor(iconName);
    psiFile.accept(visitor);
    return visitor.info;
  }

  private Icon findIconFromDef(String iconClassName, IconInfo iconDef) {
    final String path = KnownPaths.get(iconClassName);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null) return null;
    final VirtualFile parent = virtualFile.getParent();
    List<VirtualFile> ttfFiles = new ArrayList<>();
    VfsUtilCore.visitChildrenRecursively(parent, new VirtualFileVisitor<Object>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if ("ttf".equals(file.getExtension())) {
          ttfFiles.add(file);
          return false;
        }
        else {
          return super.visitFile(file);
        }
      }
    });
    int match = -1;
    String family = iconDef.familyName;
    VirtualFile bestFileMatch = null;
    if (family == null) family = "FontAwesomeSolid";
    for (VirtualFile file : ttfFiles) {
      int n = findPattern(file.getNameWithoutExtension(), family);
      if (n > match) {
        match = n;
        bestFileMatch = file;
      }
    }
    if (bestFileMatch != null) {
      IconPreviewGenerator generator = new IconPreviewGenerator(bestFileMatch.getPath());
      Icon icon = generator.convert(iconDef.codepoint);
      if (icon != null) return icon;
    }
    for (VirtualFile file : ttfFiles) {
      IconPreviewGenerator generator = new IconPreviewGenerator(file.getPath());
      Icon icon = generator.convert(iconDef.codepoint);
      if (icon != null) return icon;
    }
    return null;
  }

  public int findPattern(String t, String p) {
    // TODO Experiment with https://github.com/tdebatty/java-string-similarity
    return 0;
  }

  static class IconInfo {
    final @NotNull String iconName;
    final @NotNull String className;
    final @Nullable String familyName;
    final @NotNull String codepoint;

    IconInfo(@NotNull String className, @NotNull String iconName, @Nullable String familyName, @NotNull String codepoint) {
      this.className = className;
      this.iconName = iconName;
      this.familyName = familyName;
      this.codepoint = codepoint;
    }
  }

  static class IconInfoVisitor extends DartRecursiveVisitor {
    final HashMap<String, String> staticVars = new HashMap<>();
    final String iconName;
    IconInfo info;

    IconInfoVisitor(String iconName) {
      this.iconName = iconName;
    }

    private String findFamilyName(@Nullable PsiElement expression, @NotNull DartType type) {
      if (expression == null) {
        LOG.info("Check superclass constructor for font family: " + type.getName());
        return null; // TODO Check superclass of <type> for a constructor that includes the family.
      }
      else if (expression instanceof DartStringLiteralExpression) {
        final Pair<String, TextRange> pair = DartPsiImplUtil.getUnquotedDartStringAndItsRange(expression.getText().trim());
        return pair.first;
      }
      else if (expression.getNode().getElementType() == DartTokenTypes.REFERENCE_EXPRESSION) {
        final Pair<String, TextRange> pair = DartPsiImplUtil.getUnquotedDartStringAndItsRange(expression.getText().trim());
        String varName = pair.first;
        return staticVars.get(varName);
      }
      return null;
    }

    @Override
    public void visitVarAccessDeclaration(@NotNull DartVarAccessDeclaration o) {
      if (o.getComponentName().getText().trim().equals(iconName)) {
        DartVarInit init = (DartVarInit)o.getParent().getLastChild();
        final DartExpression expression = init.getExpression();
        if (expression instanceof DartNewExpression) {
          DartNewExpression newExpr = (DartNewExpression)expression;
          DartType type = newExpr.getType();
          if (type != null) {
            final String className = type.getText();
            if (KnownPaths.containsKey(className)) {
              final DartArguments arguments = newExpr.getArguments();
              if (arguments != null) {
                final DartArgumentList argumentList = arguments.getArgumentList();
                if (argumentList != null) {
                  final List<DartExpression> list = argumentList.getExpressionList();
                  if (!list.isEmpty()) {
                    String codepoint = list.get(0).getText();
                    final PsiElement family = getNamedArgumentExpression(arguments, "family");
                    String familyName = findFamilyName(family, type);
                    info = new IconInfo(className, iconName, familyName, codepoint);
                  }
                }
              }
            }
          }
        }
      }
      else {
        if (o.getFirstChild().getText().trim().equals("static")) {
          String varName = o.getComponentName().getText().trim();
          DartVarInit init = (DartVarInit)o.getParent().getLastChild();
          final DartExpression expression = init.getExpression();
          if (expression instanceof DartStringLiteralExpression) {
            final Pair<String, TextRange> pair = DartPsiImplUtil.getUnquotedDartStringAndItsRange(expression.getText());
            staticVars.put(varName, pair.first);
          }
        }
      }
    }
  }

  @Deprecated
  static class YamlAssetMapVisitor extends YamlRecursivePsiElementVisitor {
    HashMap<String, String> assetMap = new HashMap<>();
    List<String> iconClassNames = new ArrayList<>();

    @Override
    public void visitCompoundValue(@NotNull YAMLCompoundValue compoundValue) {
      final PsiElement[] children = compoundValue.getChildren();
      if (children.length == 2 && children[0].getFirstChild().textMatches("asset")) {
        final String fontFilePath = children[0].getLastChild().getText();
        final String className = children[1].getLastChild().getText();
        iconClassNames.add(className);
        assetMap.put(className, fontFilePath);
      }
      else {
        super.visitCompoundValue(compoundValue);
      }
    }

    @Override
    public void visitKeyValue(@NotNull YAMLKeyValue keyValue) {
      if (keyValue.getKeyText().equals("icons")) {
        iconClassNames.add(keyValue.getValueText());
      }
      else {
        super.visitKeyValue(keyValue);
      }
    }
  }
}
