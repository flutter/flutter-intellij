/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npwOld.project;

import com.android.tools.idea.npwOld.model.NewProjectModel;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * This Expression takes the Company Domain (eg: "mycompany.com"), and the Application Name (eg: "My App") and returns a valid Java package
 * name (eg: "com.mycompany.myapp"). Besides reversing the Company Name, taking spaces, a lower casing, it also takes care of
 * invalid java keywords (eg "new", "switch", "if", etc).
 */
public class DomainToPackageExpression extends Expression<String> {
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
      .map(NewProjectModel::toPackagePart)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.joining("."));
  }
}
