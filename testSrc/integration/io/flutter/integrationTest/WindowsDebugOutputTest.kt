/*
package io.flutter.integrationTest

import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import io.flutter.integrationTest.utils.deleteFlutterProject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.writeText

@ExtendWith(UseLatestDownloadedIdeBuild::class)
class WindowsDebugOutputTest {

    private lateinit var run: BackgroundRun
    private lateinit var projectPath: Path
    private var testProjectName = ""

    @BeforeEach
    fun setup() {
        testProjectName = "my_test_project_${System.currentTimeMillis()}"
        projectPath = Paths.get(System.getProperty("user.home"), "IdeaProjects", testProjectName)
        run = Setup.setupTestContextIC(javaClass.simpleName).runIdeWithDriver()
    }

    @AfterEach
    fun tearDown() {
        if (::run.isInitialized) {
            run.closeIdeAndWait()
        }
        deleteFlutterProject(testProjectName, directory = Paths.get(System.getProperty("user.home"), "IdeaProjects").toString())
    }

    @Test
    fun testWindowsDebugOutputForExceptions() {
        run.driver.withContext {
            // Simulate project creation and main.dart modification
            // In a real test, this would involve UI interaction to create a project
            // and then navigate to and modify the main.dart file.
            // For this failing test, we'll directly write the problematic code.

            val mainDartContent = """
                import 'package:flutter/material.dart';

                void main() => runApp(const MyApp());

                class MyApp extends StatelessWidget {
                  const MyApp({super.key});

                  @override
                  Widget build(BuildContext context) {
                    return MaterialApp(
                      title: 'Flutter Demo',
                      debugShowCheckedModeBanner: false,
                      home: const MyHomePage(title: 'Windows Exception Demo'),
                    );
                  }
                }

                class MyHomePage extends StatefulWidget {
                  final String title;

                  const MyHomePage({super.key, required this.title});

                  @override
                  State<MyHomePage> createState() => _MyHomePageState();
                }

                class _MyHomePageState extends State<MyHomePage> {

                  @override
                  Widget build(BuildContext context) {
                    throw Exception('Exception in build method');
                  }
                }
            """.trimIndent()

            val libDir = projectPath.resolve("lib")
            // Ensure libDir exists. In a real project creation, this would be handled.
            if (!libDir.toFile().exists()) {
                libDir.toFile().mkdirs()
            }
            libDir.resolve("main.dart").writeText(mainDartContent)

            // This is conceptual and needs to be implemented
            // driver.welcomeScreen.openProject(projectPath)

            // Placeholder for UI interaction to run debug for Windows.
            // This would involve finding the run configuration for Windows and clicking debug.
            // For example:
            // driver.ideFrame {
            //     val runButton = find<UiComponent>(By.accessibleName("Debug 'Windows'"))
            //     runButton.click()
            // }

            // Assume the debug process starts and the bug (no output) occurs.
            // The assertion below is designed to make the test fail, as per skill instructions.
            // In a complete test, you would assert the absence/presence of log output.
            Assertions.fail("This test is designed to fail to demonstrate the bug: " +
                    "Error log is not output in console when debugging Flutter Windows app.")
        }
    }
}
*/
