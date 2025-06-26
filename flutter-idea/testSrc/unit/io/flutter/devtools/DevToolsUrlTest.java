package io.flutter.devtools;

import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.sdk.FlutterSdkVersion;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DevToolsUrlTest {
  final String devtoolsHost = "127.0.0.1";
  final int devtoolsPort = 9100;
  final String serviceProtocolUri = "http://127.0.0.1:50224/WTFTYus3IPU=/";
  final String page = "timeline";
  final FlutterSdkUtil mockSdkUtil = mock(FlutterSdkUtil.class);
  final WorkspaceCache notBazelWorkspaceCache = mock(WorkspaceCache.class);
  final WorkspaceCache bazelWorkspaceCache = mock(WorkspaceCache.class);
  FlutterSdkVersion newVersion = new FlutterSdkVersion("3.3.0");

  @Before
  public void setUp() {
    when(mockSdkUtil.getFlutterHostEnvValue()).thenReturn("IntelliJ-IDEA");

    when(notBazelWorkspaceCache.isBazel()).thenReturn(false);

    when(bazelWorkspaceCache.isBazel()).thenReturn(true);
  }

  @Test
  public void testGetUrlStringWithoutColor() {
    final DevToolsUtils noColorUtils = mock(DevToolsUtils.class);
    when(noColorUtils.getColorHexCode()).thenReturn(null);
    when(noColorUtils.getIsBackgroundBright()).thenReturn(null);

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=IntelliJ-IDEA&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      new DevToolsUrl.Builder()
        .setDevToolsHost(devtoolsHost)
        .setDevToolsPort(devtoolsPort)
        .setVmServiceUri(serviceProtocolUri)
        .setFlutterSdkVersion(newVersion)
        .setPage(page)
        .setWorkspaceCache(notBazelWorkspaceCache)
        .setFlutterSdkUtil(mockSdkUtil)
        .setDevToolsUtils(noColorUtils)
        .build()
        .getUrlString()
    );

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=IntelliJ-IDEA&embed=true&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      new DevToolsUrl.Builder()
        .setDevToolsHost(devtoolsHost)
        .setDevToolsPort(devtoolsPort)
        .setVmServiceUri(serviceProtocolUri)
        .setPage(page)
        .setEmbed(true)
        .setFlutterSdkVersion(newVersion)
        .setWorkspaceCache(notBazelWorkspaceCache)
        .setFlutterSdkUtil(mockSdkUtil)
        .setDevToolsUtils(noColorUtils)
        .build()
        .getUrlString()
    );

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA",
      new DevToolsUrl.Builder()
        .setDevToolsHost(devtoolsHost)
        .setDevToolsPort(devtoolsPort)
        .setFlutterSdkVersion(newVersion)
        .setWorkspaceCache(notBazelWorkspaceCache)
        .setFlutterSdkUtil(mockSdkUtil)
        .setDevToolsUtils(noColorUtils)
        .build()
        .getUrlString()
    );

    when(mockSdkUtil.getFlutterHostEnvValue()).thenReturn("Android-Studio");

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=Android-Studio&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      new DevToolsUrl.Builder()
        .setDevToolsHost(devtoolsHost)
        .setDevToolsPort(devtoolsPort)
        .setVmServiceUri(serviceProtocolUri)
        .setPage(page)
        .setFlutterSdkVersion(newVersion)
        .setWorkspaceCache(notBazelWorkspaceCache)
        .setFlutterSdkUtil(mockSdkUtil)
        .setDevToolsUtils(noColorUtils)
        .build()
        .getUrlString()
    );

    // No Flutter SDK version set
    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=Android-Studio&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      new DevToolsUrl.Builder()
        .setDevToolsHost(devtoolsHost)
        .setDevToolsPort(devtoolsPort)
        .setVmServiceUri(serviceProtocolUri)
        .setPage(page)
        .setWorkspaceCache(notBazelWorkspaceCache)
        .setFlutterSdkUtil(mockSdkUtil)
        .setDevToolsUtils(noColorUtils)
        .build()
        .getUrlString()
    );

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=Android-Studio&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      new DevToolsUrl.Builder()
        .setDevToolsHost(devtoolsHost)
        .setDevToolsPort(devtoolsPort)
        .setPage(page)
        .setVmServiceUri(serviceProtocolUri)
        .setWorkspaceCache(bazelWorkspaceCache)
        .setFlutterSdkUtil(mockSdkUtil)
        .setDevToolsUtils(noColorUtils)
        .build()
        .getUrlString()
    );

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=Android-Studio&ideFeature=toolWindow&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      new DevToolsUrl.Builder()
        .setDevToolsHost(devtoolsHost)
        .setDevToolsPort(devtoolsPort)
        .setVmServiceUri(serviceProtocolUri)
        .setPage(page)
        .setIdeFeature(DevToolsIdeFeature.TOOL_WINDOW)
        .setWorkspaceCache(bazelWorkspaceCache)
        .setFlutterSdkUtil(mockSdkUtil)
        .setDevToolsUtils(noColorUtils)
        .build()
        .getUrlString()
    );

  }

  @Test
  public final void testGetUrlStringWithColor() {
    final DevToolsUtils lightUtils = mock(DevToolsUtils.class);
    when(lightUtils.getColorHexCode()).thenReturn("ffffff");
    when(lightUtils.getIsBackgroundBright()).thenReturn(true);

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=IntelliJ-IDEA&backgroundColor=ffffff&theme=light&embed=true&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      new DevToolsUrl.Builder()
        .setDevToolsHost(devtoolsHost)
        .setDevToolsPort(devtoolsPort)
        .setVmServiceUri(serviceProtocolUri)
        .setEmbed(true)
        .setPage(page)
        .setFlutterSdkVersion(newVersion)
        .setWorkspaceCache(notBazelWorkspaceCache)
        .setFlutterSdkUtil(mockSdkUtil)
        .setDevToolsUtils(lightUtils)
        .build()
        .getUrlString()
    );

    when(mockSdkUtil.getFlutterHostEnvValue()).thenReturn("Android-Studio");

    final DevToolsUtils darkUtils = mock(DevToolsUtils.class);
    when(darkUtils.getColorHexCode()).thenReturn("3c3f41");
    when(darkUtils.getIsBackgroundBright()).thenReturn(false);

    assertEquals(
      "http://127.0.0.1:9100/timeline?ide=Android-Studio&backgroundColor=3c3f41&theme=dark&embed=true&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F",
      new DevToolsUrl.Builder()
        .setDevToolsHost(devtoolsHost)
        .setDevToolsPort(devtoolsPort)
        .setVmServiceUri(serviceProtocolUri)
        .setEmbed(true)
        .setPage(page)
        .setFlutterSdkVersion(newVersion)
        .setWorkspaceCache(notBazelWorkspaceCache)
        .setFlutterSdkUtil(mockSdkUtil)
        .setDevToolsUtils(darkUtils)
        .build()
        .getUrlString()
    );
  }
}