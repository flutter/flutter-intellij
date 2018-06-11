/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import com.jetbrains.lang.dart.util.DartUrlResolverImpl;
import io.flutter.server.vmService.DartVmServiceDebugProcess;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.dartlang.vm.service.element.LibraryRef;
import org.dartlang.vm.service.element.Script;
import org.dartlang.vm.service.element.ScriptRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Verifies that we can map file locations.
 */
public class PositionMapperTest {
  private final FakeScriptProvider scripts = new FakeScriptProvider();

  @Rule
  public ProjectFixture fixture = Testing.makeEmptyModule();

  @Rule
  public TestDir tmp = new TestDir();


  VirtualFile sourceRoot;

  @Before
  public void setUp() throws Exception {
    sourceRoot = tmp.ensureDir("root");
    ModuleRootModificationUtil.addContentRoot(fixture.getModule(), sourceRoot.getPath());
  }

  @Test
  public void shouldGetPositionInFileUnderRemoteSourceRoot() throws Exception {
    tmp.writeFile("root/pubspec.yaml", "");
    tmp.ensureDir("root/lib");
    final VirtualFile main = tmp.writeFile("root/lib/main.dart", "");
    final VirtualFile hello = tmp.writeFile("root/lib/hello.dart", "");

    final PositionMapper mapper = setUpMapper(main, null);
    mapper.onLibrariesDownloaded(ImmutableList.of(
      makeLibraryRef("some/stuff/to/ignore/lib/main.dart")
    ));
    assertEquals("some/stuff/to/ignore", mapper.getRemoteSourceRoot());

    scripts.addScript("1", "2", "some/stuff/to/ignore/lib/hello.dart", ImmutableList.of(new Line(10, 123, 1)));

    final XSourcePosition pos = mapper.getSourcePosition("1", makeScriptRef("2", "some/stuff/to/ignore/lib/hello.dart"), 123);
    assertNotNull(pos);
    assertEquals(pos.getFile(), hello);
    assertEquals(pos.getLine(), 9); // zero-based
  }

  @Test
  public void shouldGetPositionInFileUnderRemoteBaseUri() throws Exception {
    tmp.writeFile("root/pubspec.yaml", "");
    tmp.ensureDir("root/lib");
    final VirtualFile main = tmp.writeFile("root/lib/main.dart", "");
    final VirtualFile hello = tmp.writeFile("root/lib/hello.dart", "");

    final PositionMapper mapper = setUpMapper(main, "remote:root");

    scripts.addScript("1", "2", "remote:root/lib/hello.dart", ImmutableList.of(new Line(10, 123, 1)));

    final XSourcePosition pos = mapper.getSourcePosition("1", makeScriptRef("2", "remote:root/lib/hello.dart"), 123);
    assertNotNull(pos);
    assertEquals(pos.getFile(), hello);
    assertEquals(pos.getLine(), 9); // zero-based
  }

  @NotNull
  private PositionMapper setUpMapper(VirtualFile contextFile, String remoteBaseUri) {
    final DartUrlResolver resolver = new DartUrlResolverImpl(fixture.getProject(), contextFile);
    final PositionMapper mapper = new PositionMapper(fixture.getProject(), sourceRoot, resolver, null);
    mapper.onConnect(scripts, remoteBaseUri);
    return mapper;
  }

  private LibraryRef makeLibraryRef(String uri) {
    final JsonObject elt = new JsonObject();
    elt.addProperty("uri", uri);
    return new LibraryRef(elt);
  }

  private ScriptRef makeScriptRef(String scriptId, String uri) {
    final JsonObject elt = new JsonObject();
    elt.addProperty("id", scriptId);
    elt.addProperty("uri", uri);
    return new ScriptRef(elt);
  }

  private static final class FakeScriptProvider implements DartVmServiceDebugProcess.ScriptProvider {
    final Map<String, Script> scripts = new HashMap<>();

    void addScript(String isolateId, String scriptId, String uri, List<Line> table) {
      final JsonArray tokenPosTable = new JsonArray();
      table.forEach((line) -> tokenPosTable.add(line.json));

      final JsonObject elt = new JsonObject();
      elt.addProperty("uri", uri);
      elt.add("tokenPosTable", tokenPosTable);
      scripts.put(isolateId + "-" + scriptId, new Script(elt));
    }

    @Nullable
    @Override
    public Script downloadScript(@NotNull String isolateId, @NotNull String scriptId) {
      return scripts.get(isolateId + "-" + scriptId);
    }
  }

  private static class Line {
    final JsonArray json = new JsonArray();
    Line(int number) {
      json.add(number);
    }

    Line(int number, int tokenPos, int column) {
      this(number);
      addToken(tokenPos, column);
    }

    void addToken(int tokenPos, int column) {
      json.add(tokenPos);
      json.add(column);
    }
  }
}
