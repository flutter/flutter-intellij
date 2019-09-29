/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.common.util.concurrent.Uninterruptibles;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.inspector.InspectorService;
import io.flutter.inspector.InspectorGroupManagerService;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.EventStream;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.SourceChange;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WidgetEditToolbar {
  private class QuickAssistAction extends AnAction {
    private final String id;

    QuickAssistAction(@NotNull String id, Icon icon, String assistMessage) {
      super(assistMessage, null, icon);
      this.id = id;
      messageToActionMap.put(assistMessage, this);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      sendAnalyticEvent(id);
      final SourceChange change;
      synchronized (actionToChangeMap) {
        change = actionToChangeMap.get(this);
        actionToChangeMap.clear();
      }
      if (change != null) {
        applyChangeAndShowException(change);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final boolean hasChange = actionToChangeMap.containsKey(this);
      e.getPresentation().setEnabled(hasChange);
    }

    boolean isEnabled() {
      return actionToChangeMap.containsKey(this);
    }
  }

  private class ExtractMethodAction extends AnAction {
    private final String id = "dart.assist.flutter.extractMethod";

    ExtractMethodAction() {
      super("Extract Method...", null, FlutterIcons.ExtractMethod);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final AnAction action = ActionManager.getInstance().getAction("ExtractMethod");
      if (action != null) {
        final FlutterOutline outline = getWidgetOutline();
        if (outline != null) {
          TransactionGuard.submitTransaction(project, () -> {
            final Editor editor = getCurrentEditor();
            if (editor == null) {
              // It is a race condition if we hit this. Gracefully assume
              // the action has just been canceled.
              return;
            }
            // TODO(jacobr): how recently was this implemented?
            // Likely this code can be removed.
            // Ideally we don't need this - just caret at the beginning should be enough.
            // Unfortunately this was implemented only recently.
            // So, we have to select the widget range.
            final OutlineOffsetConverter converter = new OutlineOffsetConverter(project, activeFile.getValue());

            final int offset = converter.getConvertedOutlineOffset(outline);
            final int end = converter.getConvertedOutlineEnd(outline);
            editor.getSelectionModel().setSelection(offset, end);

            final JComponent editorComponent = editor.getComponent();
            final DataContext editorContext = DataManager.getInstance().getDataContext(editorComponent);
            final AnActionEvent editorEvent = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, editorContext);

            action.actionPerformed(editorEvent);
          });
        }
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final boolean isEnabled = isEnabled();
      e.getPresentation().setEnabled(isEnabled);
    }

    boolean isEnabled() {
      return getWidgetOutline() != null && getCurrentEditor() != null;
    }
  }

  private class ExtractWidgetAction extends AnAction {
    private final String id = "dart.assist.flutter.extractwidget";

    ExtractWidgetAction() {
      super("Extract Widget...");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final AnAction action = ActionManager.getInstance().getAction("Flutter.ExtractWidget");
      if (action != null) {
        TransactionGuard.submitTransaction(project, () -> {
          final Editor editor = getCurrentEditor();
          if (editor == null) {
            // It is a race condition if we hit this. Gracefully assume
            // the action has just been canceled.
            return;
          }
          final JComponent editorComponent = editor.getComponent();
          final DataContext editorContext = DataManager.getInstance().getDataContext(editorComponent);
          final AnActionEvent editorEvent = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, editorContext);

          action.actionPerformed(editorEvent);
        });
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final boolean isEnabled = isEnabled();
      e.getPresentation().setEnabled(isEnabled);
    }

    boolean isEnabled() {
      return getWidgetOutline() != null && getCurrentEditor() != null;
    }
  }

  final QuickAssistAction actionCenter;
  final QuickAssistAction actionPadding;
  final QuickAssistAction actionColumn;
  final QuickAssistAction actionRow;
  final QuickAssistAction actionContainer;
  final QuickAssistAction actionMoveUp;
  final QuickAssistAction actionMoveDown;
  final QuickAssistAction actionRemove;
  final ExtractMethodAction actionExtractMethod;
  final ExtractWidgetAction actionExtractWidget;
  private final FlutterDartAnalysisServer flutterAnalysisServer;
  private final EventStream<VirtualFile> activeFile;
  private final Map<String, AnAction> messageToActionMap = new HashMap<>();
  private final Map<AnAction, SourceChange> actionToChangeMap = new HashMap<>();
  private final Project project;
  private final boolean hotReloadOnAction;
  EventStream<List<FlutterOutline>> activeOutlines;
  private ActionToolbar toolbar;

  public WidgetEditToolbar(
    boolean hotReloadOnAction,
    EventStream<List<FlutterOutline>> activeOutlines,
    EventStream<VirtualFile> activeFile,
    Project project,
    FlutterDartAnalysisServer flutterAnalysisServer
  ) {
    this.hotReloadOnAction = hotReloadOnAction;
    this.project = project;
    this.flutterAnalysisServer = flutterAnalysisServer;
    this.activeFile = activeFile;
    actionCenter = new QuickAssistAction("dart.assist.flutter.wrap.center", FlutterIcons.Center, "Wrap with Center");
    actionPadding = new QuickAssistAction("dart.assist.flutter.wrap.padding", FlutterIcons.Padding, "Wrap with Padding");
    actionColumn = new QuickAssistAction("dart.assist.flutter.wrap.column", FlutterIcons.Column, "Wrap with Column");
    actionRow = new QuickAssistAction("dart.assist.flutter.wrap.row", FlutterIcons.Row, "Wrap with Row");
    actionContainer = new QuickAssistAction("dart.assist.flutter.wrap.container", FlutterIcons.Container, "Wrap with Container");
    actionMoveUp = new QuickAssistAction("dart.assist.flutter.move.up", FlutterIcons.Up, "Move widget up");
    actionMoveDown = new QuickAssistAction("dart.assist.flutter.move.down", FlutterIcons.Down, "Move widget down");
    actionRemove = new QuickAssistAction("dart.assist.flutter.removeWidget", FlutterIcons.RemoveWidget, "Remove this widget");
    actionExtractMethod = new ExtractMethodAction();
    actionExtractWidget = new ExtractWidgetAction();

    this.activeOutlines = activeOutlines;
    activeOutlines.listen(this::activeOutlineChanged);
  }

  Editor getCurrentEditor() {
    final VirtualFile file = activeFile.getValue();
    if (file == null) return null;

    final FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file);
    if (fileEditor instanceof TextEditor) {
      final TextEditor textEditor = (TextEditor)fileEditor;
      final Editor editor = textEditor.getEditor();
      if (!editor.isDisposed()) {
        return editor;
      }
    }
    return null;
  }

  public ActionToolbar getToolbar() {
    if (toolbar == null) {
      DefaultActionGroup toolbarGroup = new DefaultActionGroup();
      toolbarGroup.add(actionCenter);
      toolbarGroup.add(actionPadding);
      toolbarGroup.add(actionColumn);
      toolbarGroup.add(actionRow);
      toolbarGroup.add(actionContainer);
      toolbarGroup.addSeparator();
      toolbarGroup.add(actionExtractMethod);
      toolbarGroup.addSeparator();
      toolbarGroup.add(actionMoveUp);
      toolbarGroup.add(actionMoveDown);
      toolbarGroup.addSeparator();
      toolbarGroup.add(actionRemove);

      toolbar = ActionManager.getInstance().createActionToolbar("PreviewViewToolbar", toolbarGroup, true);
    }
    return toolbar;
  }

  private void activeOutlineChanged(List<FlutterOutline> outlines) {
    synchronized (actionToChangeMap) {
      actionToChangeMap.clear();
    }

    final VirtualFile selectionFile = activeFile.getValue();
    if (selectionFile != null && !outlines.isEmpty()) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        final OutlineOffsetConverter converter = new OutlineOffsetConverter(project, activeFile.getValue());
        final FlutterOutline firstOutline = outlines.get(0);
        final FlutterOutline lastOutline = outlines.get(outlines.size() - 1);
        final int offset = converter.getConvertedOutlineOffset(firstOutline);
        final int length = converter.getConvertedOutlineEnd(lastOutline) - offset;
        final List<SourceChange> changes = flutterAnalysisServer.edit_getAssists(selectionFile, offset, length);

        // If the current file or outline are different, ignore the changes.
        // We will eventually get new changes.
        final List<FlutterOutline> newOutlines = activeOutlines.getValue();
        if (!Objects.equals(activeFile.getValue(), selectionFile) || !outlines.equals(newOutlines)) {
          return;
        }

        // Associate changes with actions.
        // Actions will be enabled / disabled in background.
        for (SourceChange change : changes) {
          final AnAction action = messageToActionMap.get(change.getMessage());
          if (action != null) {
            actionToChangeMap.put(action, change);
          }
        }

        // Update actions immediately.
        if (getToolbar() != null) {
          ApplicationManager.getApplication().invokeLater(() -> getToolbar().updateActionsImmediately());
        }
      });
    }
  }

  FlutterOutline getWidgetOutline() {
    final List<FlutterOutline> outlines = activeOutlines.getValue();
    if (outlines.size() == 1) {
      final FlutterOutline outline = outlines.get(0);
      if (outline.getDartElement() == null) {
        return outline;
      }
    }
    return null;
  }

  private void sendAnalyticEvent(@NotNull String name) {
    FlutterInitializer.getAnalytics().sendEvent("preview", name);
  }

  private void applyChangeAndShowException(SourceChange change) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        AssistUtils.applySourceChange(project, change, false);
        if (hotReloadOnAction) {
          final InspectorService inspectorService = InspectorGroupManagerService.getInstance(project).getInspectorService();

          if (inspectorService != null) {
            ArrayList<FlutterApp> apps = new ArrayList<>();
            apps.add(inspectorService.getApp());
            FlutterReloadManager.getInstance(project).saveAllAndReloadAll(apps, "Refactor widget");
          }
        }
      }
      catch (DartSourceEditException exception) {
        FlutterMessages.showError("Error applying change", exception.getMessage());
      }
    });
  }

  public void createPopupMenu(Component comp, int x, int y) {
    // The corresponding tree item may have just been selected.
    // Wait short time for receiving assists from the server.
    for (int i = 0; i < 20 && actionToChangeMap.isEmpty(); i++) {
      Uninterruptibles.sleepUninterruptibly(5, TimeUnit.MILLISECONDS);
    }

    final DefaultActionGroup group = new DefaultActionGroup();
    boolean hasAction = false;
    if (actionCenter.isEnabled()) {
      hasAction = true;
      group.add(new TextOnlyActionWrapper(actionCenter));
    }
    if (actionPadding.isEnabled()) {
      hasAction = true;
      group.add(new TextOnlyActionWrapper(actionPadding));
    }
    if (actionColumn.isEnabled()) {
      hasAction = true;
      group.add(new TextOnlyActionWrapper(actionColumn));
    }
    if (actionRow.isEnabled()) {
      hasAction = true;
      group.add(new TextOnlyActionWrapper(actionRow));
    }
    if (actionContainer.isEnabled()) {
      hasAction = true;
      group.add(new TextOnlyActionWrapper(actionContainer));
    }
    group.addSeparator();
    if (actionExtractMethod.isEnabled()) {
      hasAction = true;
      group.add(new TextOnlyActionWrapper(actionExtractMethod));
    }
    if (actionExtractWidget.isEnabled()) {
      hasAction = true;
      group.add(new TextOnlyActionWrapper(actionExtractWidget));
    }
    group.addSeparator();
    if (actionMoveUp.isEnabled()) {
      hasAction = true;
      group.add(new TextOnlyActionWrapper(actionMoveUp));
    }
    if (actionMoveDown.isEnabled()) {
      hasAction = true;
      group.add(new TextOnlyActionWrapper(actionMoveDown));
    }
    group.addSeparator();
    if (actionRemove.isEnabled()) {
      hasAction = true;
      group.add(new TextOnlyActionWrapper(actionRemove));
    }

    // Don't show the empty popup.
    if (!hasAction) {
      return;
    }

    final ActionManager actionManager = ActionManager.getInstance();
    final ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group);
    popupMenu.getComponent().show(comp, x, y);
  }
}
