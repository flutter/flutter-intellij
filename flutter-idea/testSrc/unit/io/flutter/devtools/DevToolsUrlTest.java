package io.flutter.devtools;

import io.flutter.sdk.FlutterSdkUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DevToolsUrlTest {
  @Test
  public void testGetUrlString() {
    final String devtoolsHost = "127.0.0.1";
    final int devtoolsPort = 9100;
    final String serviceProtocolUri = "http://127.0.0.1:50224/WTFTYus3IPU=/";
    final String page = "timeline";

    final FlutterSdkUtil mockSdkUtil = mock(FlutterSdkUtil.class);
    when(mockSdkUtil.getFlutterHostEnvValue()).thenReturn("IntelliJ-IDEA");

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA&page=timeline&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, null, null, mockSdkUtil)).getUrlString()
    );

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA&page=timeline&embed=true&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, true, null, null, mockSdkUtil)).getUrlString()
    );

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, null, null, false, null, null, mockSdkUtil).getUrlString())
    );

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA&page=timeline&backgroundColor=ffffff&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, "ffffff", null, mockSdkUtil).getUrlString())
    );

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA&page=timeline&backgroundColor=ffffff&fontSize=12.0&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, "ffffff", 12.0f, mockSdkUtil).getUrlString())
    );

    when(mockSdkUtil.getFlutterHostEnvValue()).thenReturn("Android-Studio");

    assertEquals(
      "http://127.0.0.1:9100/?ide=Android-Studio&page=timeline&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, null, null, mockSdkUtil).getUrlString())
    );

    assertEquals(
      "http://127.0.0.1:9100/?ide=Android-Studio&page=timeline&backgroundColor=3c3f41&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, "3c3f41", null, mockSdkUtil).getUrlString())
    );
  }
}