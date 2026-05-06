/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.regex.Pattern;
import static org.junit.Assert.assertTrue;

/**
 * Integrity tests for the CI/CD automation pipeline.
 * <p>
 * <b>Why this exists:</b>
 * Our CI/CD scripts rely on a shared bash script to provision the Flutter SDK, and a
 * scheduled GitHub Actions workflow to automatically check for new Flutter stable releases
 * and submit version-bumping Pull Requests.
 * <p>
 * If a developer modifies the provisioning script (e.g., renaming the constant FLUTTER_VERSION)
 * or alters the regex search format without updating the GitHub Actions workflow, the automation
 * will fail silently in production.
 * <p>
 * This class acts as a "meta-test" or "integrity-check" that runs during standard project
 * compilations (`./gradlew test`) to give developers an immediate early warning of synchronization
 * failures before code is merged.
 */
public class CIIntegrityTest {

  /**
   * Verifies that the Flutter SDK provisioning script exists at the expected path
   * and defines the `FLUTTER_VERSION` constant in the exact semver format required.
   * <p>
   * If this test fails, it means `tool/provision_flutter.sh` was moved, deleted, or
   * the `FLUTTER_VERSION` constant definition was formatted incorrectly (or renamed).
   */
  @Test
  public void testFlutterProvisioningScriptIntegrity() throws Exception {
    // The JVM's Current Working Directory (CWD) during unit tests is the repository root.
    File scriptFile = new File("tool/provision_flutter.sh");
    assertTrue("provision_flutter.sh must exist at the repository root 'tool/' directory", scriptFile.exists());

    String content = Files.readString(scriptFile.toPath());

    // Ensure the shell script declares the version constant in a predictable pattern:
    // E.g. FLUTTER_VERSION="3.41.0"
    Pattern versionPattern = Pattern.compile("FLUTTER_VERSION=\"[0-9.]+\"");
    assertTrue(
      "provision_flutter.sh must define a constant FLUTTER_VERSION matching the expected format (e.g., FLUTTER_VERSION=\"3.41.0\"). " +
      "This constant is critical because the automated GitHub Actions updater searches for this exact pattern to bump the version.",
      versionPattern.matcher(content).find()
    );
  }

  /**
   * Verifies that the automated GitHub Actions updater workflow is synchronized
   * with the provisioning script.
   * <p>
   * Specifically, it ensures the workflow searches for the exact constant pattern
   * `FLUTTER_VERSION="[0-9.]+"` to perform its automated string replacement. If a developer
   * renames the constant in the script, they MUST also update the workflow, otherwise this test fails.
   */
  @Test
  public void testGitHubWorkflowRegexSync() throws Exception {
    File workflowFile = new File(".github/workflows/update_flutter.yaml");
    assertTrue("update_flutter.yaml workflow must exist in the '.github/workflows/' directory", workflowFile.exists());

    String content = Files.readString(workflowFile.toPath());

    // The workflow must use the exact same constant name in its grep/sed matching regex
    assertTrue(
      "update_flutter.yaml must reference the 'FLUTTER_VERSION=\"[0-9.]+\"' constant in its search-and-replace step. " +
      "If you renamed this constant in tool/provision_flutter.sh, you must also update it inside update_flutter.yaml.",
      content.contains("FLUTTER_VERSION=\"[0-9.]+\"")
    );
  }
}
