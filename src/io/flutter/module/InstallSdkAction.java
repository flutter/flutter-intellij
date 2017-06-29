/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;


import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

@SuppressWarnings("ComponentNotRegistered")
class InstallSdkAction extends DumbAwareAction {

  private static final String GIT_EXECUTABLE = "git";

  private static GeneralCommandLine gitCommandBase() {
    return new GeneralCommandLine().withParentEnvironmentType(
      GeneralCommandLine.ParentEnvironmentType.CONSOLE).withExePath(GIT_EXECUTABLE);
  }

  public interface CancelActionListener {
    void actionCanceled();
  }

  private static abstract class InstallAction implements CancelActionListener {

    @Override
    public void actionCanceled() {
    }

    @NotNull
    private final FlutterGeneratorPeer myPeer;

    InstallAction(@NotNull FlutterGeneratorPeer peer) {
      peer.addCancelActionListener(this);
      myPeer = peer;
    }

    abstract void perform();

    abstract Icon getLinkIcon();

    abstract String getLinkText();

    void showError(@NotNull String message) {
      setErrorDetails(message);
    }

    void setProgressText(String text) {
      myPeer.getProgressText().setText(text);
    }

    void setSdkPath(String path) {
      myPeer.setSdkPath(path);
    }

    void validatePeer() {
      myPeer.validate();
    }

    void cancelAction() {
      myPeer.getCancelProgressButton().setEnabled(false);
    }

    void requestNextStep() {
      myPeer.requestNextStep();
    }

    void setProgressVisible(boolean visible) {
      myPeer.getInstallActionLink().setVisible(!visible);
      myPeer.getCancelProgressButton().setVisible(visible);
      myPeer.getCancelProgressButton().setEnabled(visible);
      myPeer.getProgressBar().setVisible(visible);
      myPeer.getProgressText().setVisible(visible);
    }

    void setErrorDetails(String details) {
      myPeer.setErrorDetails(details);
    }

    void setSdkComboEnablement(boolean enabled) {
      myPeer.getSdkComboBox().setEnabled(enabled);
    }
  }

  private static class ViewDocsAction extends InstallAction {
    private static final String ANALYTICS_KEY = "InstallSdkViewDocsAction";

    ViewDocsAction(FlutterGeneratorPeer peer) {
      super(peer);
    }

    @Override
    void perform() {
      FlutterInitializer.sendAnalyticsAction(ANALYTICS_KEY);
      BrowserUtil.browse("https://flutter.io/setup/");
    }

    @Override
    String getLinkText() {
      return "View setup docs…";
    }

    @Override
    Icon getLinkIcon() {
      return AllIcons.General.Web;
    }
  }

  private static class GitCloneAction extends InstallAction {
    private static final String ANALYTICS_KEY = "InstallSdkRunGitAction";
    private OSProcessHandler handler;

    GitCloneAction(FlutterGeneratorPeer peer) {
      super(peer);
    }

    @Override
    void perform() {
      // Defaults to ~/flutter
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
        FlutterInitializer.sendAnalyticsAction(ANALYTICS_KEY);
        installTo(installTarget);
      }
    }

    @Override
    public void actionCanceled() {
      handler.destroyProcess();
      validatePeer();
    }

    private void installTo(final @NotNull VirtualFile directory) {
      final String installPath = directory.getPath();
      final String sdkDir = new File(installPath, "flutter").getPath();

      setSdkPath(sdkDir);
      runCommand(new GitCloneCommand(installPath, sdkDir));
    }

    @Override
    String getLinkText() {
      return "Install SDK…";
    }

    @Override
    Icon getLinkIcon() {
      return AllIcons.Actions.Download;
    }

    private void runCommand(@NotNull InstallCommand cmd) {
      try {
        handler = new OSProcessHandler(cmd.getCommandLine());
      }
      catch (ExecutionException e) {
        cmd.onError(e);
        return;
      }
      cmd.registerTo(handler);

      handler.startNotify();
    }

    private abstract class InstallCommand {
      InstallCommand() {
        setProgressText(getTitle());
      }

      @NotNull
      abstract String getTitle();

      @NotNull
      abstract GeneralCommandLine getCommandLine();

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
              onError(event);
            }
          }

          @Override
          public void onTextAvailable(ProcessEvent event, Key outputType) {
            InstallCommand.this.onTextAvailable(event, outputType);
          }
        });
      }

      void onSuccess(@NotNull ProcessEvent event) {
      }

      void onError(@NotNull ExecutionException event) {
      }

      void onError(@NotNull ProcessEvent event) {
      }

      void onTextAvailable(ProcessEvent event, Key outputType) {
      }
    }

    private class GitCloneCommand extends InstallCommand {
      @NotNull private final String myInstallPath;
      @NotNull private final String mySdkDir;

      GitCloneCommand(final @NotNull String installPath, final @NotNull String sdkDir) {
        myInstallPath = installPath;
        mySdkDir = sdkDir;
      }

      @NotNull
      @Override
      GeneralCommandLine getCommandLine() {
        return gitCommandBase().withWorkDirectory(myInstallPath)
          .withParameters("clone", "-b", "alpha", "https://github.com/flutter/flutter.git");
      }

      @NotNull
      @Override
      String getTitle() {
        return "Cloning Flutter repository…";
      }

      @Override
      void onError(@NotNull ExecutionException exception) {
        showError("Error cloning Flutter repository: " + exception.getMessage());
      }

      @Override
      void onError(@NotNull ProcessEvent event) {
        showError("Flutter SDK download canceled.");
      }

      @Override
      protected void onSuccess(@NotNull ProcessEvent event) {
        runCommand(new FlutterPrecacheCommand(mySdkDir));
      }
    }

    private class FlutterPrecacheCommand extends InstallCommand {
      @NotNull private final String mySdkDir;

      FlutterPrecacheCommand(final @NotNull String sdkDir) {
        mySdkDir = sdkDir;
      }

      @NotNull
      @Override
      GeneralCommandLine getCommandLine() {
        final String flutterTool = FileUtil.toSystemDependentName(mySdkDir + "/bin/" + FlutterSdkUtil.flutterScriptName());
        return new GeneralCommandLine().withParentEnvironmentType(
          GeneralCommandLine.ParentEnvironmentType.CONSOLE).withWorkDirectory(mySdkDir)
          .withExePath(flutterTool)
          .withParameters("precache");
      }

      @NotNull
      @Override
      String getTitle() {
        return "Running 'flutter precache'…";
      }

      @Override
      void onTextAvailable(ProcessEvent event, Key outputType) {
        final String details = event.getText();
        // Filter out long messages and ones w/ leading whitespace.
        // Conveniently, these are also the unfriendly ones.  For example:
        // 6 57.9M    6 3838k    0     0  2978k      0  0:00:19  0:00:01  0:00:18 2978k
        //TODO(pq): consider a more robust approach to filtering.
        if (!details.startsWith(" ") && details.length() < 70) {
          setProgressText(details);
        }
      }

      @Override
      void onSuccess(@NotNull ProcessEvent event) {
        requestNextStep();
      }

      @Override
      void onError(@NotNull ExecutionException event) {
        showError("Error installing Flutter: " + event.getMessage());
      }

      @Override
      void onError(@NotNull ProcessEvent event) {
        showError("Error installing Flutter: " + event.getText() + " returned " + event.getExitCode());
      }
    }

    private void setInProgress(boolean visible) {
      if (visible) {
        setErrorDetails(null);
      }

      setSdkComboEnablement(!visible);
      setProgressVisible(visible);
    }
  }

  private final InstallAction myInstallAction;

  public InstallSdkAction(FlutterGeneratorPeer peer) {
    myInstallAction = createInstallAction(peer);
  }

  private static InstallAction createInstallAction(FlutterGeneratorPeer peer) {
    return hasGit() ? new GitCloneAction(peer) : new ViewDocsAction(peer);
  }

  private static boolean hasGit() {
    return FlutterUtils.runsCleanly(gitCommandBase().withParameters("version"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myInstallAction.perform();
  }

  public Icon getLinkIcon() {
    return myInstallAction.getLinkIcon();
  }

  public String getLinkText() {
    return myInstallAction.getLinkText();
  }
}
