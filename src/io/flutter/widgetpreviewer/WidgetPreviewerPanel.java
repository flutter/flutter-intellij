package io.flutter.widgetpreviewer;

import com.google.gson.*;
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
import io.flutter.logging.PluginLogger;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.MostlySilentColoredProcessHandler;
import io.flutter.utils.OpenApiUtils;
import io.flutter.view.EmbeddedTab;
import io.flutter.view.SimpleUrlProvider;
import io.flutter.view.ViewUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WidgetPreviewerPanel extends SimpleToolWindowPanel implements Disposable {
  private static final @NotNull Logger LOG = PluginLogger.createLogger(WidgetPreviewerPanel.class);
  @NotNull private final Project project;
  @NotNull private final ToolWindow toolWindow;
  private final @NotNull AtomicReference<ProcessHandler> flutterProcessRef = new AtomicReference<>();
  private final AtomicReference<EmbeddedTab> browserTabRef = new AtomicReference<>();

  private final JPanel contentPanel;
  @NotNull private final ViewUtils viewUtils = new ViewUtils();

  public WidgetPreviewerPanel(@NotNull Project project, @NotNull ToolWindow toolWindow) {
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

        final PubRoot root = PubRoot.forFile(project.getProjectFile());
        if (root == null) {
          showInfoMessage("Pub root could not be found to start widget previewer.");
          return;
        }
        boolean isVerboseMode = FlutterSettings.getInstance().isVerboseLogging();
        final FlutterCommand command = sdk.widgetPreview(root, isVerboseMode);
        LOG.info(command.getDisplayCommand());
        final ProcessHandler handler = new MostlySilentColoredProcessHandler(command.createGeneralCommandLine(project));
        flutterProcessRef.set(handler);
        handler.addProcessListener(new ProcessListener() {
          // Regex to find a URL like http://localhost:XXXXX/
          private static final Pattern URL_PATTERN = Pattern.compile("http://localhost:\\d+/?");

          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            if (outputType != ProcessOutputTypes.STDOUT) {
              return;
            }

            String text = event.getText();
            if (text == null) return;
            LOG.debug("STDOUT from Widget previewer: " + text);

            // Don't parse further if the URL has already been found.
            if (urlFuture.isDone()) {
              return;
            }

            // If we are in verbose mode, the text will have a prepended section for timings, e.g.:
            // ```
            // [   +4 ms] [{"event":"widget_preview.logMessage",...}]
            if (isVerboseMode) {
              text = jsonFromVerboseOutput(text);
            }

            try {
              final String maybeUrl = tryToExtractUrlFromJson(text);
              if (maybeUrl != null && !urlFuture.isDone()) {
                urlFuture.complete(maybeUrl);
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

  private static @NotNull String jsonFromVerboseOutput(@NotNull String text) {
    if (text.startsWith("[") && !text.startsWith("[{")) {
      final int closingBracketLocation = text.indexOf("]");
      if (closingBracketLocation != -1) {
        text = text.substring(closingBracketLocation + 1).trim();
      }
    }
    return text;
  }

  private static @Nullable String tryToExtractUrlFromJson(@NotNull String text) {
    final JsonElement element = JsonParser.parseString(text);
    if (element == null) return null;
    if (element.isJsonArray()) {
      final JsonArray jsonArray = element.getAsJsonArray();
      if (jsonArray == null) return null;
      for (JsonElement item : jsonArray) {
        if (item == null) continue;
        if (item.isJsonObject()) {
          final JsonObject obj = item.getAsJsonObject();
          if (obj == null) continue;
          if (obj.has("event") && obj.has("params")) {
            JsonElement eventElement = obj.get("event");
            if (eventElement == null) continue;
            final String eventName = eventElement.getAsString();
            if ("widget_preview.app.webLaunchUrl".equals(eventName) || "widget_preview.started".equals(eventName)) {
              JsonElement eventParams = obj.get("params");
              if (eventParams == null) continue;
              final JsonObject params = eventParams.getAsJsonObject();
              if (params == null) continue;
              if (params.has("url")) {
                JsonElement urlElement = params.get("url");
                if (urlElement == null) continue;
                return urlElement.getAsString();
              }
            }
          }
        }
      }
    }
    return null;
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
