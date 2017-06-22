/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;


import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBProgressBar;
import io.flutter.module.FlutterGeneratorPeer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

@SuppressWarnings("ComponentNotRegistered")
public class InstallSdkAction extends DumbAwareAction {

  private static abstract class InstallAction {
    abstract void perform();

    abstract String getLinkText();

    void showError(@NotNull String title, @NotNull String message) {
      //TODO(pq): implement
      System.out.println("Error: " + title + ", Message: " + message);
    }
  }

  private static class ViewDocsAction extends InstallAction {
    @Override
    void perform() {
      BrowserUtil.browse("https://flutter.io/setup/");
    }

    @Override
    String getLinkText() {
      return "View setup docs…";
    }
  }

  private static class GitCloneAction extends InstallAction {
    private final FlutterGeneratorPeer myPeer;

    public GitCloneAction(FlutterGeneratorPeer peer) {
      myPeer = peer;
    }

    @Override
    void perform() {
      //TODO(pq): consider prompting w a sensible default location (~/flutter)?
      @SuppressWarnings("DialogTitleCapitalization") final FileChooserDescriptor descriptor =
        new FileChooserDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor()) {
          @Override
          public void validateSelectedFiles(VirtualFile[] files) throws Exception {
            for (VirtualFile file : files) {
              if (file.findChild("flutter") != null) {
                throw new IllegalArgumentException("A file callled 'flutter' already exists in this location.");
              }
            }
          }
        }.withTitle("Flutter SDK Directory").withDescription("Choose a directory to install the Flutter SDK");

      final VirtualFile installTarget = FileChooser.chooseFile(descriptor, null, null);
      if (installTarget != null) {
        installTo(installTarget);
      }
    }

    private void installTo(final @NotNull VirtualFile directory)  {
      final String installPath = directory.getPath();

      final GeneralCommandLine cmd = new GeneralCommandLine().withParentEnvironmentType(
        GeneralCommandLine.ParentEnvironmentType.CONSOLE).withWorkDirectory(installPath).withExePath("git")
        .withParameters("clone", "https://github.com/flutter/flutter.git");
      runCommand(cmd, new CommandListener("Cloning Flutter repository…") {
        @Override
        void onError(@NotNull ExecutionException exception) {
          showError("Error Cloning Flutter Repository", exception.getMessage());
        }

        @Override
        protected void onSuccess(@NotNull ProcessEvent event) {
          final String sdkDir = new File(directory.getPath(), "flutter").getPath();

          final GeneralCommandLine cmd = new GeneralCommandLine().withParentEnvironmentType(
            GeneralCommandLine.ParentEnvironmentType.CONSOLE).withWorkDirectory(sdkDir).withExePath("bin/flutter")
            .withParameters("precache");
          runCommand(cmd, new CommandListener("Running 'flutter precache'…") {
            @Override
            void onTextAvailable(ProcessEvent event, Key outputType) {
              //TODO(pq): filter less useful / truncated messages.
              getProgressText().setText(event.getText());
            }

            @Override
            void onSuccess(@NotNull ProcessEvent event) {
              myPeer.setSdkPath(sdkDir);
            }
          });
        }
      });
    }

    private JBProgressBar getProgress() {
      return myPeer.getProgressBar();
    }

    private JTextPane getProgressText() {
      return myPeer.getProgressText();
    }

    @Override
    String getLinkText() {
      return "Install SDK…";
    }

    private void runCommand(@NotNull GeneralCommandLine cmd, @NotNull CommandListener listener) {
      final OSProcessHandler handler;
      try {
        handler = new OSProcessHandler(cmd);
      }
      catch (ExecutionException e) {
        listener.onError(e);
        return;
      }
      listener.registerTo(handler);

      handler.startNotify();
    }

    private abstract class CommandListener {

      CommandListener(@Nullable String title) {
        getProgressText().setText(title);
      }

      final void registerTo(OSProcessHandler handler) {
        handler.addProcessListener(new ProcessAdapter() {

          @Override
          public void startNotified(ProcessEvent event) {
            setInProgress(true);
          }

          @Override
          public void processTerminated(ProcessEvent event) {
            // Order matters here as onSuccess may produce more progress info.
            setInProgress(false);

            if (event.getExitCode() == 0) {
              onSuccess(event);
            }
            else {
              onError(new ExecutionException(event.getText()));
            }
          }

          @Override
          public void onTextAvailable(ProcessEvent event, Key outputType) {
            CommandListener.this.onTextAvailable(event, outputType);
          }
        });
      }


      void onSuccess(@NotNull ProcessEvent event) {
      }

      void onError(@NotNull ExecutionException event) {
      }

      void onTextAvailable(ProcessEvent event, Key outputType) {
      }
    }

    private void setInProgress(boolean visible) {
      if (visible) {
        getProgress().setIndeterminate(true);
        myPeer.hideErrorDetails();
        //TODO(pq): Disable Next button.
      }

      getProgress().setVisible(visible);
      getProgressText().setVisible(visible);
    }
  }

  private final InstallAction myInstallAction;

  public InstallSdkAction(FlutterGeneratorPeer peer) {
    myInstallAction = createInstallAction(peer);
  }

  private static InstallAction createInstallAction(FlutterGeneratorPeer peer) {
    return hasGit() ? new GitCloneAction(peer) : new ViewDocsAction();
  }

  @SuppressWarnings("SameReturnValue")
  private static boolean hasGit() {
    //TODO(pq): Flow bypassed by default; return true for testing.
    return false;  //SystemInfo.isMac;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myInstallAction.perform();
  }

  public String getLinkText() {
    return myInstallAction.getLinkText();
  }
}
