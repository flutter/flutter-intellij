// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.lang.dart.navigation;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.ide.DartSymbolContributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Test the Dart "go to symbol" functionality.
 * <p>
 * This class tests the {@link com.jetbrains.lang.dart.ide.DartSymbolContributor} class, which is responsible for providing the symbols that are displayed in the "go to symbol" list.
 */
public class DartGoToSymbolTest extends DartCodeInsightFixtureTestCase {

  private void assertNamesFound(ChooseByNameContributor contributor, String... names) {
    Collection<String> actual = new ArrayList<>();
    for (String name : names) {
      for (NavigationItem item : contributor.getItemsByName(name, "", getProject(), false)) {
        actual.add(item.getName());
      }
    }
    assertSameElements(actual, names);
  }

  public void testGoToSymbol() {
    myFixture.addFileToProject("foo.dart",
                               """
                                 fooBarBaz1() {}
                                 class fooBarBaz2 {
                                   var fooBarBaz3;
                                   fooBarBaz4(){}
                                 }
                                 mixin fooBarBaz5 {
                                   var fooBarBaz6;
                                   fooBarBaz7(){}
                                 }
                                 """);

    DartSymbolContributor contributor = new DartSymbolContributor();
    List<String> allNames = new ArrayList<>();
    contributor.processNames(allNames::add, GlobalSearchScope.allScope(getProject()), null);
    assertFalse(allNames.contains("fooBarBaz0"));
    assertFalse(allNames.contains("FooBarBaz1"));
    assertFalse(allNames.contains("fooBarBaz8"));
    assertNamesFound(contributor, "fooBarBaz1", "fooBarBaz2", "fooBarBaz3", "fooBarBaz4", "fooBarBaz5", "fooBarBaz6", "fooBarBaz7");
  }

  public void testGoToSymbolMalformedClass() {
    myFixture.addFileToProject("malformed.dart",
                               """
                               class {
                                 int x;
                               }
                               class <caret>Foo {
                                 int y;
                               }
                               """);

    DartSymbolContributor contributor = new DartSymbolContributor();
    // This will trigger indexing and should not throw a NullPointerException.
    // See: https://github.com/flutter/dart-intellij-third-party/issues/375
    List<String> names = new ArrayList<>();
    contributor.processNames(names::add, GlobalSearchScope.allScope(getProject()), null);
    assertTrue(names.contains("Foo"));
  }

  public void testGoToSymbolPrimaryConstructors() {
    myFixture.addFileToProject("constructors.dart",
                               """
                               class Foo1 {
                                 new();
                               }
                               class Foo2 {
                                 Foo2.new();
                               }
                               class Foo3 {
                                 new named();
                               }
                               """);

    DartSymbolContributor contributor = new DartSymbolContributor();
    List<String> names = new ArrayList<>();
    contributor.processNames(names::add, GlobalSearchScope.allScope(getProject()), null);
    assertTrue(names.contains("Foo1"));
    assertTrue(names.contains("Foo2"));
    assertTrue(names.contains("named"));
  }
}
