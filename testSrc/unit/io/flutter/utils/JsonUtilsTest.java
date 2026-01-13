package io.flutter.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JsonUtilsTest {

  @Test
  public void getStringMember() {
    JsonObject json = JsonParser.parseString("{\"a\": \"b\", \"c\": null, \"d\": 1}").getAsJsonObject();
    assertEquals("b", JsonUtils.getStringMember(json, "a"));
    assertNull(JsonUtils.getStringMember(json, "c"));
    assertNull(JsonUtils.getStringMember(json, "missing"));
    assertEquals("1", JsonUtils.getStringMember(json, "d"));
  }

  @Test
  public void getIntMember() {
    JsonObject json = JsonParser.parseString("{\"a\": 1, \"b\": null, \"c\": \"2\"}").getAsJsonObject();
    assertEquals(1, JsonUtils.getIntMember(json, "a"));
    assertEquals(-1, JsonUtils.getIntMember(json, "b"));
    assertEquals(-1, JsonUtils.getIntMember(json, "missing"));
    assertEquals(2, JsonUtils.getIntMember(json, "c"));
  }

  @Test
  public void getKeySet() {
    JsonObject json = JsonParser.parseString("{\"a\": 1, \"b\": 2}").getAsJsonObject();
    Set<String> keys = JsonUtils.getKeySet(json);
    assertEquals(2, keys.size());
    assertTrue(keys.contains("a"));
    assertTrue(keys.contains("b"));
  }
}
