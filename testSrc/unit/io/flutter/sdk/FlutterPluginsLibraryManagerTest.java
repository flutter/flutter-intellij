/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import io.flutter.FlutterProjectDisposable;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FlutterPluginsLibraryManager}.
 */
public class FlutterPluginsLibraryManagerTest {
  private final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();
  private final TestDir tmp = new TestDir();

  @Rule
  public final TestRule chain = RuleChain.outerRule(fixture).around(tmp);

  private static final class RecordingPluginsLibraryManager extends FlutterPluginsLibraryManager {
    final AtomicInteger scheduleCount = new AtomicInteger();

    RecordingPluginsLibraryManager(@NotNull Project project) {
      super(project);
    }

    @Override
    void scheduleUpdate() {
      scheduleCount.incrementAndGet();
    }
  }

  @Test
  public void startWatchingBindsBothListenersToFlutterProjectDisposable() {
    final Project mockProject = mock(Project.class);
    final FlutterProjectDisposable projectDisposable = new FlutterProjectDisposable();
    final MessageBus mockBus = mock(MessageBus.class);
    final MessageBusConnection mockConnection = mock(MessageBusConnection.class);

    when(mockProject.getService(FlutterProjectDisposable.class)).thenReturn(projectDisposable);
    when(mockProject.getMessageBus()).thenReturn(mockBus);
    when(mockBus.connect(projectDisposable)).thenReturn(mockConnection);

    final RecordingPluginsLibraryManager manager = new RecordingPluginsLibraryManager(mockProject);
    manager.startWatching();

    final ArgumentCaptor<BulkFileListener> vfsCaptor = ArgumentCaptor.forClass(BulkFileListener.class);
    final ArgumentCaptor<ModuleRootListener> rootCaptor = ArgumentCaptor.forClass(ModuleRootListener.class);
    verify(mockBus).connect(projectDisposable);
    verify(mockConnection).subscribe(eq(VirtualFileManager.VFS_CHANGES), vfsCaptor.capture());
    verify(mockConnection).subscribe(eq(ModuleRootListener.TOPIC), rootCaptor.capture());
    assertEquals(1, manager.scheduleCount.get());

    rootCaptor.getValue().rootsChanged(mock(ModuleRootEvent.class));
    assertEquals(2, manager.scheduleCount.get());
  }

  @Test
  public void startWatchingReactsToLiveVfsEventsAndModuleRoots() throws Exception {
    final Project project = fixture.getProject();
    assertNotNull(project);

    final RecordingPluginsLibraryManager manager = new RecordingPluginsLibraryManager(project);
    manager.startWatching();
    assertEquals(1, manager.scheduleCount.get());

    // File creation triggers update.
    tmp.writeFile("pkg/package_config.json", "{}");
    final int afterCreate = manager.scheduleCount.get();
    assertTrue("Expected VFS file creation to schedule update", afterCreate > 1);

    // Content modification triggers update.
    tmp.writeFile("pkg/package_config.json", "{\"configVersion\": 2}");
    final int afterModify = manager.scheduleCount.get();
    assertEquals(afterCreate + 1, afterModify);

    // File deletion triggers update.
    tmp.deleteFile("pkg/package_config.json");
    final int afterDelete = manager.scheduleCount.get();
    assertEquals(afterModify + 1, afterDelete);

    // ModuleRootListener.TOPIC triggers update (fired inside a write action and paired with
    // beforeRootsChange to satisfy platform root-change invariants on the shared light project).
    final ModuleRootEvent rootEvent = mock(ModuleRootEvent.class);
    final ModuleRootListener publisher = project.getMessageBus().syncPublisher(ModuleRootListener.TOPIC);
    Testing.runInWriteAction(() -> {
      publisher.beforeRootsChange(rootEvent);
      publisher.rootsChanged(rootEvent);
    });
    assertEquals(afterDelete + 1, manager.scheduleCount.get());
  }

  @Test
  public void onVfsChangesFiltersEventTypesFileSystemsAndCoalescesBatches() {
    final VirtualFileSystem localFs = mock(VirtualFileSystem.class);
    final VirtualFileSystem otherFs = mock(VirtualFileSystem.class);

    final VirtualFile localFile = mock(VirtualFile.class);
    when(localFile.getFileSystem()).thenReturn(localFs);

    final VirtualFile jarFile = mock(VirtualFile.class);
    when(jarFile.getFileSystem()).thenReturn(otherFs);

    final RecordingPluginsLibraryManager manager =
      new RecordingPluginsLibraryManager(mock(Project.class));

    // Property changes (e.g. writable flag or file rename property) do not schedule updates,
    // matching VirtualFileContentsChangedAdapter behavior.
    final VFilePropertyChangeEvent propEvent = mock(VFilePropertyChangeEvent.class);
    when(propEvent.getFile()).thenReturn(localFile);
    manager.onVfsChanges(List.of(propEvent), localFs, false);
    assertEquals(0, manager.scheduleCount.get());

    // Non-local filesystem events are ignored outside unit test mode.
    final VFileContentChangeEvent jarContentEvent = mock(VFileContentChangeEvent.class);
    when(jarContentEvent.getFile()).thenReturn(jarFile);
    manager.onVfsChanges(List.of(jarContentEvent), localFs, false);
    assertEquals(0, manager.scheduleCount.get());

    // Non-local filesystem events ARE accepted in unit test mode (e.g. TempFileSystem).
    manager.onVfsChanges(List.of(jarContentEvent), localFs, true);
    assertEquals(1, manager.scheduleCount.get());

    // Null created file on VFileCreateEvent or VFileCopyEvent is safely ignored.
    final VFileCreateEvent unresolvedCreate = mock(VFileCreateEvent.class);
    when(unresolvedCreate.getFile()).thenReturn(null);
    final VFileCopyEvent unresolvedCopy = mock(VFileCopyEvent.class);
    when(unresolvedCopy.findCreatedFile()).thenReturn(null);
    manager.onVfsChanges(List.of(unresolvedCreate, unresolvedCopy), localFs, false);
    assertEquals(1, manager.scheduleCount.get());

    // VFileCopyEvent with resolved created file on LocalFileSystem schedules update.
    final VFileCopyEvent resolvedCopy = mock(VFileCopyEvent.class);
    when(resolvedCopy.findCreatedFile()).thenReturn(localFile);
    manager.onVfsChanges(List.of(resolvedCopy), localFs, false);
    assertEquals(2, manager.scheduleCount.get());

    // VFileMoveEvent and VFileDeleteEvent on LocalFileSystem schedule updates.
    final VFileMoveEvent moveEvent = mock(VFileMoveEvent.class);
    when(moveEvent.getFile()).thenReturn(localFile);
    manager.onVfsChanges(List.of(moveEvent), localFs, false);
    assertEquals(3, manager.scheduleCount.get());

    final VFileDeleteEvent deleteEvent = mock(VFileDeleteEvent.class);
    when(deleteEvent.getFile()).thenReturn(localFile);
    manager.onVfsChanges(List.of(deleteEvent), localFs, false);
    assertEquals(4, manager.scheduleCount.get());

    // Batch with leading ignored events followed by multiple relevant events coalesces to 1 update.
    final VFileContentChangeEvent localContent1 = mock(VFileContentChangeEvent.class);
    when(localContent1.getFile()).thenReturn(localFile);
    final VFileContentChangeEvent localContent2 = mock(VFileContentChangeEvent.class);
    when(localContent2.getFile()).thenReturn(localFile);
    manager.onVfsChanges(
      List.of(propEvent, jarContentEvent, localContent1, localContent2),
      localFs,
      false);
    assertEquals(5, manager.scheduleCount.get());
  }
}
