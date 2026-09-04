package com.jetbrains.lang.dart.pubServer

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PubServerPathHandlerTest : BasePlatformTestCase() {

  fun testServedDirAndPathForWebFolder() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("web/index.html", "<html></html>")
    val result = requireNotNull(getServedDirAndPathForPubServer(project, psiFile.virtualFile)) {
      "Result should not be null for web folder"
    }

    assertEquals("web", result.first.name)
    assertEquals("/index.html", result.second)
  }

  fun testServedDirAndPathForExampleFolder() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("example/main.dart", "void main() {}")
    val result = requireNotNull(getServedDirAndPathForPubServer(project, psiFile.virtualFile)) {
      "Result should not be null for example folder"
    }

    assertEquals("example", result.first.name)
    assertEquals("/main.dart", result.second)
  }

  fun testLibFolderReturnsNull() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("lib/foo.dart", "class Foo {}")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNull("Result should be null for files inside lib folder", result)
  }

  fun testBuildFolderReturnsNull() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("build/out.js", "// compiled JS")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNull("Result should be null for files inside build folder", result)
  }

  fun testPackagesFolderReturnsNull() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("packages/pkg.dart", "// packages")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNull("Result should be null for files inside packages folder", result)
  }

  fun testRootLevelFileReturnsNull() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("README.md", "# Test")
    val result = getServedDirAndPathForPubServer(project, psiFile.virtualFile)

    assertNull("Result should be null for files directly in Dart project root", result)
  }

  fun testEscapedUrlPath() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    val psiFile = myFixture.addFileToProject("web/my page.html", "<html></html>")
    val result = requireNotNull(getServedDirAndPathForPubServer(project, psiFile.virtualFile)) {
      "Result should not be null for file with space in path"
    }

    assertEquals("web", result.first.name)
    assertEquals("/my%20page.html", result.second)
  }

  fun testServedDirAndPathForStringPathWithProjectName() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    myFixture.addFileToProject("web/index.html", "<html></html>")
    val result = requireNotNull(getServedDirAndPathForPubServer(project, "/test_project/web/index.html", "test_project")) {
      "Result should not be null for path with project name prefix"
    }

    assertEquals("web", result.first.name)
    assertEquals("/index.html", result.second)
  }

  fun testServedDirAndPathForStringPathWithoutProjectName() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    myFixture.addFileToProject("web/index.html", "<html></html>")
    val result = requireNotNull(getServedDirAndPathForPubServer(project, "/web/index.html", "test_project")) {
      "Result should not be null for path without project name prefix"
    }

    assertEquals("web", result.first.name)
    assertEquals("/index.html", result.second)
  }

  fun testServedDirAndPathForNonExistingFileWithStringPath() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    myFixture.addFileToProject("web/index.html", "<html></html>")
    val result = requireNotNull(getServedDirAndPathForPubServer(project, "/test_project/web/generated.dart.js", "test_project")) {
      "Result should not be null for non-existing file in served directory"
    }

    assertEquals("web", result.first.name)
    assertEquals("/generated.dart.js", result.second)
  }

  fun testServedDirAndPathForStringPathWithoutLeadingSlash() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    myFixture.addFileToProject("web/index.html", "<html></html>")
    val result = requireNotNull(getServedDirAndPathForPubServer(project, "test_project/web/index.html", "test_project")) {
      "Result should not be null for path without leading slash"
    }

    assertEquals("web", result.first.name)
    assertEquals("/index.html", result.second)
  }

  fun testServedDirAndPathForStringPathWithDifferentProjectNamePrefix() {
    myFixture.addFileToProject("pubspec.yaml", "name: test_project\n")
    myFixture.addFileToProject("web/index.html", "<html></html>")
    val result = getServedDirAndPathForPubServer(project, "/test_project_other/web/index.html", "test_project")

    assertNull("Result should be null when path prefix matches project name only partially and file doesn't exist", result)
  }
}
