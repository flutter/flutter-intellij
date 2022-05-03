package io.flutter.analytics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.dart.server.AnalysisServerListener;
import com.google.dart.server.RequestListener;
import com.google.dart.server.ResponseListener;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupListener;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.fixes.DartQuickFix;
import com.jetbrains.lang.dart.fixes.DartQuickFixListener;
import io.flutter.FlutterInitializer;
import io.flutter.utils.FileUtils;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.beans.PropertyChangeEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("LocalCanBeFinal")
public final class FlutterAnalysisServerListener implements Disposable, AnalysisServerListener {
  // statics
  static final String INITIAL_COMPUTE_ERRORS_TIME = "initialComputeErrorsTime";
  static final String INITIAL_HIGHLIGHTS_TIME = "initialHighlightsTime";
  static final String INITIAL_OUTLINE_TIME = "initialOutlineTime";
  static final String ROUND_TRIP_TIME = "roundTripTime";
  static final String QUICK_FIX = "quickFix";
  static final String UNKNOWN_LOOKUP_STRING = "<unknown>";
  static final String ANALYSIS_SERVER_LOG = "analysisServerLog";
  static final String ACCEPTED_COMPLETION = "acceptedCompletion";
  static final String REJECTED_COMPLETION = "rejectedCompletion";
  static final String E2E_IJ_COMPLETION_TIME = "e2eIJCompletionTime";
  static final String GET_SUGGESTIONS = "completion.getSuggestions";
  static final String FIND_REFERENCES = "search.findElementReferences";
  static final Set<String> MANUALLY_MANAGED_METHODS = Sets.newHashSet(GET_SUGGESTIONS, FIND_REFERENCES);
  static final String ERRORS = "errors";
  static final String WARNINGS = "warnings";
  static final String HINTS = "hints";
  static final String LINTS = "lints";
  static final String DURATION = "duration";
  static final String FAILURE = "failure";
  static final String SUCCESS = "success";
  static final String ERROR_TYPE_REQUEST = "R";
  static final String ERROR_TYPE_SERVER = "@";

  static final String DAS_STATUS_EVENT_TYPE = "analysisServerStatus";
  static final String[] ERROR_TYPES = new String[]{
    AnalysisErrorType.CHECKED_MODE_COMPILE_TIME_ERROR,
    AnalysisErrorType.COMPILE_TIME_ERROR,
    AnalysisErrorType.HINT,
    AnalysisErrorType.LINT,
    AnalysisErrorType.STATIC_TYPE_WARNING,
    AnalysisErrorType.STATIC_WARNING,
    AnalysisErrorType.SYNTACTIC_ERROR
  };

  static final String LOG_ENTRY_KIND = "kind";
  static final String LOG_ENTRY_TIME = "time";
  static final String LOG_ENTRY_DATA = "data";
  static final String LOG_ENTRY_SDK_VERSION = "sdkVersion";

  private static final long ERROR_REPORT_INTERVAL = 1000 * 60 * 60 * 2; // Two hours between cumulative error reports, in ms.
  private static final long GENERAL_REPORT_INTERVAL = 1000 * 60; // One minute between general analytic reports, in ms.
  private static final Logger LOG = Logger.getInstance(FlutterAnalysisServerListener.class);
  private static final boolean IS_TESTING = ApplicationManager.getApplication().isUnitTestMode();

  @NotNull final FlutterRequestListener requestListener;
  @NotNull final FlutterResponseListener responseListener;
  @NotNull final DartQuickFixListener quickFixListener;
  // instance members
  @NotNull private final Project project;
  @NotNull private final Map<String, List<AnalysisError>> pathToErrors;
  @NotNull private final Map<String, Instant> pathToErrorTimestamps;
  @NotNull private final Map<String, Instant> pathToHighlightTimestamps;
  @NotNull private final Map<String, Instant> pathToOutlineTimestamps;
  @NotNull private final Map<String, RequestDetails> requestToDetails;
  @NotNull private final MessageBusConnection messageBusConnection;
  @NotNull private final FileEditorManagerListener fileEditorManagerListener;
  LookupSelectionHandler lookupSelectionHandler;
  @NotNull private Instant nextMemoryUsageLoggedInstant = Instant.EPOCH;
  private long errorsTimestamp;
  private long generalTimestamp;
  private int errorCount;
  private int warningCount;
  private int hintCount;
  private int lintCount;

  FlutterAnalysisServerListener(@NotNull Project project) {
    this.project = project;
    this.pathToErrors = new HashMap<>();
    this.pathToErrorTimestamps = new HashMap<>();
    this.pathToHighlightTimestamps = new HashMap<>();
    this.pathToOutlineTimestamps = new HashMap<>();
    this.requestToDetails = new HashMap<>();
    this.messageBusConnection = project.getMessageBus().connect();
    LookupManager.getInstance(project).addPropertyChangeListener(this::onPropertyChange);

    this.fileEditorManagerListener = new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
        // Record the time that this file was opened so that we'll be able to log
        // relative timings for errors, highlights, outlines, etc.
        String filePath = file.getPath();
        Instant nowInstant = Instant.now();
        pathToErrorTimestamps.put(filePath, nowInstant);
        pathToHighlightTimestamps.put(filePath, nowInstant);
        pathToOutlineTimestamps.put(filePath, nowInstant);
      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      }

      @Override
      public void fileClosed(@NotNull final FileEditorManager source, @NotNull final VirtualFile file) {
      }
    };
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorManagerListener);
    messageBusConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      public void projectClosing(@NotNull Project project) {
        messageBusConnection.disconnect(); // Do this first to void memory leaks when switching pojects.
        errorsTimestamp = 0L; // Ensure we always report error counts on shutdown.
        maybeReportErrorCounts(); // The ShutdownTracker only allows three seconds, so this might not always complete.
      }
    });
    this.quickFixListener = new QuickFixListener();
    this.requestListener = new FlutterRequestListener();
    this.responseListener = new FlutterResponseListener();
    DartAnalysisServerService analysisServer = DartAnalysisServerService.getInstance(project);
    analysisServer.setServerLogSubscription(true);
    analysisServer.addQuickFixListener(this.quickFixListener);
    analysisServer.addRequestListener(this.requestListener);
    analysisServer.addResponseListener(this.responseListener);
  }

  @NotNull
  public static FlutterAnalysisServerListener getInstance(@NotNull final Project project) {
    return Objects.requireNonNull(project.getService(FlutterAnalysisServerListener.class));
  }

  @NotNull
  private static String safelyGetString(JsonObject jsonObject, String memberName) {
    if (jsonObject != null && StringUtil.isNotEmpty(memberName)) {
      JsonElement jsonElement = jsonObject.get(memberName);
      if (jsonElement != null) {
        return Objects.requireNonNull(jsonElement.getAsString());
      }
    }
    return "";
  }

  @Override
  public void dispose() {
    // This is deprecated and marked for removal in 2021.3. If it is removed we will
    // have to do some funny stuff to support older versions of Android Studio.
    //noinspection UnstableApiUsage
    LookupManager.getInstance(project).removePropertyChangeListener(this::onPropertyChange);
  }

  @Override
  public void computedAnalyzedFiles(List<String> list) {
    // No start time is recorded.
  }

  @Override
  public void computedAvailableSuggestions(@NotNull List<AvailableSuggestionSet> list, int[] ints) {
    // No start time is recorded.
  }

  @Override
  public void computedCompletion(String completionId,
                                 int replacementOffset,
                                 int replacementLength,
                                 List<CompletionSuggestion> completionSuggestions,
                                 List<IncludedSuggestionSet> includedSuggestionSets,
                                 List<String> includedElementKinds,
                                 List<IncludedSuggestionRelevanceTag> includedSuggestionRelevanceTags,
                                 boolean isLast,
                                 String libraryFilePathSD) {
    long currentTimestamp = System.currentTimeMillis();
    String id = getIdForMethod(GET_SUGGESTIONS);
    if (id == null) {
      return;
    }
    RequestDetails details = requestToDetails.remove(id);
    if (details == null) {
      return;
    }
    maybeReport(true, (analytics) -> {
      long startTime = details.startTime().toEpochMilli();
      analytics.sendTiming(ROUND_TRIP_TIME, GET_SUGGESTIONS, currentTimestamp - startTime); // test: computedCompletion
    });
  }

  @Nullable
  private String getIdForMethod(@NotNull String method) {
    Set<String> keys = requestToDetails.keySet();
    for (String id : keys) {
      RequestDetails details = requestToDetails.get(id);
      assert details != null;
      if ("completion.getSuggestions".equals(details.method())) {
        return id;
      }
    }
    return null;
  }

  @Override
  public void computedErrors(String path, List<AnalysisError> list) {
    assert list != null;
    pathToErrors.put(path, list);
    assert path != null;
    maybeLogInitialAnalysisTime(INITIAL_COMPUTE_ERRORS_TIME, path, pathToErrorTimestamps);
  }

  @NotNull
  public List<AnalysisError> getAnalysisErrorsForFile(String path) {
    if (path == null) {
      return AnalysisError.EMPTY_LIST;
    }
    return Objects.requireNonNull(pathToErrors.getOrDefault(path, AnalysisError.EMPTY_LIST));
  }

  /**
   * Iterate through all files in this {@link Project}, counting how many of each {@link
   * AnalysisErrorType} is in each file. The returned {@link HashMap} will contain the set of String
   * keys in ERROR_TYPES and values with the mentioned sums, converted to Strings.
   */
  @NotNull
  private HashMap<String, Integer> getTotalAnalysisErrorCounts() {
    // Create a zero-filled array of length ERROR_TYPES.length.
    int[] errorCountsArray = new int[ERROR_TYPES.length];

    // Iterate through each file in this project.
    for (String keyPath : pathToErrors.keySet()) {
      // Get the list of AnalysisErrors and remove any todos from the list, these are ignored in the
      // Dart Problems view, and can be ignored for any dashboard work.
      assert keyPath != null;
      List<AnalysisError> errors = getAnalysisErrorsForFile(keyPath);
      errors.removeIf(e -> {
        assert e != null;
        return Objects.equals(e.getType(), AnalysisErrorType.TODO);
      });
      if (errors.isEmpty()) {
        continue;
      }

      // For this file, count how many of each ERROR_TYPES type we have and add this count to each
      // errorCountsArray[*]
      for (int i = 0; i < ERROR_TYPES.length; i++) {
        final int j = i;
        errorCountsArray[j] += errors.stream().filter(e -> {
          assert e != null;
          return Objects.equals(e.getType(), ERROR_TYPES[j]);
        }).count();
      }
    }

    // Finally, create and return the final HashMap.
    HashMap<String, Integer> errorCounts = new HashMap<>();
    for (int i = 0; i < ERROR_TYPES.length; i++) {
      errorCounts.put(ERROR_TYPES[i], errorCountsArray[i]);
    }
    return errorCounts;
  }

  @Override
  public void computedHighlights(String path, List<HighlightRegion> list) {
    assert path != null;
    maybeLogInitialAnalysisTime(INITIAL_HIGHLIGHTS_TIME, path, pathToHighlightTimestamps);
  }

  @Override
  public void computedImplemented(String s, List<ImplementedClass> list, List<ImplementedMember> list1) {
    // No start time is recorded.
  }

  @Override
  public void computedLaunchData(String s, String s1, String[] strings) {
  }

  @Override
  public void computedNavigation(String s, List<NavigationRegion> list) {
    // No start time is recorded.
  }

  @Override
  public void computedOccurrences(String s, List<Occurrences> list) {
    // No start time is recorded.
  }

  @Override
  public void computedOutline(String path, Outline outline) {
    assert path != null;
    maybeLogInitialAnalysisTime(INITIAL_OUTLINE_TIME, path, pathToOutlineTimestamps);
  }

  @Override
  public void computedOverrides(String s, List<OverrideMember> list) {
    // No start time is recorded.
  }

  @Override
  public void computedClosingLabels(String s, List<ClosingLabel> list) {
    // No start time is recorded.
  }

  @Override
  public void computedSearchResults(String searchId, List<SearchResult> results, boolean isLast) {
    RequestDetails details = requestToDetails.remove(searchId);
    if (details == null) {
      return;
    }
    maybeReport(true, (analytics) -> {
      String method = details.method();
      long duration = generalTimestamp - details.startTime().toEpochMilli();
      LOG.debug(ROUND_TRIP_TIME + " " + method + " " + duration);
      analytics.sendTiming(ROUND_TRIP_TIME, method, duration); // test: computedSearchResults()
    });
  }

  @Override
  public void flushedResults(List<String> list) {
    // Timing info not valid.
  }

  @Override
  public void requestError(RequestError requestError) {
    maybeReport(true, (analytics) -> {
      assert requestError != null;
      String code = requestError.getCode();
      if (code == null) {
        code = requestError.getMessage(); // test: requestErrorNoCode()
      }
      String stack = requestError.getStackTrace();
      String exception = composeException(ERROR_TYPE_REQUEST, code, stack);
      LOG.debug(exception);
      analytics.sendException(exception, false); // test: requestError()
    });
  }

  /**
   * Build an exception parameter containing type, code, and stack. Limit it to 150 chars.
   *
   * @param type  "R" for request error, "S" for server error
   * @param code  error code or message
   * @param stack stack trace
   * @return exception description, value of "exd" parameter in analytics
   */
  private static String composeException(@NotNull String type, @Nullable String code, @Nullable String stack) {
    String exception = type + " ";
    if (code != null && !code.isEmpty()) {
      exception += code;
      if (stack != null && !stack.isEmpty()) {
        exception += "\n" + stack;
      }
    }
    else if (stack != null && !stack.isEmpty()) {
      exception += stack;
    }
    else {
      exception += "exception";
    }
    if (exception.length() > 150) {
      exception = exception.substring(0, 149);
    }
    return exception;
  }

  @Override
  public void serverConnected(String s) {
  }

  @Override
  public void serverError(boolean isFatal, String message, String stackTraceString) {
    maybeReport(true, (analytics) -> {
      String exception = composeException(ERROR_TYPE_SERVER, message, stackTraceString);
      LOG.debug(exception + " fatal");
      analytics.sendException(exception, isFatal); // test: serverError()
    });
  }

  @Override
  public void serverIncompatibleVersion(String s) {
  }

  @Override
  public void serverStatus(AnalysisStatus analysisStatus, PubStatus pubStatus) {
    assert analysisStatus != null;
    if (!analysisStatus.isAnalyzing()) {
      @NotNull HashMap<String, Integer> errorCounts = getTotalAnalysisErrorCounts();
      errorCount = 0;
      errorCount += extractCount(errorCounts, AnalysisErrorType.CHECKED_MODE_COMPILE_TIME_ERROR);
      errorCount += extractCount(errorCounts, AnalysisErrorType.COMPILE_TIME_ERROR);
      errorCount += extractCount(errorCounts, AnalysisErrorType.SYNTACTIC_ERROR);
      warningCount = 0;
      warningCount += extractCount(errorCounts, AnalysisErrorType.STATIC_TYPE_WARNING);
      warningCount += extractCount(errorCounts, AnalysisErrorType.STATIC_WARNING);
      hintCount = extractCount(errorCounts, AnalysisErrorType.HINT);
      lintCount = extractCount(errorCounts, AnalysisErrorType.LINT);
      if (IS_TESTING) {
        errorCount = warningCount = hintCount = lintCount = 1;
      }
      maybeReportErrorCounts();
    }
  }

  private void maybeReport(boolean observeThrottling, @NotNull java.util.function.Consumer<@NotNull Analytics> func) {
    if (observeThrottling && !IS_TESTING) {
      long currentTimestamp = System.currentTimeMillis();
      // Throttle to one report per interval.
      if (currentTimestamp - generalTimestamp < GENERAL_REPORT_INTERVAL) {
        return;
      }
      generalTimestamp = currentTimestamp;
    }
    func.accept(FlutterInitializer.getAnalytics());
  }

  private void maybeReportErrorCounts() {
    long currentTimestamp = System.currentTimeMillis();
    // Send accumulated error counts once every defined interval, plus when the project is closed.
    if (errorsTimestamp == 0L || currentTimestamp - errorsTimestamp > ERROR_REPORT_INTERVAL || IS_TESTING) {
      errorsTimestamp = currentTimestamp;
      Analytics analytics = FlutterInitializer.getAnalytics();
      LOG.debug(DAS_STATUS_EVENT_TYPE + " " + errorCount + " " + warningCount + " " + hintCount + " " + lintCount);
      analytics.disableThrottling(() -> {
        if (errorCount > 0) {
          analytics.sendEventMetric(DAS_STATUS_EVENT_TYPE, ERRORS, errorCount); // test: serverStatus()
        }
        if (warningCount > 0) {
          analytics.sendEventMetric(DAS_STATUS_EVENT_TYPE, WARNINGS, warningCount); // test: serverStatus()
        }
        if (hintCount > 0) {
          analytics.sendEventMetric(DAS_STATUS_EVENT_TYPE, HINTS, hintCount); // test: serverStatus()
        }
        if (lintCount > 0) {
          analytics.sendEventMetric(DAS_STATUS_EVENT_TYPE, LINTS, lintCount); // test: serverStatus()
        }
      });
      errorCount = warningCount = hintCount = lintCount = 0;
    }
  }

  private static int extractCount(@NotNull Map<String, Integer> errorCounts, String name) {
    //noinspection Java8MapApi,ConstantConditions
    return errorCounts.containsKey(name) ? errorCounts.get(name) : 0;
  }

  @Override
  public void computedExistingImports(String file, Map<String, Map<String, Set<String>>> existingImports) {
    // No start time is recorded.
  }

  private void logCompletion(@NotNull String selection, int prefixLength, @NotNull String eventType) {
    maybeReport(true, (analytics) -> {
      LOG.debug(eventType + " " + selection + " " + prefixLength);
      analytics.sendEventMetric(eventType, selection, prefixLength); // test: acceptedCompletion(), lookupCanceled()
    });
  }

  void logE2ECompletionSuccessMS(long e2eCompletionMS) {
    maybeReport(true, (analytics) -> {
      LOG.debug(E2E_IJ_COMPLETION_TIME + " " + SUCCESS + " " + e2eCompletionMS);
      analytics.sendTiming(E2E_IJ_COMPLETION_TIME, SUCCESS, e2eCompletionMS); // test: logE2ECompletionSuccessMS()
    });
  }

  void logE2ECompletionErrorMS(long e2eCompletionMS) {
    maybeReport(true, (analytics) -> {
      LOG.debug(E2E_IJ_COMPLETION_TIME + " " + FAILURE + " " + e2eCompletionMS);
      analytics.sendTiming(E2E_IJ_COMPLETION_TIME, FAILURE, e2eCompletionMS); // test: logE2ECompletionErrorMS()
    });
  }

  private void maybeLogInitialAnalysisTime(@NotNull String eventType, @NotNull String path, @NotNull Map<String, Instant> pathToStartTime) {
    if (!pathToStartTime.containsKey(path)) {
      return;
    }

    logFileAnalysisTime(eventType, path, Objects.requireNonNull(
      Duration.between(Objects.requireNonNull(pathToStartTime.get(path)), Instant.now())).toMillis());
    pathToStartTime.remove(path);
  }

  private void logFileAnalysisTime(@NotNull String kind, String path, long analysisTime) {
    maybeReport(false, (analytics) -> {
      LOG.debug(kind + " " + DURATION + " " + analysisTime);
      analytics.sendEvent(kind, DURATION, "", Long.toString(analysisTime)); // test: computedErrors()
    });
  }

  /**
   * Observe when the active {@link LookupImpl} changes and register the {@link
   * LookupSelectionHandler} on any new instances.
   */
  void onPropertyChange(@NotNull PropertyChangeEvent propertyChangeEvent) {
    Object newValue = propertyChangeEvent.getNewValue();
    if (!(newValue instanceof LookupImpl)) {
      return;
    }

    setLookupSelectionHandler();
    LookupImpl lookup = (LookupImpl)newValue;
    lookup.addLookupListener(lookupSelectionHandler);
  }

  @VisibleForTesting
  void setLookupSelectionHandler() {
    this.lookupSelectionHandler = new LookupSelectionHandler();
  }

  class LookupSelectionHandler implements LookupListener {
    @Override
    public void lookupCanceled(@NotNull LookupEvent event) {
      if (event.isCanceledExplicitly() && isDartLookupEvent(event)) {
        logCompletion(UNKNOWN_LOOKUP_STRING, -1, REJECTED_COMPLETION); // test: lookupCanceled()
      }
    }

    @Override
    public void itemSelected(@NotNull LookupEvent event) {
      if (event.getItem() == null) {
        return;
      }
      String selection = event.getItem().getLookupString();
      LookupImpl lookup = (LookupImpl)event.getLookup();
      assert lookup != null;
      int prefixLength = lookup.getPrefixLength(event.getItem());

      if (isDartLookupEvent(event)) {
        logCompletion(selection, prefixLength, ACCEPTED_COMPLETION); // test: acceptedCompletion()
      }
    }

    @Override
    public void currentItemChanged(@NotNull LookupEvent event) {
    }

    private boolean isDartLookupEvent(@NotNull LookupEvent event) {
      LookupImpl lookup = (LookupImpl)event.getLookup();
      return lookup != null &&
             lookup.getPsiFile() != null &&
             lookup.getPsiFile().getVirtualFile() != null &&
             FileUtils.isDartFile(Objects.requireNonNull(lookup.getPsiFile().getVirtualFile()));
    }
  }

  private class QuickFixListener implements DartQuickFixListener {
    @Override
    public void beforeQuickFixInvoked(@NotNull DartQuickFix intention, @NotNull Editor editor, @NotNull PsiFile file) {
      maybeReport(true, (analytics) -> {
        String path = Objects.requireNonNull(file.getVirtualFile()).getPath();
        int lineNumber = editor.getCaretModel().getLogicalPosition().line + 1;
        @SuppressWarnings("ConstantConditions")
        List<String> errorsOnLine =
          pathToErrors.containsKey(path) ? pathToErrors.get(path).stream().filter(error -> error.getLocation().getStartLine() == lineNumber)
            .map(AnalysisError::getCode).collect(Collectors.toList()) : ImmutableList.of();
        LOG.debug(QUICK_FIX + " " + intention.getText() + " " + errorsOnLine.size());
        analytics.sendEventMetric(QUICK_FIX, intention.getText(), errorsOnLine.size()); // test: quickFix()
      });
    }
  }

  class FlutterRequestListener implements RequestListener {
    @Override
    public void onRequest(String jsonString) {
      JsonObject request = new Gson().fromJson(jsonString, JsonObject.class);
      //noinspection ConstantConditions
      RequestDetails details = new RequestDetails(request.get("method").getAsString(), Instant.now());
      String id = Objects.requireNonNull(request.get("id")).getAsString();
      requestToDetails.put(id, details);
    }
  }

  @SuppressWarnings("LocalCanBeFinal")
  class FlutterResponseListener implements ResponseListener {
    final Map<String, Long> methodTimestamps = new HashMap<>();

    @Override
    public void onResponse(String jsonString) {
      JsonObject response = new Gson().fromJson(jsonString, JsonObject.class);
      if (response == null) return;
      if (safelyGetString(response, "event").equals("server.log")) {
        JsonObject serverLogEntry = Objects.requireNonNull(response.getAsJsonObject("params")).getAsJsonObject("entry");
        if (serverLogEntry != null) {
          maybeReport(true, (analytics) -> {
            String sdkVersionValue = safelyGetString(serverLogEntry, LOG_ENTRY_SDK_VERSION);
            ImmutableMap<String, String> map;

            @SuppressWarnings("ConstantConditions")
            String logEntry =
              String.format("%s|%s|%s|%s|%s|%s", LOG_ENTRY_TIME, serverLogEntry.get(LOG_ENTRY_TIME).getAsInt(),
                            LOG_ENTRY_KIND, serverLogEntry.get(LOG_ENTRY_KIND).getAsString(), LOG_ENTRY_DATA,
                            serverLogEntry.get(LOG_ENTRY_DATA).getAsString());
            assert logEntry != null;
            LOG.debug(ANALYSIS_SERVER_LOG + " " + logEntry);
            // Log the "sdkVersion" only if it was provided in the event
            if (StringUtil.isEmpty(sdkVersionValue)) {
              analytics.sendEvent(ANALYSIS_SERVER_LOG, logEntry); // test: dasListenerLogging()
            }
            else { // test: dasListenerLoggingWithSdk()
              analytics.sendEventWithSdk(ANALYSIS_SERVER_LOG, logEntry, sdkVersionValue);
            }
          });
        }
      }
      if (response.get("id") == null) {
        return;
      }
      //noinspection ConstantConditions
      String id = response.get("id").getAsString();
      RequestDetails details = requestToDetails.get(id);
      if (details != null) {
        if (MANUALLY_MANAGED_METHODS.contains(details.method())) {
          return;
        }
        Long timestamp = methodTimestamps.get(details.method());
        long currentTimestamp = System.currentTimeMillis();
        // Throttle to one report per interval for each distinct details.method().
        if (timestamp == null || currentTimestamp - timestamp > GENERAL_REPORT_INTERVAL) {
          methodTimestamps.put(details.method(), currentTimestamp);
          LOG.debug(ROUND_TRIP_TIME + " " + details.method() + " " + Duration.between(details.startTime(), Instant.now()).toMillis());
          FlutterInitializer.getAnalytics()
            .sendTiming(ROUND_TRIP_TIME, details.method(), // test: dasListenerTiming()
                        Objects.requireNonNull(Duration.between(details.startTime(), Instant.now())).toMillis());
        }
      }
      requestToDetails.remove(id);
    }
  }
}
