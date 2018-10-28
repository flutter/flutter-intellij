/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.jetbrains.lang.dart.DartComponentType;
import com.jetbrains.lang.dart.util.DartPresentableUtil;
import icons.DartIcons;
import org.dartlang.analysis.server.protocol.Element;
import org.dartlang.analysis.server.protocol.ElementKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.icons.AllIcons.Nodes.*;
import static com.intellij.icons.AllIcons.Nodes.Class;
import static com.intellij.icons.AllIcons.Nodes.Enum;
import static com.intellij.icons.AllIcons.RunConfigurations.Junit;

/**
 * Most of the class is copied from Dart Plugin.
 * https://github.com/JetBrains/intellij-plugins/blob/master/Dart/src/com/jetbrains/lang/dart/ide/structure/DartStructureViewElement.java
 */
public class DartElementPresentationUtil {
  private static final LayeredIcon STATIC_FINAL_FIELD_ICON = new LayeredIcon(Field, StaticMark, FinalMark);
  private static final LayeredIcon FINAL_FIELD_ICON = new LayeredIcon(Field, FinalMark);
  private static final LayeredIcon STATIC_FIELD_ICON = new LayeredIcon(Field, StaticMark);
  private static final LayeredIcon STATIC_METHOD_ICON = new LayeredIcon(Method, StaticMark);
  private static final LayeredIcon TOP_LEVEL_FUNCTION_ICON = new LayeredIcon(Function, StaticMark);
  private static final LayeredIcon TOP_LEVEL_VAR_ICON = new LayeredIcon(Variable, StaticMark);
  private static final LayeredIcon CONSTRUCTOR_INVOCATION_ICON = new LayeredIcon(Class, TabPin);
  private static final LayeredIcon FUNCTION_INVOCATION_ICON = new LayeredIcon(Method, TabPin);
  private static final LayeredIcon TOP_LEVEL_CONST_ICON = new LayeredIcon(Variable, StaticMark, FinalMark);

  @Nullable
  public static Icon getIcon(@NotNull Element element) {
    final boolean finalOrConst = element.isConst() || element.isFinal();

    switch (element.getKind()) {
      case ElementKind.CLASS:
        return element.isAbstract() ? AbstractClass : Class;
      case "MIXIN":
        // TODO(devoncarew): Use ElementKind.MIXIN when its available.
        return AbstractClass;
      case ElementKind.CONSTRUCTOR:
        return Method;
      case ElementKind.CONSTRUCTOR_INVOCATION:
        return CONSTRUCTOR_INVOCATION_ICON;
      case ElementKind.ENUM:
        return Enum;
      case ElementKind.ENUM_CONSTANT:
        return STATIC_FINAL_FIELD_ICON;
      case ElementKind.FIELD:
        if (finalOrConst && element.isTopLevelOrStatic()) return STATIC_FINAL_FIELD_ICON;
        if (finalOrConst) return FINAL_FIELD_ICON;
        if (element.isTopLevelOrStatic()) return STATIC_FIELD_ICON;
        return Field;
      case ElementKind.FUNCTION:
        return element.isTopLevelOrStatic() ? TOP_LEVEL_FUNCTION_ICON : Function;
      case ElementKind.FUNCTION_INVOCATION:
        return FUNCTION_INVOCATION_ICON;
      case ElementKind.FUNCTION_TYPE_ALIAS:
        return DartComponentType.TYPEDEF.getIcon();
      case ElementKind.GETTER:
        return element.isTopLevelOrStatic() ? PropertyReadStatic : PropertyRead;
      case ElementKind.METHOD:
        if (element.isAbstract()) return AbstractMethod;
        return element.isTopLevelOrStatic() ? STATIC_METHOD_ICON : Method;
      case ElementKind.SETTER:
        return element.isTopLevelOrStatic() ? PropertyWriteStatic : PropertyWrite;
      case ElementKind.TOP_LEVEL_VARIABLE:
        return finalOrConst ? TOP_LEVEL_CONST_ICON : TOP_LEVEL_VAR_ICON;
      case ElementKind.UNIT_TEST_GROUP:
        return Junit;
      case ElementKind.UNIT_TEST_TEST:
        return DartIcons.TestNode;

      case ElementKind.CLASS_TYPE_ALIAS:
      case ElementKind.COMPILATION_UNIT:
      case ElementKind.FILE:
      case ElementKind.LABEL:
      case ElementKind.LIBRARY:
      case ElementKind.LOCAL_VARIABLE:
      case ElementKind.PARAMETER:
      case ElementKind.PREFIX:
      case ElementKind.TYPE_PARAMETER:
      case ElementKind.UNKNOWN:
      default:
        return null; // unexpected
    }
  }

  public static void renderElement(@NotNull Element element, @NotNull OutlineTreeCellRenderer renderer, boolean nameInBold) {
    final SimpleTextAttributes attributes =
      nameInBold ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;

    renderer.appendSearch(element.getName(), attributes);

    if (!StringUtil.isEmpty(element.getTypeParameters())) {
      renderer.appendSearch(element.getTypeParameters(), attributes);
    }
    if (!StringUtil.isEmpty(element.getParameters())) {
      renderer.appendSearch(element.getParameters(), attributes);
    }
    if (!StringUtil.isEmpty(element.getReturnType())) {
      renderer.append(" ");
      renderer.append(DartPresentableUtil.RIGHT_ARROW);
      renderer.append(" ");
      renderer.appendSearch(element.getReturnType(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
