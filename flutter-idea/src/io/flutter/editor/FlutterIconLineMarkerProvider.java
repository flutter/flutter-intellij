/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.daemon.GutterName;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.lang.ASTNode;
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
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.IconPreviewGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.util.*;

import static io.flutter.dart.DartPsiUtil.*;

// Style note: Normally we put control flow changing statements on a separate line in a block.
// When working with PSI elements nearly everything can return null. There are a lot of null checks
// that could return null, but they seldom trigger, so the return is on the same line as the if statement.
public class FlutterIconLineMarkerProvider extends LineMarkerProviderDescriptor {

  public static final Map<String, Set<String>> KnownPaths = new HashMap<>();
  private static final Map<String, String> BuiltInPaths = new HashMap<>();
  private static final Logger LOG = Logger.getInstance(FlutterIconLineMarkerProvider.class);
  private static final String MaterialRelativeAssetPath = "/bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf";
  private static final String MaterialRelativeIconsPath = "/packages/flutter/lib/src/material/icons.dart";
  private static final String CupertinoRelativeAssetPath = "/assets/CupertinoIcons.ttf";
  private static final String CupertinoRelativeIconsPath = "/packages/flutter/lib/src/cupertino/icons.dart";

  static {
    initialize();
  }

  public static void initialize() {
    KnownPaths.clear();
    KnownPaths.put("Icons", new THashSet<>(Collections.singleton("packages/flutter/lib/src/material")));
    KnownPaths.put("IconData", new THashSet<>(Collections.singleton("packages/flutter/lib/src/widgets")));
    KnownPaths.put("CupertinoIcons", new THashSet<>(Collections.singleton("packages/flutter/lib/src/cupertino")));
    BuiltInPaths.clear();
    BuiltInPaths.put("Icons", MaterialRelativeIconsPath);
    BuiltInPaths.put("IconData", MaterialRelativeIconsPath);
    BuiltInPaths.put("CupertinoIcons", CupertinoRelativeIconsPath);
  }

  @Nullable("null means disabled")
  @Override
  public @GutterName String getName() {
    return FlutterBundle.message("flutter.icon.preview.title");
  }

  @Override
  @Nullable
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(element.getProject());
    return sdk == null ? null : getLineMarkerInfo(element, sdk);
  }

  @VisibleForTesting
  @Nullable
  LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element, @NotNull FlutterSdk sdk) {
    if ((element.getNode() != null ? element.getNode().getElementType() : null) != DartTokenTypes.IDENTIFIER) return null;

    final String name = element.getText();
    assert name != null;
    if (!KnownPaths.containsKey(name)) return null;

    final PsiElement refExpr = topmostReferenceExpression(element);
    if (refExpr == null) return null;
    PsiElement parent = refExpr.getParent();
    if (parent == null) return null;
    String knownPath = null;

    assert ApplicationManager.getApplication() != null;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      // Resolve the class reference and check that it is one of the known, cached classes.
      final PsiElement symbol = "IconData".equals(name) ? refExpr : refExpr.getFirstChild();
      if (!(symbol instanceof DartReference)) return null;
      final PsiElement result = ((DartReference)symbol).resolve();
      if (result == null) return null;
      assert result.getContainingFile() != null;
      final List<VirtualFile> library = DartResolveUtil.findLibrary(result.getContainingFile());
      for (VirtualFile file : library) {
        assert file != null;
        final VirtualFile dir = file.getParent();
        assert dir != null;
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
          assert knownPaths != null; // Due to guard clause above.
          if (knownPaths.contains(path) || knownPaths.contains(trimmedPath)) {
            knownPath = file.getPath();
            break;
          }
          for (String aPath : knownPaths) {
            assert aPath != null;
            if (path.endsWith(aPath) || aPath.contains(path) || trimmedPath.endsWith(aPath) || aPath.contains(trimmedPath)) {
              knownPath = file.getPath();
              break;
            }
          }
        }
      }
      if (knownPath == null) return null;
    }

    final ASTNode parentNode = parent.getNode();
    assert parentNode != null;
    if (parentNode.getElementType() == DartTokenTypes.CALL_EXPRESSION) {
      // Check font family and package
      final DartArguments arguments = DartPsiImplUtil.getArguments((DartCallExpression)parent);
      if (arguments == null) return null;
      final String family = getValueOfNamedArgument(arguments, "fontFamily");
      final PsiElement fontPackage = getNamedArgumentExpression(arguments, "fontPackage");
      final String argument = getValueOfPositionalArgument(arguments, 0);
      if (argument == null) return null;
      final Icon icon = getIconFromPackage(fontPackage, family, argument, element.getProject(), sdk);
      if (icon != null) {
        return createLineMarker(element, icon);
      }
    }
    else if (parentNode.getElementType() == DartTokenTypes.SIMPLE_TYPE) {
      parent = getNewExprFromType(parent);
      if (parent == null) return null;
      final DartArguments arguments = DartPsiImplUtil.getArguments((DartNewExpression)parent);
      if (arguments == null) return null;
      final String family = getValueOfNamedArgument(arguments, "fontFamily");
      final PsiElement fontPackage = getNamedArgumentExpression(arguments, "fontPackage");
      final String argument = getValueOfPositionalArgument(arguments, 0);
      if (argument == null) return null;
      final Icon icon = getIconFromPackage(fontPackage, family, argument, element.getProject(), sdk);
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
        assert selectorNode.getNode() != null;
        final String selector = AstBufferUtil.getTextSkippingWhitespaceComments(selectorNode.getNode());
        final Icon icon;
        if (name.equals("Icons")) {
          final IconInfo iconDef = findStandardDefinition(name, selector, element.getProject(), knownPath, sdk);
          if (iconDef == null) return null;
          // <flutter-sdk>/bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf
          icon = findStandardIconFromDef(name, iconDef, sdk.getHomePath() + MaterialRelativeAssetPath);
        }
        else if (name.equals("CupertinoIcons")) {
          final IconInfo iconDef = findStandardDefinition(name, selector, element.getProject(), knownPath, sdk);
          if (iconDef == null) return null;
          final String path = FlutterSdkUtil.getPathToCupertinoIconsPackage(element.getProject());
          // <pub_cache>/hosted/pub.dartlang.org/cupertino_icons-v.m.n/assets/CupertinoIcons.ttf
          icon = findStandardIconFromDef(name, iconDef, path + CupertinoRelativeAssetPath);
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
          assert iconName != null;
          assert knownPath != null;
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

  @Nullable
  private IconInfo findStandardDefinition(@NotNull String className, @NotNull String iconName, @NotNull Project project, @Nullable String path, @NotNull FlutterSdk sdk) {
    if (path != null) {
      return findDefinition(className, iconName, project, path);
    }
    assert Objects.requireNonNull(ApplicationManager.getApplication()).isUnitTestMode();
    return findDefinition(className, iconName, project, sdk.getHomePath() + BuiltInPaths.get(className));
  }

  @Nullable
  private Icon findStandardIconFromDef(@NotNull String name, @NotNull IconInfo iconDef, @NotNull String path) {
    assert LocalFileSystem.getInstance() != null;
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile == null) return null;
    final IconPreviewGenerator generator = new IconPreviewGenerator(virtualFile.getPath());
    return generator.convert(iconDef.codepoint);
  }

  // Note: package flutter_icons is not currently supported because it takes forever to analyze it.
  @Nullable
  private Icon getIconFromPackage(@Nullable PsiElement aPackage, @Nullable String family, @NotNull String argument, @NotNull Project project, @NotNull FlutterSdk sdk) {
    final int code;
    try {
      code = parseLiteralNumber(argument);
    }
    catch (NumberFormatException ignored) {
      return null;
    }
    family = family == null ? "MaterialIcons" : family;
    if (aPackage == null) {
      // Looking for IconData with no package -- package specification not currently supported.
      final String relativeAssetPath = family.equals("MaterialIcons") ? MaterialRelativeAssetPath : CupertinoRelativeAssetPath;
      // TODO Base path is wrong for cupertino -- is there a test for this branch (IconData with cupertino family)?
      final IconPreviewGenerator generator = new IconPreviewGenerator(sdk.getHomePath() + relativeAssetPath);
      return generator.convert(code);
    }
    return null;
  }

  @Nullable
  private LineMarkerInfo<PsiElement> createLineMarker(@Nullable PsiElement element, @NotNull Icon icon) {
    if (element == null) return null;
    assert element.getTextRange() != null;
    //noinspection MissingRecentApi
    return new LineMarkerInfo<>(element, element.getTextRange(), icon, null, null,
                                GutterIconRenderer.Alignment.LEFT, () -> "");
  }

  @Nullable
  private IconInfo findDefinition(@NotNull String className, @NotNull String iconName, @NotNull Project project, @NotNull String path) {
    assert LocalFileSystem.getInstance() != null;
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

  @Nullable
  private Icon findIconFromDef(@NotNull String iconClassName, @NotNull IconInfo iconDef, @NotNull String path) {
    assert LocalFileSystem.getInstance() != null;
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
    VfsUtilCore.visitChildrenRecursively(parent, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        final String ext = file.getExtension();
        if ("ttf".equals(ext)) {
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
        assert file != null;
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
      assert file != null;
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

    @Nullable
    private String findFamilyName(@Nullable PsiElement expression, @Nullable DartType type) {
      if (expression == null && type != null) {
        LOG.info("Check superclass constructor for font family: " + type.getName());
        return null; // TODO Check superclass of <type> for a constructor that includes the family.
      }
      else if (expression instanceof DartStringLiteralExpression) {
        //noinspection ConstantConditions
        final Pair<String, TextRange> pair = DartPsiImplUtil.getUnquotedDartStringAndItsRange(expression.getText().trim());
        return pair.first;
      }
      else {
        if (expression != null) {
          assert expression.getNode() != null;
          if (expression.getNode().getElementType() == DartTokenTypes.REFERENCE_EXPRESSION) {
            assert expression.getText() != null;
            final Pair<String, TextRange> pair = DartPsiImplUtil.getUnquotedDartStringAndItsRange(expression.getText().trim());
            final String varName = pair.first;
            return staticVars.get(varName);
          }
        }
      }
      return null;
    }

    @Override
    public void visitVarAccessDeclaration(@NotNull DartVarAccessDeclaration o) {
      if (Objects.requireNonNull(o.getComponentName().getText()).trim().equals(iconName)) {
        assert o.getParent() != null;
        final DartVarInit init = (DartVarInit)o.getParent().getLastChild();
        assert init != null;
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
                final DartExpression dartExpression = list.get(0);
                assert dartExpression != null;
                final String codepoint = dartExpression.getText();
                final PsiElement family = getNamedArgumentExpression(arguments, "fontFamily");
                final String familyName = findFamilyName(family, type);
                assert className != null;
                assert codepoint != null;
                info = new IconInfo(className, iconName, familyName, codepoint);
              }
            }
          }
        }
      }
      else {
        final PsiElement firstChild = o.getFirstChild();
        assert firstChild != null;
        assert firstChild.getText() != null;
        if (firstChild.getText().trim().equals("static")) {
          // Fortunately, in all packages checked so far, static variables defining font family and
          // package names appear at the beginning of the file. So this simple visitor works, but it
          // will fail if the static variables are defined at the end of the file.
          final String varName = Objects.requireNonNull(o.getComponentName().getText()).trim();
          assert o.getParent() != null;
          final DartVarInit init = (DartVarInit)o.getParent().getLastChild();
          assert init != null;
          final DartExpression expression = init.getExpression();
          if (expression instanceof DartStringLiteralExpression) {
            assert expression.getText() != null;
            final Pair<String, TextRange> pair = DartPsiImplUtil.getUnquotedDartStringAndItsRange(expression.getText());
            staticVars.put(varName, pair.first);
          }
        }
      }
    }
  }

  //@Deprecated // This might be useful if we eliminate the preference pane that defines packages to analyze.
  //static class YamlAssetMapVisitor extends YamlRecursivePsiElementVisitor {
  //  final HashMap<String, String> assetMap = new HashMap<>();
  //  final List<String> iconClassNames = new ArrayList<>();
  //
  //  @Override
  //  public void visitCompoundValue(@NotNull YAMLCompoundValue compoundValue) {
  //    final PsiElement[] children = compoundValue.getChildren();
  //    if (children.length == 2 && Objects.requireNonNull(children[0].getFirstChild()).textMatches("asset")) {
  //      assert children[0].getLastChild() != null;
  //      final String fontFilePath = children[0].getLastChild().getText();
  //      assert children[1] != null;
  //      assert children[1].getLastChild() != null;
  //      final String className = children[1].getLastChild().getText();
  //      iconClassNames.add(className);
  //      assetMap.put(className, fontFilePath);
  //    }
  //    else {
  //      super.visitCompoundValue(compoundValue);
  //    }
  //  }
  //
  //  @Override
  //  public void visitKeyValue(@NotNull YAMLKeyValue keyValue) {
  //    if (keyValue.getKeyText().equals("icons")) {
  //      iconClassNames.add(keyValue.getValueText());
  //    }
  //    else {
  //      super.visitKeyValue(keyValue);
  //    }
  //  }
  //}
}
