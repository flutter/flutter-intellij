/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This Expression takes the Company Domain (eg: "mycompany.com"), and the Application Name (eg: "My App") and returns a valid Java package
 * name (eg: "com.mycompany.myapp"). Besides reversing the Company Name, taking spaces, a lower casing, it also takes care of
 * invalid java keywords (eg "new", "switch", "if", etc).
 */
// Copied from com.android.tools.idea.npw.project.DomainToPackageExpression, which is now final.
public class DomainToPackageExpression extends Expression<String> {
  private static final Pattern MODULE_NAME_GROUP = Pattern.compile(".*:"); // Anything before ":" belongs to the module parent name
  private static final Pattern DISALLOWED_IN_DOMAIN = Pattern.compile("[^a-zA-Z0-9_]");
  private final StringProperty myCompanyDomain;
  private final StringProperty myApplicationName;

  public DomainToPackageExpression(StringProperty companyDomain, StringProperty applicationName) {
    super(companyDomain, applicationName);
    myCompanyDomain = companyDomain;
    myApplicationName = applicationName;
  }

  @NotNull
  @Override
  public String get() {
    Iterable<String> splitList = Splitter.on('.').split(myCompanyDomain.get());
    final List<String> list = Lists.newArrayList(splitList);
    Collections.reverse(list);
    list.add(myApplicationName.get());

    return list.stream()
      .map(DomainToPackageExpression::nameToJavaPackage)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.joining("."));
  }

  /**
   * Converts the name of a Module, Application or User to a valid java package name segment.
   * Invalid characters are removed, and reserved Java language names are converted to valid values.
   */
  @NotNull
  private static String nameToJavaPackage(@NotNull String name) {
    String res = name.replace('-', '_');
    res = MODULE_NAME_GROUP.matcher(res).replaceAll("");
    res = DISALLOWED_IN_DOMAIN.matcher(res).replaceAll("").toLowerCase(Locale.US);
    if (!res.isEmpty() && AndroidUtils.isReservedKeyword(res) != null) {
      res = StringUtil.fixVariableNameDerivedFromPropertyName(res).toLowerCase(Locale.US);
    }
    return res;
  }
}
