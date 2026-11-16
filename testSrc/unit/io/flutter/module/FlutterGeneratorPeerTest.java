/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import io.flutter.FlutterBundle;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link FlutterGeneratorPeer}.
 */
public class FlutterGeneratorPeerTest {
  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  @Rule
  public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void sdkComboUsesExtendableTextFieldWithBrowseExtension() throws Exception {
    assertNotNull(fixture.getProject());
    final AtomicBoolean ran = new AtomicBoolean(false);
    Testing.runOnDispatchThread(() -> {
      final FlutterGeneratorPeer peer = new FlutterGeneratorPeer(null);
      final ComboBox<String> combo = peer.getSdkComboBox();

      assertTrue("SDK combo box must be editable", combo.isEditable());
      assertSame(combo.getEditor(), peer.getSdkEditor());

      final Component editorComponent = peer.getSdkEditor().getEditorComponent();
      assertTrue(
        "SDK combo editor must be an ExtendableTextField",
        editorComponent instanceof ExtendableTextField);

      final ExtendableTextField textField = (ExtendableTextField)editorComponent;
      final List<ExtendableTextComponent.Extension> extensions = textField.getExtensions();
      assertEquals(1, extensions.size());

      final ExtendableTextComponent.Extension browseExtension = extensions.get(0);
      assertEquals(
        FlutterBundle.message("flutter.sdk.browse.path.label"),
        browseExtension.getTooltip());
      assertSame(AllIcons.General.OpenDisk, browseExtension.getIcon(false));
      assertSame(AllIcons.General.OpenDiskHover, browseExtension.getIcon(true));
      assertNotNull(browseExtension.getActionOnClick());

      final JBLabel sdkLabel = findLabelWithText(peer.getComponent(), "Flutter SDK path:");
      assertNotNull("SDK path JBLabel must exist in form", sdkLabel);
      assertEquals('S', (char)sdkLabel.getDisplayedMnemonic());
      assertSame("SDK path JBLabel must target the SDK ComboBox for accessibility", combo, sdkLabel.getLabelFor());
      ran.set(true);
    });
    assertTrue("EDT assertions must execute", ran.get());
  }

  @Test
  public void sdkPathNormalizationAndLiveValidation() throws Exception {
    assertNotNull(fixture.getProject());
    final File missingDir = new File(tempFolder.getRoot(), "does-not-exist");
    final File incompleteSdkDir = tempFolder.newFolder("incomplete-sdk");
    final File pubspecDir = new File(incompleteSdkDir, "packages/flutter");
    assertTrue(pubspecDir.mkdirs());
    assertTrue(new File(pubspecDir, "pubspec.yaml").createNewFile());
    final File binDir = new File(incompleteSdkDir, "bin");
    assertTrue(binDir.mkdirs());
    assertTrue(new File(binDir, "flutter").createNewFile());

    final File validSdkDir = tempFolder.newFolder("valid-sdk");
    final File validPubspecDir = new File(validSdkDir, "packages/flutter");
    assertTrue(validPubspecDir.mkdirs());
    assertTrue(new File(validPubspecDir, "pubspec.yaml").createNewFile());
    final File validBinDir = new File(validSdkDir, "bin");
    assertTrue(validBinDir.mkdirs());
    assertTrue(new File(validBinDir, "flutter").createNewFile());
    assertTrue(new File(validSdkDir, "bin/cache/dart-sdk/lib").mkdirs());

    final AtomicBoolean ran = new AtomicBoolean(false);
    Testing.runOnDispatchThread(() -> {
      final FlutterGeneratorPeer peer = new FlutterGeneratorPeer(null);
      final ExtendableTextField editorField =
        (ExtendableTextField)peer.getSdkEditor().getEditorComponent();
      final JLabel errorIcon = findErrorIconLabel(peer.getComponent());
      final JScrollPane errorPane = findComponentOfType(peer.getComponent(), JScrollPane.class);
      final JTextPane errorText = findComponentOfType(peer.getComponent(), JTextPane.class);
      assertNotNull("Error icon JLabel must exist", errorIcon);
      assertNotNull("Error JScrollPane must exist", errorPane);
      assertNotNull("Error JTextPane must exist", errorText);

      // Error UI starts hidden before any user edits.
      assertFalse(errorIcon.isVisible());
      assertFalse(errorPane.isVisible());

      // Non-existent path triggers live DocumentListener -> validate() without manual validate().
      editorField.setText(missingDir.getAbsolutePath());
      assertTrue("DocumentListener must show errorIcon on invalid path", errorIcon.isVisible());
      assertTrue("DocumentListener must show errorPane on invalid path", errorPane.isVisible());
      assertFalse(peer.validate());

      // Valid Flutter SDK with surrounding whitespace and Windows separators normalizes and hides error UI live.
      final String rawPathWithBackslashes =
        "  " + validSdkDir.getAbsolutePath().replace('/', '\\') + "  ";
      editorField.setText(rawPathWithBackslashes);
      assertFalse("DocumentListener must hide errorIcon on valid SDK path", errorIcon.isVisible());
      assertFalse("DocumentListener must hide errorPane on valid SDK path", errorPane.isVisible());
      assertEquals(
        validSdkDir.getAbsolutePath().replace('\\', '/'),
        peer.getSdkComboPath());
      assertTrue(peer.validate());

      // Flutter SDK missing bundled Dart SDK shows error UI live.
      editorField.setText(incompleteSdkDir.getAbsolutePath());
      assertTrue("DocumentListener must show errorIcon on incomplete SDK", errorIcon.isVisible());
      assertTrue("DocumentListener must show errorPane on incomplete SDK", errorPane.isVisible());
      assertFalse(peer.validate());

      // Reset to valid SDK so error UI hides, then clear text to fire removeUpdate -> empty-SDK error live.
      editorField.setText(validSdkDir.getAbsolutePath());
      assertFalse(errorIcon.isVisible());
      assertFalse(errorPane.isVisible());

      editorField.setText("");
      assertEquals("", peer.getSdkComboPath());
      assertTrue("DocumentListener removeUpdate must show errorIcon on blank path", errorIcon.isVisible());
      assertTrue("DocumentListener removeUpdate must show errorPane on blank path", errorPane.isVisible());
      assertTrue(errorText.getText().contains("A Flutter SDK must be specified for project creation."));
      assertFalse(peer.validate());
      ran.set(true);
    });
    assertTrue("EDT assertions must execute", ran.get());
  }

  @Test
  public void populateSdkComboFallsBackToFlutterSdkEnvOnlyWhenEmpty() throws Exception {
    assertNotNull(fixture.getProject());
    final AtomicBoolean ran = new AtomicBoolean(false);
    Testing.runOnDispatchThread(() -> {
      final JComboBox<String> emptyComboWithEnv = new JComboBox<>();
      emptyComboWithEnv.setEditable(true);
      FlutterGeneratorPeer.populateSdkCombo(emptyComboWithEnv, "/env/flutter/sdk");
      assertEquals("/env/flutter/sdk", emptyComboWithEnv.getSelectedItem());

      final JComboBox<String> emptyComboWithoutEnv = new JComboBox<>();
      emptyComboWithoutEnv.setEditable(true);
      FlutterGeneratorPeer.populateSdkCombo(emptyComboWithoutEnv, null);
      assertNull(emptyComboWithoutEnv.getSelectedItem());

      final JComboBox<String> emptyComboWithBlankEnv = new JComboBox<>();
      emptyComboWithBlankEnv.setEditable(true);
      FlutterGeneratorPeer.populateSdkCombo(emptyComboWithBlankEnv, "");
      assertNull(emptyComboWithBlankEnv.getSelectedItem());

      final JComboBox<String> populatedCombo =
        new JComboBox<>(new DefaultComboBoxModel<>(new String[]{"/existing/flutter/sdk"}));
      populatedCombo.setEditable(true);
      FlutterGeneratorPeer.populateSdkCombo(populatedCombo, "/env/flutter/sdk");
      assertEquals("/existing/flutter/sdk", populatedCombo.getSelectedItem());
      ran.set(true);
    });
    assertTrue("EDT assertions must execute", ran.get());
  }

  @Nullable
  private static JBLabel findLabelWithText(@NotNull Container root, @NotNull String expectedText) {
    for (Component child : root.getComponents()) {
      if (child instanceof JBLabel label && expectedText.equals(label.getText())) {
        return label;
      }
      if (child instanceof Container container) {
        final JBLabel nested = findLabelWithText(container, expectedText);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  @Nullable
  private static JLabel findErrorIconLabel(@NotNull Container root) {
    for (Component child : root.getComponents()) {
      if (child instanceof JLabel label && label.getIcon() == AllIcons.Actions.Lightning) {
        return label;
      }
      if (child instanceof Container container) {
        final JLabel nested = findErrorIconLabel(container);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  @Nullable
  private static <T extends Component> T findComponentOfType(@NotNull Container root, @NotNull Class<T> type) {
    for (Component child : root.getComponents()) {
      if (type.isInstance(child)) {
        return type.cast(child);
      }
      if (child instanceof Container container) {
        final T nested = findComponentOfType(container, type);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }
}
