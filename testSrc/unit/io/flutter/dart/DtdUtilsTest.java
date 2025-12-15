package io.flutter.dart;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ServiceContainerUtil;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class DtdUtilsTest {
  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  @Mock
  private DartToolingDaemonService mockDtdService;

  private Project project;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    project = fixture.getProject();
    
    // Register mock service
    // We need to use the disposable from the fixture to ensure it gets cleaned up
    ServiceContainerUtil.registerServiceInstance(project, DartToolingDaemonService.class, mockDtdService);
  }

  @Test
  public void testReadyDtdServiceRemovesProjectFromWaiters() throws Exception {
    when(mockDtdService.getWebSocketReady()).thenReturn(true);
    
    DtdUtils dtdUtils = new DtdUtils();
    CompletableFuture<DartToolingDaemonService> future = dtdUtils.readyDtdService(project);
    
    assertTrue(future.isDone());
    // Verify WAITERS map is empty
    assertTrue("WAITERS map should be empty after service is ready", DtdUtils.WAITERS.isEmpty());
  }

  @Test
  public void testWaitersMapIsClearedAfterAsyncCompletion() throws Exception {
    when(mockDtdService.getWebSocketReady()).thenReturn(false);

    DtdUtils dtdUtils = new DtdUtils();
    CompletableFuture<DartToolingDaemonService> future = dtdUtils.readyDtdService(project);

    // It should be in the map now
    assertTrue("Project should be in WAITERS map", DtdUtils.WAITERS.containsKey(project));

    // Simulate service becoming ready
    when(mockDtdService.getWebSocketReady()).thenReturn(true);

    // Wait for the poller to pick it up (it runs every 500ms)
    // We can just wait on the future with a timeout
    future.get(5, java.util.concurrent.TimeUnit.SECONDS);

    assertTrue(future.isDone());

    // Wait for the removal to happen (it happens in whenComplete, which might be slightly delayed if async)
    // We can poll the map
    long start = System.currentTimeMillis();
    while (!DtdUtils.WAITERS.isEmpty() && System.currentTimeMillis() - start < 2000) {
      Thread.sleep(100);
    }

    assertTrue("WAITERS map should be empty after async completion", DtdUtils.WAITERS.isEmpty());
  }
}
