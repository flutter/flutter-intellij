package io.flutter.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito

class FlutterStudioProjectOpenProcessorTest {
  @Test
  fun testOpenProjectAsyncFallsBackWhenFindProjectFails() = runBlocking {
    // Setup
    val mockProject = Mockito.mock(Project::class.java)
    val mockVirtualFile = Mockito.mock(VirtualFile::class.java)
    Mockito.`when`(mockVirtualFile.path).thenReturn("/path/to/project")
    Mockito.`when`(mockProject.isDisposed).thenReturn(false)

    val fakeDelegate = object : ProjectOpenProcessor() {
      override val name: String = "Fake"
      override fun canOpenProject(file: VirtualFile): Boolean = true
      override fun doOpenProject(
        virtualFile: VirtualFile,
        projectToClose: Project?,
        forceOpenInNewFrame: Boolean
      ): Project? = mockProject

      override suspend fun openProjectAsync(
        virtualFile: VirtualFile,
        projectToClose: Project?,
        forceOpenInNewFrame: Boolean
      ): Project? {
        return mockProject
      }
    }

    val processor = object : FlutterStudioProjectOpenProcessor() {
      override fun getDelegateImportProvider(file: VirtualFile): ProjectOpenProcessor? {
        return fakeDelegate
      }

      override fun findProject(path: String): Project? {
        return null // Simulate failure to find project
      }

      override suspend fun configureFlutterProject(project: Project) {
        // No-op for test to avoid dependency on static FlutterModuleUtils
      }
    }

    // Execution
    val result = processor.openProjectAsync(mockVirtualFile, null, false)

    // Verification
    // If the fix works, it should fallback to mockProject
    // If the fix is absent, it would return null (because findProject returned null)
    Assert.assertEquals(mockProject, result)
  }
}
