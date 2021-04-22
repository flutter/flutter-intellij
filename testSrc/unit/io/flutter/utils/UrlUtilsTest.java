package io.flutter.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UrlUtilsTest {
    @Test
    public void testAddUrlTags() {
        assertEquals(
                UrlUtils.addUrlTags("Open http://link.com"),
                "Open <a href=\"http://link.com\">http://link.com</a>"
        );
        assertEquals(UrlUtils.addUrlTags("Unchanged text without URLs"), "Unchanged text without URLs");
        assertEquals(
                UrlUtils.addUrlTags("Multiple http://link1.com links http://link2.com test"),
                "Multiple <a href=\"http://link1.com\">http://link1.com</a> links <a href=\"http://link2.com\">http://link2.com</a> test"
        );
    }
}
