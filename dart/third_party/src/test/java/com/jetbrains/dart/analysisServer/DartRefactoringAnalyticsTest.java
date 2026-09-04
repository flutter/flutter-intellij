package com.jetbrains.dart.analysisServer;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.lang.dart.analytics.AnalyticsConstants;
import com.jetbrains.lang.dart.analytics.LegacyRefactoringData;
import com.jetbrains.lang.dart.ide.refactoring.ServerExtractMethodRefactoring;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.jetbrains.annotations.NotNull;

public class DartRefactoringAnalyticsTest extends CodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    DartTestUtils.configureDartSdk(myModule, myFixture.getTestRootDisposable(), true);
    myFixture.setTestDataPath(DartTestUtils.BASE_TEST_DATA_PATH + getBasePath());
  }

  @Override
  protected String getBasePath() {
    return "/analysisServer/refactoring/extract/method";
  }

  @NotNull
  private ServerExtractMethodRefactoring createRefactoring(String filePath) {
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
    final PsiFile psiFile = myFixture.configureByFile(filePath);
    myFixture.doHighlighting();
    final SelectionModel selectionModel = getEditor().getSelectionModel();
    int offset = selectionModel.getSelectionStart();
    final int length = selectionModel.getSelectionEnd() - offset;
    return new ServerExtractMethodRefactoring(getProject(), psiFile.getVirtualFile(), offset, length);
  }

  public void testExtractMethodDefaultAnalytics() {
    ServerExtractMethodRefactoring refactoring = createRefactoring("MethodAll.dart");
    RefactoringStatus initial = refactoring.checkInitialConditions();
    assertNotNull(initial);
    assertTrue(initial.isOK());
    String[] names = refactoring.getNames();
    assertTrue(names.length > 0);
    refactoring.setName(names[0]);
    RefactoringStatus finalCond = refactoring.checkFinalConditions();
    assertNotNull(finalCond);
    assertTrue(finalCond.isOK());

    LegacyRefactoringData data = refactoring.collectAnalyticsData();
    assertEquals(true, data.getData().get(AnalyticsConstants.EXTRACT_ALL.getName()));
    assertEquals(false, data.getData().get(AnalyticsConstants.CREATE_GETTER.getName()));
    assertEquals(false, data.getData().get(AnalyticsConstants.CUSTOM_NAME.getName()));
  }

  public void testExtractMethodNonDefaultAnalytics() {
    ServerExtractMethodRefactoring refactoring = createRefactoring("MethodGetter.dart");
    RefactoringStatus initial = refactoring.checkInitialConditions();
    assertNotNull(initial);
    assertTrue(initial.isOK());
    refactoring.setName("customFunctionName");
    refactoring.setCreateGetter(true);
    RefactoringStatus finalCond = refactoring.checkFinalConditions();
    assertNotNull(finalCond);
    assertTrue(finalCond.isOK());

    LegacyRefactoringData data = refactoring.collectAnalyticsData();
    assertEquals(true, data.getData().get(AnalyticsConstants.EXTRACT_ALL.getName()));
    assertEquals(true, data.getData().get(AnalyticsConstants.CREATE_GETTER.getName()));
    assertEquals(true, data.getData().get(AnalyticsConstants.CUSTOM_NAME.getName()));
  }
}
