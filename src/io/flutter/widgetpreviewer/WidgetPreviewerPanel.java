package io.flutter.widgetpreviewer;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.util.messages.MessageBusConnection;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.devtools.DevToolsUtils;
import io.flutter.logging.PluginLogger;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.MostlySilentColoredProcessHandler;
import io.flutter.utils.OpenApiUtils;
import io.flutter.view.BrowserUrlProvider;
import io.flutter.view.EmbeddedTab;
import io.flutter.view.WidgetPreviewerUrlProvider;
import io.flutter.view.ViewUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class WidgetPreviewerPanel extends SimpleToolWindowPanel implements Disposable {
  private static final @NotNull Logger LOG = PluginLogger.createLogger(WidgetPreviewerPanel.class);
  @NotNull private final Project project;
  @NotNull private final ToolWindow toolWindow;
  private final @NotNull AtomicReference<ProcessHandler> flutterProcessRef = new AtomicReference<>();
  private final AtomicReference<EmbeddedTab> browserTabRef = new AtomicReference<>();

  private final JPanel contentPanel;
  @NotNull private final ViewUtils viewUtils = new ViewUtils();

  private BrowserUrlProvider urlProvider;

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
        // Check versioning of Flutter SDK.
        FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
        if (sdk == null) {
          showInfoMessage(FlutterBundle.message("flutter.sdk.not.found"));
          LOG.info("Flutter SDK was not found");
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
        Consumer<String> onError = (message) -> {
          showInfoMessage(FlutterBundle.message("widget.preview.error", message != null ? message : ""));
        };
        Consumer<@NotNull String> onSuccess = this::setUrlAndLoad;
        handler.addProcessListener(new WidgetPreviewListener(urlFuture, isVerboseMode, onError, onSuccess));
        handler.startNotify();
      }
      catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    });
  }

  // This is intended for the first time we load the panel - save the URL and listen for changes.
  private void setUrlAndLoad(@NotNull String url) {
    this.urlProvider = new WidgetPreviewerUrlProvider(url, new DevToolsUtils().getIsBackgroundBright());
    loadUrl(urlProvider);
    listenForReload();
  }

  private void loadUrl(@NotNull BrowserUrlProvider urlProvider) {
    showInfoMessage(FlutterBundle.message("widget.preview.loading", urlProvider.getBrowserUrl()));

    OpenApiUtils.safeInvokeLater(() -> {
      Optional.ofNullable(
          FlutterUtils.embeddedBrowser(project))
        .ifPresent(embeddedBrowser ->
                   {
                     embeddedBrowser.openPanel(toolWindow, "Widget Previewer", FlutterIcons.Flutter, urlProvider,
                                               System.out::println,
                                               null);
                   });
    });
  }

  private void listenForReload() {
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
        if (urlProvider == null) {
          return;
        }

        final boolean changed = urlProvider.maybeUpdateColor();
        if (changed) {
          loadUrl(urlProvider);
        }
      }
    });
    Disposer.register(toolWindow.getDisposable(), connection);
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
