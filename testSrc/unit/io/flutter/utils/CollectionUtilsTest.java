package io.flutter.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CollectionUtilsTest {

  @Test
  public void anyMatch() {
    assertTrue(CollectionUtils.anyMatch(new String[]{"a", "b"}, s -> s.equals("a")));
    assertFalse(CollectionUtils.anyMatch(new String[]{"a", "b"}, s -> s.equals("c")));

    assertTrue(CollectionUtils.anyMatch(Arrays.asList("a", "b"), s -> s.equals("a")));
    assertFalse(CollectionUtils.anyMatch(Arrays.asList("a", "b"), s -> s.equals("c")));
  }

  @Test
  public void filter() {
    List<String> result = CollectionUtils.filter(new String[]{"a", "b", "c"}, s -> s.equals("a") || s.equals("c"));
    assertEquals(2, result.size());
    assertEquals("a", result.get(0));
    assertEquals("c", result.get(1));

    result = CollectionUtils.filter(Arrays.asList("a", "b", "c"), s -> s.equals("b"));
    assertEquals(1, result.size());
    assertEquals("b", result.get(0));
  }
}
