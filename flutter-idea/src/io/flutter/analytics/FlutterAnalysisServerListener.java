package io.flutter.analytics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.dart.server.AnalysisServerListenerAdapter;
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.fixes.DartQuickFix;
import com.jetbrains.lang.dart.fixes.DartQuickFixListener;
import io.flutter.FlutterInitializer;
import io.flutter.utils.FileUtils;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("LocalCanBeFinal")
public final class FlutterAnalysisServerListener extends AnalysisServerListenerAdapter implements Disposable {
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

  // variables to throttle certain, high frequency events
  @SuppressWarnings("ConstantConditions")
  @NotNull private static final Duration INTERVAL_TO_REPORT_MEMORY_USAGE = Duration.ofHours(1);
  @NotNull final RequestListener requestListener;
  @NotNull final ResponseListener responseListener;
  @NotNull final DartQuickFixListener quickFixListener;
  // instance members
  @NotNull private final Project project;
  @NotNull private final Map<String, List<AnalysisError>> pathToErrors;
  @NotNull private final Map<String, Instant> pathToErrorTimestamps;
  @NotNull private final Map<String, Instant> pathToHighlightTimestamps;
  @NotNull private final Map<String, Instant> pathToOutlineTimestamps;
  @NotNull private final Map<String, RequestDetails> requestToDetails;
  @NotNull private final FileEditorManagerListener fileEditorManagerListener;
  LookupSelectionHandler lookupSelectionHandler;
  @NotNull private Instant nextMemoryUsageLoggedInstant = Instant.EPOCH;

  FlutterAnalysisServerListener(@NotNull Project project) {
    this.project = project;
    this.pathToErrors = new HashMap<>();
    this.pathToErrorTimestamps = new HashMap<>();
    this.pathToHighlightTimestamps = new HashMap<>();
    this.pathToOutlineTimestamps = new HashMap<>();
    this.requestToDetails = new HashMap<>();
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
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorManagerListener);
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
    LookupManager.getInstance(project).removePropertyChangeListener(this::onPropertyChange);
  }

  @Override
  public void computedAnalyzedFiles(List<String> list) {
  }

  @Override
  public void computedAvailableSuggestions(@NotNull List<AvailableSuggestionSet> list, int[] ints) {
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
  }

  @Override
  public void computedErrors(String path, List<AnalysisError> list) {
    assert list != null;
    List<AnalysisError> existing = pathToErrors.get(path);
    if (existing != null && existing.equals(list)) {
      return;
    }
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
  }

  @Override
  public void computedLaunchData(String s, String s1, String[] strings) {
  }

  @Override
  public void computedNavigation(String s, List<NavigationRegion> list) {
  }

  @Override
  public void computedOccurrences(String s, List<Occurrences> list) {
  }

  @Override
  public void computedOutline(String path, Outline outline) {
    assert path != null;
    maybeLogInitialAnalysisTime(INITIAL_OUTLINE_TIME, path, pathToOutlineTimestamps);
  }

  @Override
  public void computedOverrides(String s, List<OverrideMember> list) {
  }

  @Override
  public void computedClosingLabels(String s, List<ClosingLabel> list) {
  }

  @Override
  public void computedSearchResults(String s, List<SearchResult> list, boolean b) {
  }

  @Override
  public void flushedResults(List<String> list) {
  }

  @Override
  public void requestError(RequestError requestError) {
    assert requestError != null;
    String code = requestError.getCode();
    if (code == null) {
      code = requestError.getMessage(); // test: requestErrorNoCode()
    }
    String stack = requestError.getStackTrace();
    String exception = composeException(ERROR_TYPE_REQUEST, code, stack);
    FlutterInitializer.getAnalytics().sendException(exception, false); // test: requestError()
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
    String exception = composeException(ERROR_TYPE_SERVER, message, stackTraceString);
    FlutterInitializer.getAnalytics().sendException(exception, isFatal); // test: serverError()
  }

  @Override
  public void serverIncompatibleVersion(String s) {
  }

  @Override
  public void serverStatus(AnalysisStatus analysisStatus, PubStatus pubStatus) {
    assert analysisStatus != null;
    if (!analysisStatus.isAnalyzing()) {
      @NotNull HashMap<String, Integer> errorCounts = getTotalAnalysisErrorCounts();
      int errorCount = 0;
      errorCount += extractCount(errorCounts, AnalysisErrorType.CHECKED_MODE_COMPILE_TIME_ERROR);
      errorCount += extractCount(errorCounts, AnalysisErrorType.COMPILE_TIME_ERROR);
      errorCount += extractCount(errorCounts, AnalysisErrorType.SYNTACTIC_ERROR);
      FlutterInitializer.getAnalytics().sendEventMetric(DAS_STATUS_EVENT_TYPE, ERRORS, errorCount); // test: serverStatus()
      int warningCount = 0;
      warningCount += extractCount(errorCounts, AnalysisErrorType.STATIC_TYPE_WARNING);
      warningCount += extractCount(errorCounts, AnalysisErrorType.STATIC_WARNING);
      FlutterInitializer.getAnalytics().sendEventMetric(DAS_STATUS_EVENT_TYPE, WARNINGS, warningCount); // test: serverStatus()
      int hintCount = extractCount(errorCounts, AnalysisErrorType.HINT);
      FlutterInitializer.getAnalytics().sendEventMetric(DAS_STATUS_EVENT_TYPE, HINTS, hintCount); // test: serverStatus()
      int lintCount = extractCount(errorCounts, AnalysisErrorType.LINT);
      FlutterInitializer.getAnalytics().sendEventMetric(DAS_STATUS_EVENT_TYPE, LINTS, lintCount); // test: serverStatus()
    }
  }

  private static int extractCount(@NotNull Map<String, Integer> errorCounts, String name) {
    //noinspection Java8MapApi,ConstantConditions
    return errorCounts.containsKey(AnalysisErrorType.CHECKED_MODE_COMPILE_TIME_ERROR) ? errorCounts.get(
      AnalysisErrorType.CHECKED_MODE_COMPILE_TIME_ERROR) : 0;
  }

  @Override
  public void computedExistingImports(String file, Map<String, Map<String, Set<String>>> existingImports) {
  }

  private static void logCompletion(@NotNull String selection, int prefixLength, @NotNull String eventType) {
    FlutterInitializer.getAnalytics().sendEventMetric(eventType, selection, prefixLength); // test: acceptedCompletion(), lookupCanceled()
  }

  void logE2ECompletionSuccessMS(long e2eCompletionMS) {
    FlutterInitializer.getAnalytics().sendTiming(E2E_IJ_COMPLETION_TIME, SUCCESS, e2eCompletionMS); // test: logE2ECompletionSuccessMS()
  }

  void logE2ECompletionErrorMS(long e2eCompletionMS) {
    FlutterInitializer.getAnalytics().sendTiming(E2E_IJ_COMPLETION_TIME, FAILURE, e2eCompletionMS); // test: logE2ECompletionErrorMS()
  }
  //
  //private void logAnalysisError(@Nullable AnalysisError error) {
  //  if (error != null && (computedErrorCounter++ % COMPUTED_ERROR_SAMPLE_RATE) == 0) {
  //    FlutterInitializer.getAnalytics().sendEvent( // test: computedErrors()
  //      COMPUTED_ERROR,
  //      Objects.requireNonNull(error.getCode()), "", Long.toString(Objects.requireNonNull(Instant.now()).toEpochMilli()));
  //  }
  //}

  private void maybeLogInitialAnalysisTime(@NotNull String eventType, @NotNull String path, @NotNull Map<String, Instant> pathToStartTime) {
    if (!pathToStartTime.containsKey(path)) {
      return;
    }

    logFileAnalysisTime(eventType, path, Objects.requireNonNull(
      Duration.between(Objects.requireNonNull(pathToStartTime.get(path)), Instant.now())).toMillis());
    pathToStartTime.remove(path);
  }

  private void logFileAnalysisTime(@NotNull String kind, String path, long analysisTime) {
    FlutterInitializer.getAnalytics().sendEvent(kind, DURATION, "", Long.toString(analysisTime)); // test: computedErrors()
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

    this.lookupSelectionHandler = new LookupSelectionHandler();
    LookupImpl lookup = (LookupImpl)newValue;
    lookup.addLookupListener(lookupSelectionHandler);
  }

  static class LookupSelectionHandler implements LookupListener {
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

    private static boolean isDartLookupEvent(@NotNull LookupEvent event) {
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
      String path = Objects.requireNonNull(file.getVirtualFile()).getPath();
      int lineNumber = editor.getCaretModel().getLogicalPosition().line + 1;
      @SuppressWarnings("ConstantConditions")
      List<String> errorsOnLine =
        pathToErrors.containsKey(path) ? pathToErrors.get(path).stream().filter(error -> error.getLocation().getStartLine() == lineNumber)
          .map(AnalysisError::getCode).collect(Collectors.toList()) : ImmutableList.of();
      FlutterInitializer.getAnalytics().sendEventMetric(QUICK_FIX, intention.getText(), errorsOnLine.size()); // test: quickFix()
    }
  }

  class FlutterRequestListener implements RequestListener {
    @Override
    public void onRequest(String jsonString) {
      JsonObject request = new Gson().fromJson(jsonString, JsonObject.class);
      @SuppressWarnings("ConstantConditions") RequestDetails details =
        new RequestDetails(request.get("method").getAsString(), Instant.now());
      String id = Objects.requireNonNull(request.get("id")).getAsString();
      requestToDetails.put(id, details);
    }
  }

  @SuppressWarnings("LocalCanBeFinal")
  class FlutterResponseListener implements ResponseListener {
    @Override
    public void onResponse(String jsonString) {
      JsonObject response = new Gson().fromJson(jsonString, JsonObject.class);
      if (response == null) return;
      if (safelyGetString(response, "event").equals("server.log")) {
        JsonObject serverLogEntry = Objects.requireNonNull(response.getAsJsonObject("params")).getAsJsonObject("entry");
        if (serverLogEntry != null) {
          String sdkVersionValue = safelyGetString(serverLogEntry, LOG_ENTRY_SDK_VERSION);
          ImmutableMap<String, String> map;

          @SuppressWarnings("ConstantConditions")
          String logEntry =
            String.format("%s|%s|%s|%s|%s|%s", LOG_ENTRY_TIME, serverLogEntry.get(LOG_ENTRY_TIME).getAsInt(),
                          LOG_ENTRY_KIND, serverLogEntry.get(LOG_ENTRY_KIND).getAsString(), LOG_ENTRY_DATA,
                          serverLogEntry.get(LOG_ENTRY_DATA).getAsString());
          assert logEntry != null;
          // Log the "sdkVersion" only if it was provided in the event
          if (StringUtil.isEmpty(sdkVersionValue)) {
            FlutterInitializer.getAnalytics().sendEvent(ANALYSIS_SERVER_LOG, logEntry); // test: dasListenerLogging()
          }
          else { // test: dasListenerLoggingWithSdk()
            FlutterInitializer.getAnalytics().sendEventWithSdk(ANALYSIS_SERVER_LOG, logEntry, sdkVersionValue);
          }
        }
      }
      if (response.get("id") == null) {
        return;
      }
      //noinspection ConstantConditions
      String id = response.get("id").getAsString();
      RequestDetails details = requestToDetails.get(id);
      if (details != null) {
        FlutterInitializer.getAnalytics()
          .sendTiming(ROUND_TRIP_TIME, details.method(), // test: dasListenerTiming()
                      Objects.requireNonNull(Duration.between(details.startTime(), Instant.now())).toMillis());
      }
      requestToDetails.remove(id);
    }
  }
}
