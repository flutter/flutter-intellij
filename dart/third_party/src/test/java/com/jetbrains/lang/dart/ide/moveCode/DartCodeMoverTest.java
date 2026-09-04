package com.jetbrains.lang.dart.ide.moveCode;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import com.jetbrains.lang.dart.util.DartTestUtils;

/**
 * The base class for the Dart code mover tests.
 * <p>
 * This class provides the core testing infrastructure for the Dart code mover actions.
 */
abstract public class DartCodeMoverTest extends DartCodeInsightFixtureTestCase {

  // Added setUp to make sure the files that the test needs are available during tests
  //
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(getModule(), myFixture.getProjectDisposable(), false);
  }
  protected void doTest() {
    final String testName = getTestName(false);
    myFixture.configureByFile(testName + ".dart");
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION);
    myFixture.checkResultByFile(testName + "_afterUp.dart", true);

    FileDocumentManager.getInstance().reloadFromDisk(myFixture.getDocument(myFixture.getFile()));
    myFixture.configureByFile(testName + ".dart");
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
    myFixture.checkResultByFile(testName + "_afterDown.dart", true);
  }
}
