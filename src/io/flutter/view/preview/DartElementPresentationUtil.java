/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view.preview;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LayeredIcon;
import com.jetbrains.lang.dart.DartComponentType;
import com.jetbrains.lang.dart.util.DartPresentableUtil;
import org.dartlang.analysis.server.protocol.Element;
import org.dartlang.analysis.server.protocol.ElementKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.icons.AllIcons.Nodes.*;
import static com.intellij.icons.AllIcons.Nodes.PropertyWrite;

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
  public static Icon getIcon(@NotNull  Element element) {
    final boolean finalOrConst = element.isConst() || element.isFinal();

    switch (element.getKind()) {
      case ElementKind.CLASS:
        return element.isAbstract() ? AbstractClass : Class;
      case ElementKind.CONSTRUCTOR:
        return Method;
      // TODO(scheglov) Enable once minimal version is 2017.3
      //case ElementKind.CONSTRUCTOR_INVOCATION:
      //  return CONSTRUCTOR_INVOCATION_ICON;
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
      // TODO(scheglov) Enable once minimal version is 2017.3
      //case ElementKind.FUNCTION_INVOCATION:
      //  return FUNCTION_INVOCATION_ICON;
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

  @NotNull
  public static String getText(@NotNull Element element) {
    final StringBuilder b = new StringBuilder(element.getName());
    if (!StringUtil.isEmpty(element.getTypeParameters())) {
      b.append(element.getTypeParameters());
    }
    if (!StringUtil.isEmpty(element.getParameters())) {
      b.append(element.getParameters());
    }
    if (!StringUtil.isEmpty(element.getReturnType())) {
      b.append(" ").append(DartPresentableUtil.RIGHT_ARROW).append(" ").append(element.getReturnType());
    }
    return b.toString();
  }
}
