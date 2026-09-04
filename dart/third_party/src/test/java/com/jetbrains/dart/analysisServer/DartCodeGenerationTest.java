// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.dart.analysisServer;

import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.generation.BaseDartGenerateHandler;
import com.jetbrains.lang.dart.ide.generation.DartGenerateEqualsAndHashcodeHandler;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;

// TODO(https://github.com/flutter/dart-intellij-third-party/issues/51)
@Ignore
public class DartCodeGenerationTest extends CodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    DartAnalysisServerService.getInstance(getProject()).serverReadyForRequest();
  }

  protected void doEqualsAndHashcodeTest(@NotNull final String before, @NotNull final String after) {
    doTest(before, after, new DartGenerateEqualsAndHashcodeHandler());
  }

  protected void doTest(@NotNull final String before, @NotNull final String after, @NotNull final BaseDartGenerateHandler anAction) {
    myFixture.configureByText("foo.dart", before);
    anAction.invoke(getProject(), getEditor(), getFile());
    myFixture.checkResult(after);
  }

  /**
   * Note: These tests were recently updated to match the expected output, but should the code formatter used by the
   * Dart Analysis server change, these tests will start to break again. They are updated to match with the assumption
   * that the equals and hashcode tests include testing features of Intellij and the Dart plugin, not just the
   * formatting returned from the server.
   */
  public void testEqualsAndHashcodeNoSuper() {
    doEqualsAndHashcodeTest("""
                              class Interface {
                                bool operator ==(Object other) => super == other;
                                int get hashCode => super.hashCode;
                              }
                              class Bar {}
                              class Baz implements Interface {}
                              class Foo extends Bar with Baz implements Interface {<caret>}
                              """,

                            """
                             class Interface {
                               bool operator ==(Object other) => super == other;
                               int get hashCode => super.hashCode;
                             }
                             class Bar {}
                             class Baz implements Interface {}
                             class Foo extends Bar with Baz implements Interface {
                               @override
                               bool operator ==(Object other) =>
                                   identical(this, other) ||
                                   other is Foo && runtimeType == other.runtimeType;
                                    
                               @override
                               int get hashCode => 0;
                             }
                             """);
  }

  public void testEqualsAndHashcodeWithSuper() {
    doEqualsAndHashcodeTest("""
                              class Bar extends Baz {}
                              class Baz {
                                bool operator ==(Object other) => super == other;
                                int get hashCode => super.hashCode;
                              }
                              class Foo extends Bar {<caret>}""",

                            """
                             class Bar extends Baz {}
                             class Baz {
                               bool operator ==(Object other) => super == other;
                               int get hashCode => super.hashCode;
                             }
                             class Foo extends Bar {
                               @override
                               bool operator ==(Object other) =>
                                   identical(this, other) ||
                                   other is Foo && runtimeType == other.runtimeType;
                            
                               @override
                               int get hashCode => 0;
                             }""");
  }

  public void testEqualsAndHashcodeWithFieldsNoSuper() {
    doEqualsAndHashcodeTest("""
                              class Interface {
                                bool operator ==(Object other) => super == other;
                                int get hashCode => super.hashCode;
                              }
                              class Foo extends Object implements Interface {
                                Error e;
                                bool b;
                              <caret>}""",

                            """
                                class Interface {
                                  bool operator ==(Object other) => super == other;
                                  int get hashCode => super.hashCode;
                                }
                                class Foo extends Object implements Interface {
                                  Error e;
                                  bool b;
                                
                                  @override
                                  bool operator ==(Object other) =>
                                      identical(this, other) ||
                                      other is Foo &&
                                          runtimeType == other.runtimeType &&
                                          e == other.e &&
                                          b == other.b;
                                
                                  @override
                                  int get hashCode => Object.hash(e, b);
                                }""");
  }

  public void testEqualsAndHashcodeWithFieldsAndSuper() {
    doEqualsAndHashcodeTest("""
                              class Bar extends Baz {var qwe;}
                              class Baz {
                                bool operator ==(Object other) => super == other;
                                int get hashCode => super.hashCode;
                              }
                              class Foo extends Bar {
                                Error e;
                                bool b;<caret>
                              }""",

                            """
                                class Bar extends Baz {var qwe;}
                                class Baz {
                                  bool operator ==(Object other) => super == other;
                                  int get hashCode => super.hashCode;
                                }
                                class Foo extends Bar {
                                  Error e;
                                  bool b;
                                
                                  @override
                                  bool operator ==(Object other) =>
                                      identical(this, other) ||
                                      other is Foo &&
                                          runtimeType == other.runtimeType &&
                                          e == other.e &&
                                          b == other.b;
                                
                                  @override
                                  int get hashCode => Object.hash(e, b);
                                }""");
  }
}
