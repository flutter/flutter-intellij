package io.flutter.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("HttpUrlsUsage")
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
    assertEquals(
        "Open <a href=\"https://secure.com\">https://secure.com</a>",
        UrlUtils.generateHtmlFragmentWithHrefTags("Open https://secure.com"));
    assertEquals(
        "<a href=\"http://start.com\">http://start.com</a> at start",
        UrlUtils.generateHtmlFragmentWithHrefTags("http://start.com at start"));
  }

  @Test
  public void testNoScheme() {
    // Verify that we don't accidentally linkify things without scheme if that's the
    // desired behavior (usually it is for this util)
    assertEquals("google.com", UrlUtils.generateHtmlFragmentWithHrefTags("google.com"));
  }
}
