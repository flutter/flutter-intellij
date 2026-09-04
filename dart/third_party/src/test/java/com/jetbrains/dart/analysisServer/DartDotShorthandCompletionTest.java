package com.jetbrains.dart.analysisServer;

import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.util.DartTestUtils;

public class DartDotShorthandCompletionTest extends DartServerCompletionTest {
  public void testDotNewCompletion() {
    doTest("new",
           """
             class A {
               A();
             }
             void f(A a) {}
             main() {
               f(.<caret>);
             }""");
  }

  public void testDotNamedCompletion() {
    doTest("named",
           """
             class A {
               A.named();
             }
             void f(A a) {}
             main() {
               f(.<caret>);
             }""");
  }

  public void testEnumCompletion() {
    doTest("e1",
           """
             enum E {
               e1, e2
             }
             void f(E e) {}
             main() {
               f(.<caret>);
             }""");
  }

  public void testDotNamedArgCompletion() {
    doTest("name: ",
           """
             class A {
               A.named({String? name});
             }
             void f(A a) {}
             main() {
               f(.named(<caret>));
             }""");
  }

  public void testDotNewArgCompletion() {
    doTest("name: ",
           """
             class A {
               A({String? name});
             }
             void f(A a) {}
             main() {
               f(.new(<caret>));
             }""");
  }

  public void testDotNamedPartialArgCompletion() {
    doTest("name: ",
           """
             class A {
               A.named({String? name});
             }
             void f(A a) {}
             main() {
               f(.named(na<caret>));
             }""");
  }

  public void testDotNewPartialArgCompletion() {
    doTest("name: ",
           """
             class A {
               A({String? name});
             }
             void f(A a) {}
             main() {
               f(.new(na<caret>));
             }""");
  }

  public void testDotNewWithWhitespaceAndCommentsArgCompletion() {
    doTest("name: ",
           """
             class A {
               A({String? name});
             }
             void f(A a) {}
             main() {
               f(.new /* comment */ (<caret>));
             }""");
  }

  public void testPrimaryConstructorDotNewArgCompletion() {
    doTest("name: ",
           """
             class A({String? name});
             void f(A a) {}
             main() {
               f(.new(<caret>));
             }""");
  }

  public void testPrimaryConstructorDotNamedArgCompletion() {
    doTest("name: ",
           """
             class A.named({String? name});
             void f(A a) {}
             main() {
               f(.named(<caret>));
             }""");
  }

  public void testPrimaryConstructorDotNewPartialArgCompletion() {
    doTest("name: ",
           """
             class A({String? name});
             void f(A a) {}
             main() {
               f(.new(na<caret>));
             }""");
  }

  public void testPrimaryConstructorDotNamedPartialArgCompletion() {
    doTest("name: ",
           """
             class A.named({String? name});
             void f(A a) {}
             main() {
               f(.named(na<caret>));
             }""");
  }

  public void testExplicitNewPrimaryConstructorDotNewArgCompletion() {
    doTest("name: ",
           """
             class A.new({String? name});
             void f(A a) {}
             main() {
               f(.new(<caret>));
             }""");
  }

  private void doTest(String lookupToSelect, String text) {
    myFixture.configureByText("foo.dart", text);
    myFixture.doHighlighting();
    myFixture.completeBasic();
    if (lookupToSelect != null) {
      var lookups = myFixture.getLookupElementStrings();
      if (lookups != null) {
        assertTrue("Likely missing completion item: " + lookupToSelect + ". Found: " + lookups, lookups.contains(lookupToSelect));
      } else {
        // When a unique completion suggestion matches the prefix, IntelliJ auto-inserts it without showing a lookup list.
        String editorText = myFixture.getEditor().getDocument().getText();
        assertTrue("Lookup list was null and expected item '" + lookupToSelect + "' was not auto-inserted in editor:\n" + editorText,
                   editorText.contains(lookupToSelect));
      }
    }
  }
}
