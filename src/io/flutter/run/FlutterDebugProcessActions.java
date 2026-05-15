/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

/**
 * Shared action groups for Flutter-specific run and debug toolbars.
 */
public final class FlutterDebugProcessActions {
  /**
   * Third-party plugins can contribute inline toolbar actions with:
   * {@code <add-to-group group-id="Flutter.DebugProcess.TopToolbar" anchor="last" />}.
   */
  public static final @NotNull String TOP_TOOLBAR_EXTENSION_GROUP_ID = "Flutter.DebugProcess.TopToolbar";

  private FlutterDebugProcessActions() {
  }

  /**
   * Appends toolbar actions contributed by downstream dependent plugins.
   *
   * <p>{@link ActionManager#getAction(String)} returns {@link AnAction} because action groups are
   * actions too. The extension id resolves to an inline {@link ActionGroup}, which can contain
   * multiple contributed child actions.
   */
  public static void addTopToolbarExtensionActions(@NotNull DefaultActionGroup topToolbar) {
    final AnAction extensionGroupAction = ActionManager.getInstance().getAction(TOP_TOOLBAR_EXTENSION_GROUP_ID);
    if (!(extensionGroupAction instanceof ActionGroup actionGroup) || isEmpty(actionGroup)) {
      return;
    }

    topToolbar.addSeparator();
    topToolbar.addAction(extensionGroupAction);
  }

  private static boolean isEmpty(@NotNull ActionGroup actionGroup) {
    if (actionGroup instanceof DefaultActionGroup defaultActionGroup) {
      return defaultActionGroup.getChildActionsOrStubs().length == 0;
    }

    return actionGroup.getChildren(null).length == 0;
  }
}
