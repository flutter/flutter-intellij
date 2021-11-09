package io.flutter.vmService;

import gnu.trove.THashMap;
import org.dartlang.vm.service.element.Isolate;
import org.dartlang.vm.service.element.IsolateRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class IsolatesInfo {

  public static class IsolateInfo {
    private final IsolateRef myIsolateRef;
    private boolean breakpointsSet = false;
    private boolean shouldInitialResume = false;
    private CompletableFuture<Isolate> myCachedIsolate;

    private IsolateInfo(@NotNull IsolateRef isolateRef) {
      this.myIsolateRef = isolateRef;
    }

    void invalidateCache() {
      myCachedIsolate = null;
    }

    CompletableFuture<Isolate> getCachedIsolate() {
      return myCachedIsolate;
    }

    void setCachedIsolate(CompletableFuture<Isolate> cachedIsolate) {
      myCachedIsolate = cachedIsolate;
    }

    public IsolateRef getIsolateRef() {
      return myIsolateRef;
    }

    public String getIsolateId() {
      return myIsolateRef.getId();
    }

    public String getIsolateName() {
      return myIsolateRef.getName();
    }

    public String toString() {
      return getIsolateId() + ": breakpointsSet=" + breakpointsSet + ", shouldInitialResume=" + shouldInitialResume;
    }
  }

  private final Map<String, IsolateInfo> myIsolateIdToInfoMap = new THashMap<>();

  public synchronized boolean addIsolate(@NotNull final IsolateRef isolateRef) {
    if (myIsolateIdToInfoMap.containsKey(isolateRef.getId())) {
      return false;
    }

    myIsolateIdToInfoMap.put(isolateRef.getId(), new IsolateInfo(isolateRef));

    return true;
  }

  public synchronized void setBreakpointsSet(@NotNull final IsolateRef isolateRef) {
    final IsolateInfo info = myIsolateIdToInfoMap.get(isolateRef.getId());
    if (info != null) {
      info.breakpointsSet = true;
    }
  }

  public synchronized void setShouldInitialResume(@NotNull final IsolateRef isolateRef) {
    final IsolateInfo info = myIsolateIdToInfoMap.get(isolateRef.getId());
    if (info != null) {
      info.shouldInitialResume = true;
    }
  }

  public synchronized boolean getShouldInitialResume(@NotNull final IsolateRef isolateRef) {
    final IsolateInfo info = myIsolateIdToInfoMap.get(isolateRef.getId());
    if (info != null) {
      return info.breakpointsSet && info.shouldInitialResume;
    }
    else {
      return false;
    }
  }

  public synchronized void deleteIsolate(@NotNull final IsolateRef isolateRef) {
    myIsolateIdToInfoMap.remove(isolateRef.getId());
  }

  public synchronized void invalidateCache(String isolateId) {
    final IsolateInfo info = myIsolateIdToInfoMap.get(isolateId);
    if (info != null) {
      info.invalidateCache();
    }
  }

  public synchronized CompletableFuture<Isolate> getCachedIsolate(String isolateId, Supplier<CompletableFuture<Isolate>> isolateSupplier) {
    final IsolateInfo info = myIsolateIdToInfoMap.get(isolateId);
    if (info == null) {
      return CompletableFuture.completedFuture(null);
    }
    CompletableFuture<Isolate> cachedIsolate = info.getCachedIsolate();
    if (cachedIsolate != null) {
      return cachedIsolate;
    }
    cachedIsolate = isolateSupplier.get();
    info.setCachedIsolate(cachedIsolate);
    return cachedIsolate;
  }

  public synchronized Collection<IsolateInfo> getIsolateInfos() {
    return new ArrayList<>(myIsolateIdToInfoMap.values());
  }
}
