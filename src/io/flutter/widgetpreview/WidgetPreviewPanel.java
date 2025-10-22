package io.flutter.widgetpreview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.actions.RefreshToolWindowAction;
import io.flutter.jxbrowser.EmbeddedJxBrowser;
import io.flutter.logging.PluginLogger;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.utils.MostlySilentColoredProcessHandler;
import io.flutter.utils.OpenApiUtils;
import io.flutter.view.EmbeddedTab;
import io.flutter.view.SimpleUrlProvider;
import io.flutter.view.ViewUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WidgetPreviewPanel extends SimpleToolWindowPanel implements Disposable {
  private static final Logger LOG = PluginLogger.createLogger(WidgetPreviewPanel.class);
  // Regex to find a URL like http://localhost:XXXXX/
  private static final Pattern URL_PATTERN = Pattern.compile("http://localhost:\\d+/?");

  @NotNull private final Project project;
  @NotNull private final ToolWindow toolWindow;
  private final AtomicReference<ProcessHandler> flutterProcessRef = new AtomicReference<>();
  private final AtomicReference<EmbeddedTab> browserTabRef = new AtomicReference<>();

  private final JPanel contentPanel;
  @NotNull private final ViewUtils viewUtils = new ViewUtils();

  public WidgetPreviewPanel(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    super(true, true); // vertical, with border
    this.project = project;
    this.toolWindow = toolWindow;

    this.contentPanel = new JPanel(new BorderLayout());
    setContent(contentPanel);

    showInfoMessage(FlutterBundle.message("widget.preview.initializing"));

    // Start the preview process asynchronously
    startWidgetPreview();
  }

  private void startWidgetPreview() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
        if (sdk == null) {
          showInfoMessage(FlutterBundle.message("flutter.sdk.not.found"));
          return;
        }

        if (sdk.getVersion().fullVersion().equals(FlutterSdkVersion.UNKNOWN_VERSION)) {
          viewUtils.presentLabels(toolWindow, List.of("A Flutter SDK was found at the location",
                                                      "specified in the settings, however the directory",
                                                      "is in an incomplete state. To fix, shut down the IDE,",
                                                      "run `flutter doctor` or `flutter --version`",
                                                      "and then restart the IDE."));
          return;
        }

        // Check if widget-preview is available (requires a recent Flutter SDK)
        // Adjust this version check based on when 'flutter widget-preview' was introduced/stabilized.
        if (!sdk.getVersion().canUseWidgetPreviewer()) {
          showInfoMessage(FlutterBundle.message("widget.preview.sdk.too.old"));
          return;
        }

        showInfoMessage(FlutterBundle.message("widget.preview.starting"));

        final CompletableFuture<String> urlFuture = new CompletableFuture<>();
        final FlutterCommand command = sdk.widgetPreview(PubRoot.forFile(project.getProjectFile()));
        LOG.info(command.getDisplayCommand());
        final ProcessHandler handler = new MostlySilentColoredProcessHandler(command.createGeneralCommandLine(project));
        flutterProcessRef.set(handler);
        handler.addProcessListener(new ProcessAdapter() {
          // Regex to find a URL like http://localhost:XXXXX/
          private static final Pattern URL_PATTERN = Pattern.compile("http://localhost:\\d+/?");

          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            if (outputType != ProcessOutputTypes.STDOUT) {
              return;
            }

            final String text = event.getText();
            LOG.info("Text from widget previewer: " + text);
            try {
              JsonElement element = JsonParser.parseString(text);
              if (element.isJsonArray()) {
                for (JsonElement item : element.getAsJsonArray()) {
                  if (item.isJsonObject()) {
                    final JsonObject obj = item.getAsJsonObject();
                    if (obj.has("event") && obj.has("params")) {
                      final String eventName = obj.get("event").getAsString();
                      if ("widget_preview.app.webLaunchUrl".equals(eventName) || "widget_preview.started".equals(eventName)) {
                        final JsonObject params = obj.get("params").getAsJsonObject();
                        if (params.has("url")) {
                          final String url = params.get("url").getAsString();
                          if (!urlFuture.isDone()) {
                            urlFuture.complete(url);
                          }
                          // Optional: remove listener after we found the URL?
                          // handler.removeProcessListener(this);
                        }
                      }
                    }
                  }
                }
              }
            }
            catch (JsonSyntaxException e) {
              // This might happen if the output is not JSON. We can fallback to regex.
              final Matcher matcher = URL_PATTERN.matcher(text);
              if (matcher.find()) {
                final String url = matcher.group();
                if (!urlFuture.isDone()) {
                  urlFuture.complete(url);
                }
              }
            }

            urlFuture.whenComplete((url, ex) -> {
              if (ex != null) {
                LOG.error("Error getting widget preview URL", ex);
                showInfoMessage(FlutterBundle.message("widget.preview.error", ex.getMessage()));
                return;
              }

              ApplicationManager.getApplication().invokeLater(() -> {
                showInfoMessage(FlutterBundle.message("widget.preview.loading", url));

                OpenApiUtils.safeInvokeLater(() -> {
                  Optional.ofNullable(
                      FlutterUtils.embeddedBrowser(project))
                    .ifPresent(embeddedBrowser ->
                               {
                                 embeddedBrowser.openPanel(toolWindow, "Widget Previewer", FlutterIcons.Flutter, new SimpleUrlProvider(url), System.out::println,
                                                           null);
                               });
                });
              });
            });
          }

          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            if (!urlFuture.isDone()) {
              urlFuture.completeExceptionally(new Exception("Process terminated before URL was found. Exit code: " + event.getExitCode()));
            }
          }
        });

        handler.startNotify();
      }
      catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private void showInfoMessage(@NotNull String message) {
    ApplicationManager.getApplication().invokeLater(() -> {
      contentPanel.removeAll();
      contentPanel.add(viewUtils.warningLabel(message), BorderLayout.CENTER);
      contentPanel.revalidate();
      contentPanel.repaint();
    });
  }

  @Override
  public void dispose() {
    // Terminate the Flutter process when the tool window is closed
    final ProcessHandler process = flutterProcessRef.getAndSet(null);
    if (process != null && !process.isProcessTerminated()) {
      LOG.info("Terminating Flutter widget-preview process.");
      process.destroyProcess();
    }

    // Dispose the browser tab
    final EmbeddedTab tab = browserTabRef.getAndSet(null);
    if (tab != null) {
      tab.close();
    }
  }
}
