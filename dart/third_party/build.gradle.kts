import java.io.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class VerboseTestListener : TestListener, Serializable {
    override fun beforeSuite(suite: TestDescriptor) {}
    override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
    override fun beforeTest(testDescriptor: TestDescriptor) {}
    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
        val elapsed = result.endTime - result.startTime
        Logging.getLogger(Test::class.java).lifecycle("took ${elapsed}ms")
    }
}

// Read javaVersion from gradle.properties
val javaVersionStr = providers.gradleProperty("javaVersion").get()
val javaVersionInt = javaVersionStr.toInt()
val kotlinJvmTarget = JvmTarget.fromTarget(javaVersionStr)
val javaVer = JavaVersion.toVersion(javaVersionStr)

allprojects {
    tasks.withType<JavaCompile> {
        // Specify UTF-8 for all compilations so we avoid Windows-1252.
        options.encoding = "UTF-8"
        // Enforce the target bytecode version and standard library API level across all Java compilation tasks.
        options.release.set(javaVersionInt)
    }
    tasks.withType<KotlinCompile> {
        compilerOptions {
            // Same as above, but for kotlin.
            jvmTarget.set(kotlinJvmTarget)
        }
    }
    tasks.withType<Test> {
        systemProperty("file.encoding", "UTF-8")
        if (providers.gradleProperty("verboseTests").isPresent) {
            addTestListener(VerboseTestListener())
        }
    }
}

// Plugins - must be first
plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.changelog)
    alias(libs.plugins.kover)
}

// Read ideaVersion from gradle.properties
val ideaVersion = providers.gradleProperty("ideaVersion").get()

val commitHash = System.getenv("KOKORO_GIT_COMMIT")?.takeIf { it.isNotBlank() }?.take(7) ?: try {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim().take(7)
} catch (e: Exception) {
    ""
}

// Configure project's dependencies
repositories {
    mavenCentral()
    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    pluginConfiguration {
        if (project.hasProperty("dev")) {
            val latestVersion = changelog.getLatest().version
            val nextMajorVersion = latestVersion.substringBefore('.').toInt() + 1
            val datestamp = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now())
            val baseVersion = "$nextMajorVersion.0.0-dev.$datestamp"
            version = if (commitHash.isNotEmpty()) "$baseVersion-$commitHash" else baseVersion
        } else {
            version = changelog.getLatest().version
        }

        name = providers.gradleProperty("pluginName")
        id = providers.gradleProperty("pluginId")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }

        println("plugin version: ${version.get()}")
        println("ideaVersion: ${this.ideaVersion.sinceBuild.get()} and above")

        changeNotes = provider {
            project.changelog.renderItem(project.changelog.getLatest(), Changelog.OutputType.HTML)
        }
    }
    pluginVerification {
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_WARNINGS,
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            //            VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
            VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            //            VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
            //            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            //            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
            VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.NOT_DYNAMIC,
        )
        verificationReportsFormats = VerifyPluginTask.VerificationReportsFormats.ALL
        subsystemsToCheck = VerifyPluginTask.Subsystems.ALL
        ides {
            recommended()
        }
    }
}

sourceSets {
    main {
        java {
            srcDir("gen")
            srcDir("thirdPartySrc/analysisServer")
            srcDir("thirdPartySrc/platform-lsp/src")
        }
        resources {
            srcDir("thirdPartySrc/platform-lsp/resources")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(kotlinJvmTarget)
    }
}

java {
    toolchain {
        // Dynamically use the running JVM version for the toolchain so Gradle does not search for or download a
        // specific JDK on CI or locally. (if we set this to a specific version, it's likely to break dev build.)
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.current().majorVersion))
    }
    sourceCompatibility = javaVer
    targetCompatibility = javaVer
}

dependencies {
    intellijPlatform {
        // intellijIdea can be found here:
        // https://www.jetbrains.com/idea/download/other.html
        intellijIdea(ideaVersion)

        testFramework(TestFrameworkType.Platform)

        // 1. Depend on the main Java plugin. This provides all Java-related features, including coverage.
        bundledPlugin("com.intellij.java")

        // 2. Depend on the main Kotlin plugin. This provides the Kotlin standard library and IDE support.
        bundledPlugin("org.jetbrains.kotlin")

        // 3. Add other necessary plugins and modules.
        bundledModule("intellij.platform.coverage")
        bundledModule("intellij.platform.coverage.agent")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.copyright")
    }

    implementation(fileTree("lib") { include("*.jar") })

    testImplementation(libs.junit)
}

intellijPlatformTesting {
  runIde {
    register("runTarget") {
      val ideTarget = project.findProperty("ide") as? String
      val ideV = project.findProperty("ideV") as? String
      val idePath = project.findProperty("idePath") as? String
      
      if (idePath != null) {
        localPath = file(idePath)
      } else {
        val actualTarget = ideTarget ?: "IntelliJ"
        type = when (actualTarget) {
          "IntelliJ" -> IntelliJPlatformType.IntellijIdeaCommunity
          "Ultimate" -> IntelliJPlatformType.IntellijIdeaUltimate
          "AndroidStudio" -> IntelliJPlatformType.AndroidStudio
          else -> IntelliJPlatformType.IntellijIdeaCommunity
        }
        // Fallback to 2024.2 for IntelliJ Community because 2025.3+ has publication changes that cause resolution errors.
        val fallbackVersion = if (actualTarget == "IntelliJ") "2024.2" else ideaVersion
        this.version = ideV ?: fallbackVersion
      }
    }
  }
}

tasks.named("runTarget") {
  val ideTarget = project.findProperty("ide") as? String
  val ideV = project.findProperty("ideV") as? String
  val idePath = project.findProperty("idePath") as? String
  
  doFirst {
    if (ideTarget == null && ideV == null && idePath == null) {
      println("============================================================")
      println("runTarget - Available Options")
      println("============================================================")
      println("Valid values for -Pide:")
      println(" - IntelliJ (default, IntelliJ IDEA Community)")
      println(" - Ultimate (IntelliJ IDEA Ultimate)")
      println(" - AndroidStudio")
      println()
      println("Valid values for -PideV:")
      println(" - Any valid version string for the selected IDE.")
      println(" - Examples for IntelliJ/Ultimate: 2024.1, 2024.2, 2024.3, 2025.1")
      println(" - Run './gradlew printProductsReleases' to see the full list.")
      println()
      println("Valid values for -PidePath:")
      println(" - Path to a local installation of the IDE (e.g., Android Studio).")
      println(" - Use this if Gradle resolution fails for Android Studio.")
      println()
      println("Examples:")
      println(" ./gradlew runTarget -Pide=IntelliJ -PideV=2025.1")
      println(" ./gradlew runTarget -Pide=Ultimate -PideV=2025.1")
      println(" ./gradlew runTarget -PidePath=/Applications/Android\\ Studio.app")
      println("============================================================")
      println("Stopping execution. Please run with parameters to launch a specific IDE.")
      
      throw org.gradle.api.tasks.StopExecutionException()
    }
  }
}

tasks {

    test {
        if (providers.gradleProperty("verboseTests").isPresent) {
            testLogging {
                events(
                    TestLogEvent.STARTED,
                    TestLogEvent.PASSED,
                    TestLogEvent.FAILED,
                    TestLogEvent.SKIPPED,
                )
                exceptionFormat = TestExceptionFormat.FULL
                showExceptions = true
                showCauses = true
                showStackTraces = true
            }
        }

        var showDartHomeWarning = false
        val dartSdkPath = System.getenv("DART_HOME")
        if (dartSdkPath != null) {
            val versionFile = file("${dartSdkPath}/version")
            if (versionFile.exists() && versionFile.isFile) {
                jvmArgs("-Ddart.sdk=${dartSdkPath}")
            } else {
                logger.error(
                    "This directory, ${dartSdkPath}, doesn't appear to be Dart SDK path, " +
                            "no version file found at ${versionFile.absolutePath}"
                )
            }
        } else {
            showDartHomeWarning = true
        }

        doFirst {
            val isRunningAnalysisServerTests = try {
                // --tests command-line filters are internal to Gradle. We retrieve them
                // via reflection to avoid compiling against internal Gradle classes.
                val cmdLinePatternsMethod = filter.javaClass.getMethod("getCommandLineIncludePatterns")
                val cmdLinePatterns = cmdLinePatternsMethod.invoke(filter) as? Set<*>
                val allPatterns = filter.includePatterns + (cmdLinePatterns?.filterIsInstance<String>() ?: emptySet())

                // If filters are specified, check if they target DAS tests. Otherwise, default to true.
                if (allPatterns.isNotEmpty()) {
                    allPatterns.any { allPatterns.any { it.contains("analysisServer") || it.contains("com.jetbrains.dart") } }
                } else {
                    true
                }
            } catch (_: Exception) {
                // Fallback: assume DAS tests might run if reflection fails.
                true
            }

            if (showDartHomeWarning && isRunningAnalysisServerTests) {
                logger.error("DART_HOME environment variable is not set. Dart Analysis Server tests will fail.")
            }
        }
    }
}

// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#how-to-check-the-latest-available-eap-release
tasks {
    printProductsReleases {
        channels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.EAP)
        types = listOf(IntelliJPlatformType.IntellijIdeaCommunity, IntelliJPlatformType.IntellijIdeaUltimate, IntelliJPlatformType.AndroidStudio)
        doLast {
            println()
            println("Mapping printProductsReleases output to ideV:")
            println(" - The prefix (e.g., IU-, IC-, AI-) maps to -Pide (Ultimate, IntelliJ, AndroidStudio).")
            println(" - The number part (e.g., 261.23567.71) maps to -PideV.")
            println(" - Example: AI-2025.3.3.6 -> -Pide=AndroidStudio -PideV=2025.3.3.6")
            println()
        }
    }
}

// A task to print the classpath used for compiling an IntelliJ plugin
// Run with `./gradlew printCompileClasspath --no-configuration-cache`
tasks.register("printCompileClasspath") {
    doLast {
        println("--- Begin Compile Classpath ---")
        configurations.getByName("compileClasspath").forEach { file ->
            println(file.absolutePath)
        }
        println("--- End Compile Classpath ---")
    }
}

abstract class PrintVersionTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val pluginVersion: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Internal
    abstract val archiveFileName: org.gradle.api.provider.Property<String>

    @org.gradle.api.tasks.TaskAction
    fun action() {
        println("Plugin Version: ${pluginVersion.get()}")
        println("Archive File Name: ${archiveFileName.get()}")
    }
}

tasks.register<PrintVersionTask>("printVersion") {
    pluginVersion.set(intellijPlatform.pluginConfiguration.version)
    val buildPluginTask = tasks.named<Zip>("buildPlugin")
    archiveFileName.set(buildPluginTask.flatMap { it.archiveFileName })
}

tasks.named<Zip>("buildPlugin") {
    val v = intellijPlatform.pluginConfiguration.version
    archiveFileName.set(v.map { versionStr ->
        if (project.hasProperty("versionedName")) {
            if (commitHash.isNotEmpty() && !versionStr.contains(commitHash)) {
                "Dart-$versionStr-$commitHash.zip"
            } else {
                "Dart-$versionStr.zip"
            }
        } else {
            "Dart.zip"
        }
    })
}
