package io.flutter.utils;

import com.intellij.ui.components.labels.LinkListener;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class LabelInputTest {

  @Test
  public void testConstructors() {
    LabelInput input1 = new LabelInput("text");
    assertEquals("text", input1.text);
    assertNull(input1.listener);

    LinkListener<String> listener = (aSource, aLinkData) -> {};
    LabelInput input2 = new LabelInput("text2", listener);
    assertEquals("text2", input2.text);
    assertSame(listener, input2.listener);
  }
}
