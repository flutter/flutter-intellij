/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

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
import com.jetbrains.lang.dart.psi.*;
import com.jetbrains.lang.dart.psi.impl.DartCallExpressionImpl;
import com.jetbrains.lang.dart.util.DartPsiImplUtil;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import gnu.trove.THashSet;
import info.debatty.java.stringsimilarity.JaroWinkler;
import io.flutter.FlutterBundle;
import io.flutter.utils.IconPreviewGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLCompoundValue;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YamlRecursivePsiElementVisitor;

import javax.swing.*;
import java.util.*;

import static io.flutter.dart.DartPsiUtil.*;

// Style note: Normally we put control flow changing statements on a separate line in a block.
// When working with PSI elements nearly everything can return null. There are a lot of null checks
// that could return null, but they seldom trigger, so the return is on the same line as the if statement.
public class FlutterIconLineMarkerProvider extends LineMarkerProviderDescriptor {

  public static final Map<String, Set<String>> KnownPaths = new HashMap<>();
  private static final Logger LOG = Logger.getInstance(FlutterIconLineMarkerProvider.class);

  static {
    initialize();
  }

  public static void initialize() {
    KnownPaths.clear();
    KnownPaths.put("Icons", new THashSet<>(Collections.singleton("packages/flutter/lib/src/material")));
    KnownPaths.put("IconData", new THashSet<>(Collections.singleton("packages/flutter/lib/src/widgets")));
    KnownPaths.put("CupertinoIcons", new THashSet<>(Collections.singleton("packages/flutter/lib/src/cupertino")));
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
    String knownPath = null;

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      // Resolve the class reference and check that it is one of the known, cached classes.
      final PsiElement symbol = "IconData".equals(name) ? refExpr : refExpr.getFirstChild();
      if (!(symbol instanceof DartReference)) return null;
      final PsiElement result = ((DartReference)symbol).resolve();
      if (result == null) return null;
      final List<VirtualFile> library = DartResolveUtil.findLibrary(result.getContainingFile());
      for (VirtualFile file : library) {
        final VirtualFile dir = file.getParent();
        if (dir.isInLocalFileSystem()) {
          final String path = dir.getPath();
          String trimmedPath = path;
          if (!path.endsWith("lib")) {
            final int index = path.indexOf("lib");
            if (index >= 0) {
              trimmedPath = path.substring(0, index + 3);
            }
          }
          final Set<String> knownPaths = KnownPaths.get(name);
          if (knownPaths.contains(path) || knownPaths.contains(trimmedPath)) {
            knownPath = file.getPath();
            break;
          }
          for (String aPath : knownPaths) {
            if (path.endsWith(aPath) || aPath.contains(path) || trimmedPath.endsWith(aPath) || aPath.contains(trimmedPath)) {
              knownPath = file.getPath();
              break;
            }
          }
        }
      }
      if (knownPath == null) return null;
    }

    if (parent.getNode().getElementType() == DartTokenTypes.CALL_EXPRESSION) {
      // Check font family and package
      final DartArguments arguments = DartPsiImplUtil.getArguments((DartCallExpression)parent);
      if (arguments == null) return null;
      final String family = getValueOfNamedArgument(arguments, "fontFamily");
      final PsiElement fontPackage = getNamedArgumentExpression(arguments, "fontPackage");
      final String argument = getValueOfPositionalArgument(arguments, 0);
      if (argument == null) return null;
      final Icon icon;
      if (family == null || "MaterialIcons".equals(family)) {
        icon = getIconFromCode(argument);
      }
      else {
        icon = getIconFromPackage(fontPackage, family, argument);
      }
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
          final PsiElement iconElement = refExpr.getLastChild();
          if (iconElement == null) return null; // TODO check for instance creation with codepoint
          final String iconName = iconElement.getText();
          final IconInfo iconDef = findDefinition(name, iconName, element.getProject(), knownPath);
          if (iconDef == null) return null;
          icon = findIconFromDef(name, iconDef, knownPath);
        }
        if (icon != null) {
          return createLineMarker(element, icon);
        }
      }
    }
    return null;
  }

  // TODO Implement this if we ever support the package flutter_icons.
  // That package is not currently supported because it takes forever to analyze it.
  private Icon getIconFromPackage(PsiElement aPackage, String family, String argument) {
    if (aPackage == null || family == null || argument == null) {
      return null;
    }
    final int code;
    try {
      code = parseLiteralNumber(argument);
    }
    catch (NumberFormatException ignored) {
      return null;
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
    //noinspection MissingRecentApi
    return new LineMarkerInfo<>(element, element.getTextRange(), icon, null, null,
                                GutterIconRenderer.Alignment.LEFT, () -> "");
  }

  private IconInfo findDefinition(@NotNull String className, @NotNull String iconName, @NotNull Project project, @NotNull String path) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null) return null;
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (psiFile == null) {
      return null;
    }
    final IconInfoVisitor visitor = new IconInfoVisitor(iconName);
    psiFile.accept(visitor);
    return visitor.info;
  }

  private Icon findIconFromDef(@NotNull String iconClassName, @NotNull IconInfo iconDef, @NotNull String path) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null) return null;
    VirtualFile parent = virtualFile;
    while (parent != null && !parent.getName().equals("lib")) {
      parent = parent.getParent();
    }
    if (parent != null) parent = parent.getParent(); // We have to search the entire project.
    if (parent == null) {
      return null;
    }
    final List<VirtualFile> ttfFiles = new ArrayList<>();
    VfsUtilCore.visitChildrenRecursively(parent, new VirtualFileVisitor<>() {
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
    double match = -1;
    final String family = iconDef.familyName;
    VirtualFile bestFileMatch = null;
    if (family != null) {
      for (VirtualFile file : ttfFiles) {
        final double n = findPattern(file.getNameWithoutExtension(), family);
        if (n > match) {
          match = n;
          bestFileMatch = file;
        }
      }
    } // If the family is null we could do a search for font files named similar to the package.
    if (bestFileMatch != null) {
      final IconPreviewGenerator generator = new IconPreviewGenerator(bestFileMatch.getPath());
      final Icon icon = generator.convert(iconDef.codepoint);
      if (icon != null) return icon;
    }
    for (VirtualFile file : ttfFiles) {
      final IconPreviewGenerator generator = new IconPreviewGenerator(file.getPath());
      final Icon icon = generator.convert(iconDef.codepoint);
      if (icon != null) return icon;
    }
    return null;
  }

  public double findPattern(@NotNull String t, @NotNull String p) {
    // This is from https://github.com/tdebatty/java-string-similarity
    // It's MIT license file is: https://github.com/tdebatty/java-string-similarity/blob/master/LICENSE.md
    final JaroWinkler jw = new JaroWinkler();
    return jw.similarity(t, p);
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

    private String findFamilyName(@Nullable PsiElement expression, @Nullable DartType type) {
      if (expression == null && type != null) {
        LOG.info("Check superclass constructor for font family: " + type.getName());
        return null; // TODO Check superclass of <type> for a constructor that includes the family.
      }
      else if (expression instanceof DartStringLiteralExpression) {
        final Pair<String, TextRange> pair = DartPsiImplUtil.getUnquotedDartStringAndItsRange(expression.getText().trim());
        return pair.first;
      }
      else if (expression != null && expression.getNode().getElementType() == DartTokenTypes.REFERENCE_EXPRESSION) {
        final Pair<String, TextRange> pair = DartPsiImplUtil.getUnquotedDartStringAndItsRange(expression.getText().trim());
        final String varName = pair.first;
        return staticVars.get(varName);
      }
      return null;
    }

    @Override
    public void visitVarAccessDeclaration(@NotNull DartVarAccessDeclaration o) {
      if (o.getComponentName().getText().trim().equals(iconName)) {
        final DartVarInit init = (DartVarInit)o.getParent().getLastChild();
        final DartExpression expression = init.getExpression();
        String className = null;
        DartArguments arguments = null;
        DartType type = null;
        if (expression instanceof DartNewExpression) {
          final DartNewExpression newExpr = (DartNewExpression)expression;
          type = newExpr.getType();
          if (type != null) {
            className = type.getText();
            arguments = newExpr.getArguments();
          }
        }
        else if (expression instanceof DartCallExpression) {
          // The Dart parser sometimes generates a call expression where we expect a new expression.
          final DartCallExpressionImpl callExpr = (DartCallExpressionImpl)expression;
          arguments = callExpr.getArguments();
          className = callExpr.getExpression().getText();
        }
        if (KnownPaths.containsKey(className)) {
          if (arguments != null) {
            final DartArgumentList argumentList = arguments.getArgumentList();
            if (argumentList != null) {
              final List<DartExpression> list = argumentList.getExpressionList();
              if (!list.isEmpty()) {
                final String codepoint = list.get(0).getText();
                final PsiElement family = getNamedArgumentExpression(arguments, "fontFamily");
                final String familyName = findFamilyName(family, type);
                info = new IconInfo(className, iconName, familyName, codepoint);
              }
            }
          }
        }
      }
      else {
        if (o.getFirstChild().getText().trim().equals("static")) {
          // Fortunately, in all packages checked so far, static variables defining font family and
          // package names appear at the beginning of the file. So this simple visitor works, but it
          // will fail if the static variables are defined at the end of the file.
          final String varName = o.getComponentName().getText().trim();
          final DartVarInit init = (DartVarInit)o.getParent().getLastChild();
          final DartExpression expression = init.getExpression();
          if (expression instanceof DartStringLiteralExpression) {
            final Pair<String, TextRange> pair = DartPsiImplUtil.getUnquotedDartStringAndItsRange(expression.getText());
            staticVars.put(varName, pair.first);
          }
        }
      }
    }
  }

  @Deprecated // This might be useful if we eliminate the preference pane that defines packages to analyze.
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
