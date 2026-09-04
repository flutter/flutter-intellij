package com.jetbrains.lang.dart.injection;

import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.ParsingTestCase;
import com.jetbrains.lang.dart.util.DartTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;

/**
 * Test the Dart language injection functionality.
 * <p>
 * This class tests the Dart language injection functionality, which is used to inject other languages, such as HTML, into Dart string literals.
 */
public class DartInjectionTest extends LightPlatformCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return DartTestUtils.BASE_TEST_DATA_PATH + "/injection/";
  }

  private void doTest() {
    configureByFile(getTestName(false) + ".dart");
    ParsingTestCase.doCheckResult(getTestDataPath(), getTestName(false) + "." + "txt", toParseTreeText(getFile()));
  }

  private String toParseTreeText(PsiFile file) {
    return DebugUtil.psiToString(file, true, false, (psiElement, consumer) -> InjectedLanguageManager
      .getInstance(getProject()).enumerate(psiElement, (injectedPsi, places) -> consumer.consume(injectedPsi)));
  }

  public void testHtmlInStrings() throws Exception {
    doTest();
  }
}
