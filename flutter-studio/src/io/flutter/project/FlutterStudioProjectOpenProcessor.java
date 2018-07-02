/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import io.flutter.FlutterUtils;
import io.flutter.android.GradleDependencyMerger;
import io.flutter.pub.PubRoot;
import org.gradle.internal.impldep.org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleExecutionSettings;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;

public class FlutterStudioProjectOpenProcessor extends FlutterProjectOpenProcessor {
  private static String ANDROID_LIBRARY_NAME = "Android Libraries";

  @Override
  public String getName() {
    return "Flutter Studio";
  }

  @Override
  public boolean canOpenProject(@Nullable VirtualFile file) {
    if (file == null) return false;
    ApplicationInfo info = ApplicationInfo.getInstance();
    final PubRoot root = PubRoot.forDirectory(file);
    return root != null && root.declaresFlutter();
  }

  @Nullable
  @Override
  public Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    if (super.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame) == null) return null;
    // The superclass may have caused the project to be reloaded. Find the new Project object.
    Project project = FlutterUtils.findProject(virtualFile.getPath());
    if (project != null) {
      FlutterProjectCreator.disableUserConfig(project);
      addModuleRoots(project);
      //GradleDependencyMerger.process(project);
      return project;
    }
    return null;
  }

  private static void addModuleRoots(@NotNull Project project) {
    GradleProjectResolver myProjectResolver = new GradleProjectResolver();
    ExternalSystemTaskId id = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, project);
    String projectPath = project.getBasePath();
    assert projectPath != null;
    projectPath += "/android";
    GradleExecutionSettings settings = getGradleExecutionSettings(project);
    Project androidProject = doGradleSync(project, projectPath, settings);
    //DataNode<ProjectData> projectDataNode = myProjectResolver.resolveProjectInfo(id, projectPath, false, settings, NULL_OBJECT);
    //Project androidProject = doImportData(projectDataNode, project);
    LibraryTable androidProjectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(androidProject);
    Library[] androidProjectLibraries = androidProjectLibraryTable.getLibraries();
    assert (androidProjectLibraries.length > 0);
    new WriteAction<Void>() {
      @Override
      protected void run(@NotNull final Result<Void> result) {
        updateAndroidLibraryRoots(project, androidProjectLibraries);
        //addSyntheticLibrary(project, library);
        StoreUtil.saveProject(project, true);
        // See ProjectManagerImpl.closeProject(); need to be in write-safe context.
        androidProject.dispose();
      }
    }.execute();

    //MultiMap<Key<ProjectData>, DataNode<ProjectData>> grouped = recursiveGroup(Collections.singletonList(projectDataNode));
    //Collection<DataNode<ProjectData>> projects = grouped.get(ProjectKeys.PROJECT);
    //assert projects.size() == 1 || projects.isEmpty();
    //DataNode<ProjectData> projectNode = ContainerUtil.getFirstItem(projects);
    //ProjectData projectData = null;
    //ProjectSystemId projectSystemId = null;
    //if (projectNode != null) {
    //  projectData = projectNode.getData();
    //  projectSystemId = projectNode.getData().getOwner();
    //}
    //// else look for module and use that instead of project (not implemented).
    //if (projectSystemId != null) {
    //  ExternalSystemUtil.scheduleExternalViewStructureUpdate(project, projectSystemId);
    //}
    //// See ProjectDataManagerImpl.importData()
    //Set<Map.Entry<Key<ProjectData>, Collection<DataNode<ProjectData>>>> entries = grouped.entrySet();
    //ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    //if (indicator != null) {
    //  indicator.setIndeterminate(false);
    //}
    //int count = 0;
    //for (Map.Entry<Key<ProjectData>, Collection<DataNode<ProjectData>>> entry : entries) {
    //  if (indicator != null) {
    //    String message = ExternalSystemBundle.message(
    //      "progress.update.text", projectSystemId != null ? projectSystemId.getReadableName() : "",
    //      "Refresh " + getReadableText(entry.getKey()));
    //    indicator.setText(message);
    //    indicator.setFraction((double)count++ / entries.size());
    //  }
    //  Project androidProject =
    //    doImportData(entry.getKey(), entry.getValue(), projectData, project, new IdeModifiableModelsProviderImpl(project));

    // See ProjectManagerImpl.closeProject(); need to be in write-safe context.
    //androidProject.dispose();
    //}
  }

  private static Project doGradleSync(Project flutterProject, String path, GradleExecutionSettings settings) {
    VirtualFile dir = flutterProject.getBaseDir().findChild("android");
    assert (dir != null);
    EmbeddedAndroidProject androidProject = new EmbeddedAndroidProject(FileUtilRt.toSystemIndependentName(dir.getPath()), null);
    androidProject.init();

    GradleSyncListener listener = null;
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectLoaded();
    request.generateSourcesOnSuccess = false;
    request.useCachedGradleModels = false;
    request.runInBackground = false;
    GradleSyncInvoker gradleSyncInvoker = ServiceManager.getService(GradleSyncInvoker.class);
    gradleSyncInvoker.requestProjectSync(androidProject, request, listener);
    return androidProject;
  }

  static void deleteThis() {
    //ModuleManager mgr = ModuleManager.getInstance(project);
    //for (Module module : mgr.getModules()) {
    //  if (FlutterModuleUtils.isFlutterModule(module)) {
    //    GradleDependencyFetcher fetcher = new GradleDependencyFetcher(project);
    //    fetcher.run(); // TODO(messick): Need to make this async.
    //    Map<String, List<String>> dependencies = fetcher.getDependencies();
    //    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    //    Set<String> paths = new HashSet<>();
    //    LocalFileSystem lfs = LocalFileSystem.getInstance();
    //    List<String> single = Collections.emptyList();
    //    for (List<String> list : dependencies.values()) {
    //      if (list.size() > single.size()) {
    //        single = list;
    //      }
    //    }
    //    LibraryTable table = model.getModuleLibraryTable();
    //    for (String dep : single) {
    //      GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(dep);
    //      if (coordinate != null) {
    //        DependencyManagementUtil.addDependencies(module, Collections.singletonList(coordinate), false);
    //      }
    //      //Library library = table.createLibrary();
    //      //Library.ModifiableModel libraryModifiableModel = library.getModifiableModel();
    //      //libraryModifiableModel.addJarDirectory(dep, false);
    //    }
    //  }
    //}
  }

  @SuppressWarnings("Duplicates")
  private static Project doImportData(DataNode<ProjectData> node, Project flutterProject) {
    // TODO IMPLEMENT

    //final List<DataNode<ProjectData>> toImport = ContainerUtil.newSmartList();
    //final List<DataNode<ProjectData>> toIgnore = ContainerUtil.newSmartList();
    //
    //for (DataNode<ProjectData> node : nodes) {
    //  if (!key.equals(node.getKey())) continue;
    //
    //  if (node.isIgnored()) {
    //    toIgnore.add(node);
    //  }
    //  else {
    //    toImport.add(node);
    //  }
    //}
    //
    //ensureTheDataIsReadyToUse(toImport);

    VirtualFile dir = flutterProject.getBaseDir().findChild("android");
    assert (dir != null);
    EmbeddedAndroidProject project = new EmbeddedAndroidProject(FileUtilRt.toSystemIndependentName(dir.getPath()), null);
    project.init();
    ProjectDataManagerImpl mgr = ProjectDataManagerImpl.getInstance();
    mgr.importData(node, project, true);
    ////noinspection unchecked
    //NotNullLazyValue<Map<Key<?>, List<ProjectDataService<?, ?>>>> servicesValue = (NotNullLazyValue<Map<Key<?>, List<ProjectDataService<?, ?>>>>)ReflectionUtil.getField(ProjectDataManagerImpl.class, mgr, NotNullLazyValue.class, "myServices");
    //assert servicesValue != null;
    //List<ProjectDataService<?, ?>> services = servicesValue.getValue().get(key);
    //for (ProjectDataService service : services) {
    //  //noinspection unchecked
    //  service.importData(toImport, projectData, project, modifiableModelsProvider);
    //}
    return project;
  }

  private static void ensureTheDataIsReadyToUse(@NotNull Collection<DataNode<ProjectData>> nodes) {
    for (DataNode<?> node : nodes) {
      ProjectDataManagerImpl.getInstance().ensureTheDataIsReadyToUse(node);
    }
  }

  @NotNull
  private static String getReadableText(@NotNull Key key) {
    String[] strings = StringUtils.splitByCharacterTypeCamelCase(key.toString());
    for (int i = 0; i < strings.length; i++) {
      strings[i] = strings[i].toLowerCase(Locale.ENGLISH);
    }
    return StringUtils.join(strings, ' ');
  }

  public static MultiMap<Key<ProjectData>, DataNode<ProjectData>> recursiveGroup(@NotNull Collection<DataNode<ProjectData>> nodes) {
    MultiMap<Key<ProjectData>, DataNode<ProjectData>> result = new ContainerUtil.KeyOrderedMultiMap<>();
    Queue<Collection<DataNode<ProjectData>>> queue = ContainerUtil.newLinkedList();
    queue.add(nodes);
    while (!queue.isEmpty()) {
      Collection<DataNode<ProjectData>> _nodes = queue.remove();
      result.putAllValues(ContainerUtil.groupBy(_nodes, node -> node.getKey()));
      for (DataNode<ProjectData> _node : _nodes) {
        queue.add((Collection)_node.getChildren());
      }
    }
    return result;
  }

  private static void updateAndroidLibraryRoots(Project flutterProject, Library[] libraries) {
    Library library = getAndroidLibrary(flutterProject, ProjectLibraryTable.getInstance(flutterProject));
    String[] existingUrls = library.getUrls(OrderRootType.CLASSES);
    ApplicationManager.getApplication().runWriteAction(() -> {
      final LibraryEx.ModifiableModelEx model = (LibraryEx.ModifiableModelEx)library.getModifiableModel();
      for (String url : existingUrls) {
        model.removeRoot(url, OrderRootType.CLASSES);
      }

      for (Library refLibrary : libraries) {
        for (OrderRootType rootType : new OrderRootType[]{OrderRootType.CLASSES, OrderRootType.SOURCES}) {
          for (String url : refLibrary.getRootProvider().getUrls(rootType)) {
            model.addRoot(url, rootType);
          }
        }
      }

      model.commit();
    });
  }

  private static Library getAndroidLibrary(Project flutterProject, LibraryTable projectLibraryTable) {
    Library existingLibrary = projectLibraryTable.getLibraryByName(ANDROID_LIBRARY_NAME);
    if (existingLibrary != null) {
      return existingLibrary;
    }
    return WriteAction.compute(() -> {
      LibraryTableBase.ModifiableModel libTableModel =
        ProjectLibraryTable.getInstance(flutterProject).getModifiableModel();
      Library lib = libTableModel.createLibrary(ANDROID_LIBRARY_NAME);
      libTableModel.commit();
      return lib;
    });
  }

  private static void addSyntheticLibrary(Project flutterProject, Library gradleLibrary) {
    String name = gradleLibrary.getName();
    if (name == null) {
      return;
    }
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(flutterProject);
    LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
    Library library = libraryTableModel.getLibraryByName(name);
    if (library != null) {
      libraryTableModel.dispose();
      return;
    }
    library = libraryTableModel.createLibrary(name);
    Library.ModifiableModel model = library.getModifiableModel();
    for (Library refLibrary : gradleLibrary.getTable().getLibraries()) {
      for (OrderRootType rootType : new OrderRootType[]{OrderRootType.CLASSES, OrderRootType.SOURCES}) {
        for (String url : refLibrary.getRootProvider().getUrls(rootType)) {
          model.addRoot(url, rootType);
        }
      }
    }
    model.commit();
    model.dispose();
    libraryTableModel.commit();
    libraryTableModel.dispose();
  }

  @SuppressWarnings("Duplicates")
  private static Library addProjectLibrary(final Module module,
                                           final String name,
                                           final List<String> jarDirectories,
                                           final VirtualFile[] sources) {
    return new WriteAction<Library>() {
      @Override
      protected void run(@NotNull final Result<Library> result) {
        LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(module.getProject());
        Library library = libraryTable.getLibraryByName(name);
        if (library == null) {
          library = libraryTable.createLibrary(name);
          Library.ModifiableModel model = library.getModifiableModel();
          for (String path : jarDirectories) {
            String url = VfsUtilCore.pathToUrl(path);
            VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
            model.addJarDirectory(url, false);
          }
          for (VirtualFile sourceRoot : sources) {
            model.addRoot(sourceRoot, OrderRootType.SOURCES);
          }
          model.commit();
        }
        result.setResult(library);
      }
    }.execute().getResultObject();
  }

  private static class EmbeddedAndroidProject extends ProjectImpl {
    protected EmbeddedAndroidProject(@NotNull String filePath, @Nullable String projectName) {
      super(filePath, projectName);
    }
  }
}
