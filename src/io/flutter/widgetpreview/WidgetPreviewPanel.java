package io.flutter.widgetpreview;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.dart.DtdUtils;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.devtools.DevToolsUtils;
import io.flutter.logging.PluginLogger;
import io.flutter.pub.PubRoot;
import io.flutter.run.daemon.DevToolsInstance;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.MostlySilentColoredProcessHandler;
import io.flutter.utils.OpenApiUtils;
import io.flutter.view.BrowserUrlProvider;
import io.flutter.view.EmbeddedBrowser;
import io.flutter.view.EmbeddedTab;
import io.flutter.view.ViewUtils;
import io.flutter.view.WidgetPreviewUrlProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class WidgetPreviewPanel extends SimpleToolWindowPanel implements Disposable {
  private static final @NotNull Logger LOG = PluginLogger.createLogger(WidgetPreviewPanel.class);
  @NotNull private final Project project;
  @NotNull private final ToolWindow toolWindow;
  private final @NotNull AtomicReference<ProcessHandler> flutterProcessRef = new AtomicReference<>();
  private final AtomicReference<EmbeddedTab> browserTabRef = new AtomicReference<>();

  private final JPanel contentPanel;
  @NotNull private final ViewUtils viewUtils = new ViewUtils();

  private BrowserUrlProvider urlProvider;

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
    OpenApiUtils.safeExecuteOnPooledThread(() -> {
      try {
        // Check versioning of Flutter SDK.
        FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
        if (sdk == null) {
          // ERROR_SITE: Flutter SDK not found.
          showInfoMessage(FlutterBundle.message("flutter.sdk.not.found"));
          LOG.info("Flutter SDK was not found");
          return;
        }

        if (sdk.getVersion().fullVersion().equals(FlutterSdkVersion.UNKNOWN_VERSION)) {
          LOG.warn("Flutter SDK version is unknown or incomplete.");
          viewUtils.presentLabels(toolWindow, List.of("A Flutter SDK was found at the location",
                                                      "specified in the settings, however the directory",
                                                      "is in an incomplete state. To fix, shut down the IDE,",
                                                      "run `flutter doctor` or `flutter --version`",
                                                      "and then restart the IDE."));
          return;
        }

        if (!sdk.getVersion().canUseWidgetPreview()) {
          LOG.info("Flutter SDK version is too old for widget preview: " + sdk.getVersion().fullVersion());
          showInfoMessage(FlutterBundle.message("widget.preview.sdk.too.old"));
          return;
        }

        showInfoMessage(FlutterBundle.message("widget.preview.starting"));

        final CompletableFuture<String> urlFuture = new CompletableFuture<>();

        final PubRoot root = PubRoot.forFile(project.getProjectFile());
        if (root == null) {
          LOG.warn("Pub root not found for project: " + project.getName());
          showInfoMessage("Pub root could not be found to start widget preview.");
          return;
        }

        boolean isVerboseMode = FlutterSettings.getInstance().isVerboseLogging();
        final String dtdUri = getDtdUri();
        final String devToolsUri = getDevToolsUri();
        final FlutterCommand command = sdk.widgetPreview(root, isVerboseMode, dtdUri, devToolsUri);
        LOG.info(command.getDisplayCommand());

        final ProcessHandler handler = new MostlySilentColoredProcessHandler(command.createGeneralCommandLine(project));
        flutterProcessRef.set(handler);
        Consumer<String> onError = (message) -> {
          LOG.warn("Widget preview process error: " + message);
          showInfoMessage(FlutterBundle.message("widget.preview.error", message != null ? message : ""));
        };
        Consumer<@NotNull String> onSuccess = this::setUrlAndLoad;
        handler.addProcessListener(new WidgetPreviewListener(urlFuture, isVerboseMode, onError, onSuccess));
        handler.startNotify();
      }
      catch (ExecutionException e) {
        LOG.error("Failed to execute widget preview command", e);
        throw new RuntimeException(e);
      }
    });
  }

  private @Nullable String getDevToolsUri() {
    try {
      final CompletableFuture<DevToolsInstance> devToolsFuture = DevToolsService.getInstance(project).getDevToolsInstance();
      if (devToolsFuture == null) {
        LOG.error("DevTools future is null.");
        return null;
      }

      final DevToolsInstance instance = devToolsFuture.get(30, TimeUnit.SECONDS);
      if (instance == null) {
        LOG.error("DevTools instance is null.");
        return null;
      }
      return new DevToolsUrl.Builder().setDevToolsHost(instance.host()).setDevToolsPort(instance.port()).build().getUrlString();
    }
    catch (InterruptedException | java.util.concurrent.ExecutionException | TimeoutException e) {
      LOG.error("DevTools service failed: ", e);
    }
    return null;
  }

  private @Nullable String getDtdUri() {
    try {
      final DartToolingDaemonService dtd = new DtdUtils().readyDtdService(project).get(30, TimeUnit.SECONDS);
      if (dtd == null) {
        LOG.error("DTD service is null.");
        return null;
      }

      return dtd.getUri();
    }
    catch (TimeoutException | java.util.concurrent.ExecutionException | InterruptedException e) {
      LOG.error("DTD service is not available after 30 seconds.", e);
    }
    return null;
  }

  // This is intended for the first time we load the panel - save the URL and listen for changes.
  private void setUrlAndLoad(@NotNull String url) {
    LOG.info("Widget preview URL received: " + url);
    this.urlProvider = new WidgetPreviewUrlProvider(url, new DevToolsUtils().getIsBackgroundBright());
    loadUrl(urlProvider);
    listenForReload();
  }

  private void loadUrl(@NotNull BrowserUrlProvider urlProvider) {
    LOG.info("Embedded browser is available: " + (FlutterUtils.embeddedBrowser(project) != null));
    showInfoMessage(FlutterBundle.message("widget.preview.loading", urlProvider.getBrowserUrl()));

    OpenApiUtils.safeInvokeLater(() -> {
      final Consumer<EmbeddedBrowser> onBrowserAvailable = embeddedBrowser -> {
        embeddedBrowser.openPanel(toolWindow, "Widget Preview", FlutterIcons.Flutter, urlProvider,
            System.out::println,
            null);
      };

      final Runnable onBrowserUnavailable = () -> {
        final List<io.flutter.utils.LabelInput> inputs = List.of(
            new io.flutter.utils.LabelInput("Embedded browser is not available."),
            new io.flutter.utils.LabelInput("Open in external browser", (label, data) -> {
              BrowserLauncher.getInstance().browse(urlProvider.getBrowserUrl(), null);
            }));
        final JPanel panel = viewUtils.createClickableLabelPanel(inputs);
        ApplicationManager.getApplication().invokeLater(() -> {
          contentPanel.removeAll();
          contentPanel.add(panel, BorderLayout.CENTER);
          contentPanel.revalidate();
          contentPanel.repaint();
        });
      };

      Optional.<EmbeddedBrowser>ofNullable(FlutterUtils.embeddedBrowser(project))
          .ifPresentOrElse(onBrowserAvailable, onBrowserUnavailable);
    });
  }

  // TODO(https://github.com/flutter/flutter/issues/177945): Ideally widget preview would change colors based on theme changes events,
  //  which we already send for the DevTools panels. If this is implemented then we can remove this listening code.
  private void listenForReload() {
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(EditorColorsManager.TOPIC, (EditorColorsListener)scheme -> {
      if (urlProvider == null) {
        return;
      }

      final boolean changed = urlProvider.maybeUpdateColor();
      if (changed) {
        loadUrl(urlProvider);
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
