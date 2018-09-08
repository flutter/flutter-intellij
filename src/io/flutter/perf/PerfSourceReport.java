/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

public class PerfSourceReport {
  private final PerfReportKind kind;
  private final JsonArray entries;

  public PerfSourceReport(JsonArray entries, PerfReportKind kind) {
    this.entries = entries;
    this.kind = kind;
  }

  PerfReportKind getKind() {
    return kind;
  }

  List<Entry> getEntries() {
    final ArrayList<Entry> ret = new ArrayList<>(entries.size());
    for (JsonElement entryJson : entries) {
      ret.add(new Entry(entryJson.getAsJsonArray()));
    }
    return ret;
  }

  class Entry {
    public final int line;
    public final int column;
    public final int total;
    public final int pastSecond;

    Entry(JsonArray entry) {
      assert entry.size() == 4;
      line = entry.get(0).getAsInt();
      column = entry.get(1).getAsInt();
      total = entry.get(2).getAsInt();
      pastSecond = entry.get(3).getAsInt();
    }
  }
}
