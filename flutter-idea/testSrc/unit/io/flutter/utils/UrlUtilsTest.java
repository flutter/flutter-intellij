package io.flutter.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UrlUtilsTest {
  @Test
  public void testGenerateHtmlFragmentWithHrefTags() {
    assertEquals(
      "Open <a href=\"http://link.com\">http://link.com</a>",
      UrlUtils.generateHtmlFragmentWithHrefTags("Open http://link.com")
    );
    assertEquals("Unchanged text without URLs", UrlUtils.generateHtmlFragmentWithHrefTags("Unchanged text without URLs"));
    assertEquals(
      "Multiple <a href=\"http://link1.com\">http://link1.com</a> links <a href=\"http://link2.com\">http://link2.com</a> test",
      UrlUtils.generateHtmlFragmentWithHrefTags("Multiple http://link1.com links http://link2.com test")
    );
  }
}
