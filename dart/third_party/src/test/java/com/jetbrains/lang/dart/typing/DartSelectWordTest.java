package com.jetbrains.lang.dart.typing;

import com.intellij.codeInsight.editorActions.SelectWordHandler;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.lang.dart.util.DartTestUtils;

/**
 * Test the Dart word selection functionality.
 * <p>
 * This class tests the Dart word selection functionality, which is used to select words and blocks of code in the editor.
 */
public class DartSelectWordTest extends BasePlatformTestCase {

    // Added setUp to make sure the files that the test needs are available during tests
    //
    @Override
    protected void setUp() throws Exception {
      super.setUp();
      DartTestUtils.configureDartSdk(getModule(), myFixture.getProjectDisposable(), false);
    }
  private void doTest(@NotNull final String before, final String @NotNull ... after) {
    myFixture.configureByText("file.dart", before);
    final DataContext dataContext = DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent());
    final SelectWordHandler handler = new SelectWordHandler(null);
    for (String text : after) {
      handler.execute(myFixture.getEditor(), myFixture.getEditor().getCaretModel().getCurrentCaret(), dataContext);
      myFixture.checkResult(text);
    }
  }

  public void testLineDocComment() {
    doTest("/// foo bar<caret> baz",
           "/// foo <selection>bar</selection><caret> baz",
           "/// <selection>foo bar<caret> baz</selection>",
           "<selection>/// foo bar<caret> baz</selection>");
  }

  public void testExpressionAndSemicolon() {
    doTest("main() {\n  pri<caret>nt  \n  (1);  \n}",
           "main() {\n  <selection>pri<caret>nt</selection>  \n  (1);  \n}",
           "main() {\n<selection>  pri<caret>nt  \n</selection>  (1);  \n}",
           "main() {\n<selection>  pri<caret>nt  \n  (1);</selection>  \n}",
           "main() {\n<selection>  pri<caret>nt  \n  (1);  \n</selection>}",
           "main() <selection>{\n  pri<caret>nt  \n  (1);  \n}</selection>",
           "<selection>main() {\n  pri<caret>nt  \n  (1);  \n}</selection>");
  }
}
