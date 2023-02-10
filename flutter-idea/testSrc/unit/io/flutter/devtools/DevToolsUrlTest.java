package io.flutter.devtools;

import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.sdk.FlutterSdkVersion;
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

    FlutterSdkVersion newVersion = new FlutterSdkVersion("3.3.0");
    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=IntelliJ-IDEA&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, null, null, newVersion, mockSdkUtil)).getUrlString()
    );

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=IntelliJ-IDEA&embed=true&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, true, null, null, newVersion, mockSdkUtil)).getUrlString()
    );

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, null, null, false, null, null, newVersion, mockSdkUtil).getUrlString())
    );

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=IntelliJ-IDEA&backgroundColor=ffffff&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, "ffffff", null, newVersion, mockSdkUtil).getUrlString())
    );

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=IntelliJ-IDEA&backgroundColor=ffffff&fontSize=12.0&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, "ffffff", 12.0f, newVersion, mockSdkUtil).getUrlString())
    );

    when(mockSdkUtil.getFlutterHostEnvValue()).thenReturn("Android-Studio");

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=Android-Studio&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, null, null, newVersion, mockSdkUtil).getUrlString())
    );

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=Android-Studio&backgroundColor=3c3f41&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, "3c3f41", null, newVersion, mockSdkUtil).getUrlString())
    );

    FlutterSdkVersion oldVersion = new FlutterSdkVersion("3.0.0");
    assertEquals(
      "http://127.0.0.1:9100/#/?ide=Android-Studio&page=timeline&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      (new DevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, null, null, oldVersion, mockSdkUtil)).getUrlString()
    );
  }
}