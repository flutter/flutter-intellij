package io.flutter;

import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase;
import io.flutter.util.FlutterTestUtils;

abstract public class FlutterCodeInsightFixtureTestCase extends DartCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return FlutterTestUtils.BASE_TEST_DATA_PATH + getBasePath();
  }
}
