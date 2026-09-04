package com.jetbrains.dart.analysisServer;

import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.util.DartTestUtils;

public class DartDotShorthandTest extends DartServerResolverTest {
  public void testDotNew() {
    doTest(myFixture,
           """
             class A {
               A();
             }
             void f(A a) {}
             main() {
               f(.<caret expected='file.dart -> A -> A'>new());
             }""");
  }

  public void testDotNamed() {
    doTest(myFixture,
           """
             class A {
               A.named();
             }
             void f(A a) {}
             main() {
               f(.<caret expected='file.dart -> A -> named'>named());
             }""");
  }
  
  public void testEnum() {
    doTest(myFixture,
           """
             enum E {
               e1, e2
             }
             void f(E e) {}
             main() {
               f(.<caret expected='file.dart -> E -> e1'>e1);
             }""");
  }
}
