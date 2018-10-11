/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

/**
 * Class for accumulating sliding window performance stats optimized for fast
 * performance and stable memory usage.
 */
class SlidingWindowStats {
  // This lets as track a bit over 3 seconds at 60fps.
  // TODO(jacobr): consider a longer sliding window length
  // if we care about replaying historic stats for a longer
  // period of time.
  static final int _windowLength = 200 * 2;
  /// Array of timestamp followed by count.
  final int[] _window;
  int _next = 0;
  int _start = 0;

  int _total = 0;
  int _totalSinceNavigation = 0;

  SlidingWindowStats() {
    _window = new int[_windowLength];
  }

  int getTotal() {
    return _total;
  }

  int getTotalSinceNavigation() {
    return _totalSinceNavigation;
  }

  void clear() {
    _next = 0;
    _start = 0;
    _total = 0;
    _totalSinceNavigation = 0;
  }

  void onNavigation() {
    _totalSinceNavigation = 0;
  }

  int getTotalWithinWindow(int windowStart) {
    if (_next == _start) {
      return 0;
    }
    final int end = _start >= 0 ? _start : _next;
    int i = _next;
    int count = 0;
    while (true) {
      i -= 2;
      if (i < 0) {
        i += _windowLength;
      }

      if (_window[i] < windowStart) {
        break;
      }
      count += _window[i + 1];
      if (i == end) {
        break;
      }
    }
    return count;
  }

  void add(int count, int timeStamp) {
    _total += count;
    _totalSinceNavigation += count;
    if (_start != _next) {
      int last = _next - 2;
      if (last < 0) {
        last += _windowLength;
      }
      final int lastTimeStamp = _window[last];
      if (lastTimeStamp == timeStamp) {
        _window[last + 1] += count;
        return;
      }
      // The sliding window assumes timestamps must be given in increasing
      // order.
      assert (lastTimeStamp < timeStamp);
    }
    _window[_next] = timeStamp;
    _window[_next + 1] = count;
    _next += 2;
    if (_next == _windowLength) {
      _next = 0;
    }
    if (_start == _next) {
      // The entire sliding window is now full so no need
      // to track an explicit start.
      _start = -1;
    }
  }
}
