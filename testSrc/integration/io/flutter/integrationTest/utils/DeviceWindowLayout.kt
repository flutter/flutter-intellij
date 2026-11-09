/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.ide.starter.ide.IDETestContext
import java.awt.Frame
import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit

/**
 * Tiles IntelliJ IDEA and the running device window (Chrome or Android emulator) side by side so that both the IDE and
 * the live Flutter app are visible at the same time in walkthrough screenshots.
 *
 * Without a side-by-side layout, IntelliJ opens centered on the display and covers Chrome or the Android emulator
 * (especially on bare Xvfb or Xephyr, where there is no window manager).
 *
 * Layout:
 * - **Left ([IDE_WIDTH_PERCENT]% of display width, e.g. `1248x1080` on `1920x1080`):** IntelliJ IDEA, positioned via
 *   [IdeaFrameUI.setBounds] on all platforms.
 * - **Right (remaining width, e.g. `672x1080` at `x = 1248`):** Chrome or the Android emulator:
 *   - For Chrome on Linux and macOS, sets `CHROME_EXECUTABLE` on the IDE process to a wrapper script that passes
 *     `--window-position` and `--window-size` (plus `--ozone-platform=x11` on Linux X11 displays so Chrome opens on the
 *     test's `DISPLAY` instead of escaping to a host Wayland session).
 *   - On [Platform.DisplayMode.LINUX_X11] (walkthrough recordings), also runs a lightweight background X11 window
 *     watcher (`python3` + `libX11.so.6`) that resizes Chrome into the right column and moves Android emulator windows
 *     into the right column without distorting the phone's aspect ratio.
 */
class DeviceWindowLayout : AutoCloseable {

  private var watcherProcess: Process? = null
  private var chromeWrapper: File? = null
  private var screenBounds: Rectangle = Rectangle(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT)

  private val ideBounds: Rectangle
    get() {
      val ideWidth = (screenBounds.width * IDE_WIDTH_PERCENT) / 100
      return Rectangle(screenBounds.x, screenBounds.y, ideWidth, screenBounds.height)
    }

  private val deviceBounds: Rectangle
    get() {
      val ide = ideBounds
      return Rectangle(ide.x + ide.width, screenBounds.y, screenBounds.width - ide.width, screenBounds.height)
    }

  /**
   * Detects the display size and, on [Platform.DisplayMode.LINUX_X11], starts the background X11 tiler before any
   * emulator or browser window opens. Logs go to `device-window-layout.log` in [workDir].
   */
  fun start(workDir: File) {
    Files.createDirectories(workDir.toPath())
    if (Platform.displayMode == Platform.DisplayMode.LINUX_X11) {
      queryX11ScreenSize()?.let { (w, h) -> screenBounds = Rectangle(0, 0, w, h) }
      val dev = deviceBounds
      watcherProcess = runCatching {
        ProcessBuilder(
          "python3", "-u", "-c", X11_TILER_SCRIPT,
          dev.x.toString(), dev.y.toString(), dev.width.toString(), dev.height.toString(),
        )
          .redirectErrorStream(true)
          .redirectOutput(File(workDir, "device-window-layout.log"))
          .start()
      }.onFailure {
        println("Could not start X11 device window tiler: ${it.message}")
      }.getOrNull()
    }
    prepareChromeWrapper(workDir)
  }

  /**
   * Configures the IDE process environment before launch so `flutter run -d chrome` opens Chrome on the test's display
   * in the right-hand column.
   */
  fun configureIde(context: IDETestContext) {
    val isLinuxX11 = Platform.displayMode == Platform.DisplayMode.LINUX_X11 ||
                     Platform.displayMode == Platform.DisplayMode.LINUX_STARTER_XVFB
    val wrapper = chromeWrapper
    if (!isLinuxX11 && wrapper == null) return

    context.applyVMOptionsPatch {
      if (isLinuxX11) {
        // Keep Wayland-capable tools (such as Chrome with --ozone-platform-hint=auto) on the test's X11 display even
        // when the outer shell was started from a Wayland session with XDG_SESSION_TYPE=wayland.
        withEnv("XDG_SESSION_TYPE", "x11")
      }
      if (wrapper != null) {
        withEnv("CHROME_EXECUTABLE", wrapper.absolutePath)
      }
    }
  }

  /** Moves and resizes the IntelliJ project frame [frame] into the left-hand column of the display. */
  fun tileIdeFrame(frame: IdeaFrameUI) {
    val detected = runCatching {
      frame.driver.withContext(OnDispatcher.EDT) {
        if (frame.isMaximized) {
          frame.driver.cast(frame.component, FrameRef::class).setExtendedState(Frame.NORMAL)
        }
        frame.driver.utility(GraphicsEnvironmentRef::class)
          .getLocalGraphicsEnvironment()
          .getMaximumWindowBounds()
      }
    }.getOrNull()
    if (detected != null && detected.width >= MIN_SIDE_BY_SIDE_WIDTH && detected.height >= MIN_SIDE_BY_SIDE_HEIGHT) {
      screenBounds = detected
    }
    val target = ideBounds
    frame.setBounds(target)
    println("Tiled IDE frame to (${target.x}, ${target.y}, ${target.width}x${target.height}); " +
            "device pane is (${deviceBounds.x}, ${deviceBounds.y}, ${deviceBounds.width}x${deviceBounds.height})")
  }

  override fun close() {
    watcherProcess?.let { proc ->
      proc.destroy()
      runCatching { proc.waitFor(3, TimeUnit.SECONDS) }
      if (proc.isAlive) proc.destroyForcibly()
    }
    watcherProcess = null
    chromeWrapper?.delete()
    chromeWrapper = null
  }

  private fun prepareChromeWrapper(workDir: File) {
    val mode = Platform.displayMode
    val usePosixWrapper = mode == Platform.DisplayMode.LINUX_X11 ||
                          mode == Platform.DisplayMode.LINUX_STARTER_XVFB ||
                          mode == Platform.DisplayMode.MAC_DESKTOP
    if (!usePosixWrapper) return

    val chrome = Platform.findChromeExecutable() ?: return
    val dev = deviceBounds
    val extraFlags = buildList {
      if (mode != Platform.DisplayMode.MAC_DESKTOP) add("--ozone-platform=x11")
      add("--window-position=${dev.x},${dev.y}")
      add("--window-size=${dev.width},${dev.height}")
    }.joinToString(" ")

    val escapedChrome = "'" + chrome.replace("'", "'\\''") + "'"
    val wrapper = File(workDir, "chrome-tiled-${System.nanoTime()}.sh")
    wrapper.writeText(
      """
      |#!/bin/sh
      |for arg in "$@"; do
      |  case "${'$'}arg" in
      |    --version|-v|--product-version) exec $escapedChrome "$@" ;;
      |  esac
      |done
      |exec $escapedChrome $extraFlags "$@"
      |""".trimMargin()
    )
    runCatching {
      Files.setPosixFilePermissions(
        wrapper.toPath(),
        setOf(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE,
        ),
      )
    }.onFailure { wrapper.setExecutable(true) }
    chromeWrapper = wrapper
  }

  private fun queryX11ScreenSize(): Pair<Int, Int>? = runCatching {
    val proc = ProcessBuilder("python3", "-c", X11_SCREEN_SIZE_SCRIPT).start()
    if (!proc.waitFor(5, TimeUnit.SECONDS) || proc.exitValue() != 0) return null
    val parts = proc.inputStream.bufferedReader().readText().trim().split(" ")
    if (parts.size == 2) Pair(parts[0].toInt(), parts[1].toInt()) else null
  }.getOrNull()

  companion object {
    private const val DEFAULT_WIDTH = 1920
    private const val DEFAULT_HEIGHT = 1080
    private const val IDE_WIDTH_PERCENT = 65
    private const val MIN_SIDE_BY_SIDE_WIDTH = 1200
    private const val MIN_SIDE_BY_SIDE_HEIGHT = 700

    private val X11_SCREEN_SIZE_SCRIPT = """
      import ctypes, sys
      x11 = ctypes.CDLL("libX11.so.6")
      x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
      x11.XOpenDisplay.restype = ctypes.c_void_p
      x11.XDefaultScreen.argtypes = [ctypes.c_void_p]
      x11.XDefaultScreen.restype = ctypes.c_int
      x11.XDisplayWidth.argtypes = [ctypes.c_void_p, ctypes.c_int]
      x11.XDisplayWidth.restype = ctypes.c_int
      x11.XDisplayHeight.argtypes = [ctypes.c_void_p, ctypes.c_int]
      x11.XDisplayHeight.restype = ctypes.c_int
      x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
      dpy = x11.XOpenDisplay(None)
      if not dpy:
          sys.exit(1)
      scr = x11.XDefaultScreen(dpy)
      print(x11.XDisplayWidth(dpy, scr), x11.XDisplayHeight(dpy, scr))
      x11.XCloseDisplay(dpy)
    """.trimIndent()

    private val X11_TILER_SCRIPT = """
      import ctypes, os, signal, sys, time

      dev_x, dev_y, dev_w, dev_h = [int(a) for a in sys.argv[1:5]]

      class XWindowAttributes(ctypes.Structure):
          _fields_ = [
              ("x", ctypes.c_int), ("y", ctypes.c_int),
              ("width", ctypes.c_int), ("height", ctypes.c_int),
              ("border_width", ctypes.c_int), ("depth", ctypes.c_int),
              ("visual", ctypes.c_void_p), ("root", ctypes.c_ulong),
              ("c_class", ctypes.c_int), ("bit_gravity", ctypes.c_int),
              ("win_gravity", ctypes.c_int), ("backing_store", ctypes.c_int),
              ("backing_planes", ctypes.c_ulong), ("backing_pixel", ctypes.c_ulong),
              ("save_under", ctypes.c_int), ("colormap", ctypes.c_ulong),
              ("map_installed", ctypes.c_int), ("map_state", ctypes.c_int),
              ("all_event_masks", ctypes.c_long), ("your_event_mask", ctypes.c_long),
              ("do_not_propagate_mask", ctypes.c_long), ("override_redirect", ctypes.c_int),
              ("screen", ctypes.c_void_p),
          ]

      class XClassHint(ctypes.Structure):
          _fields_ = [("res_name", ctypes.c_void_p), ("res_class", ctypes.c_void_p)]

      x11 = ctypes.CDLL("libX11.so.6")
      ERR_HANDLER = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_void_p, ctypes.c_void_p)
      _err_cb = ERR_HANDLER(lambda d, e: 0)
      x11.XSetErrorHandler(_err_cb)

      x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
      x11.XOpenDisplay.restype = ctypes.c_void_p
      x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
      x11.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
      x11.XDefaultRootWindow.restype = ctypes.c_ulong
      x11.XQueryTree.argtypes = [
          ctypes.c_void_p, ctypes.c_ulong,
          ctypes.POINTER(ctypes.c_ulong), ctypes.POINTER(ctypes.c_ulong),
          ctypes.POINTER(ctypes.POINTER(ctypes.c_ulong)), ctypes.POINTER(ctypes.c_uint),
      ]
      x11.XGetWindowAttributes.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.POINTER(XWindowAttributes)]
      x11.XGetClassHint.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.POINTER(XClassHint)]
      x11.XGetTransientForHint.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.POINTER(ctypes.c_ulong)]
      x11.XFetchName.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.POINTER(ctypes.c_void_p)]
      x11.XMoveResizeWindow.argtypes = [
          ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_int, ctypes.c_uint, ctypes.c_uint,
      ]
      x11.XMoveWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_int]
      x11.XRaiseWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
      x11.XFlush.argtypes = [ctypes.c_void_p]
      x11.XFree.argtypes = [ctypes.c_void_p]

      running = True
      def _stop(sig, frame):
          global running
          running = False
      signal.signal(signal.SIGTERM, _stop)
      signal.signal(signal.SIGINT, _stop)

      dpy = x11.XOpenDisplay(None)
      if not dpy:
          print("XOpenDisplay failed")
          sys.exit(1)
      root = x11.XDefaultRootWindow(dpy)
      print(f"Watching X11 root 0x{root:x}; device pane=({dev_x}, {dev_y}, {dev_w}x{dev_h})")

      def get_class(win):
          ch = XClassHint()
          parts = []
          if x11.XGetClassHint(dpy, win, ctypes.byref(ch)):
              if ch.res_name:
                  parts.append(ctypes.string_at(ch.res_name).decode("utf-8", "ignore"))
                  x11.XFree(ch.res_name)
              if ch.res_class:
                  parts.append(ctypes.string_at(ch.res_class).decode("utf-8", "ignore"))
                  x11.XFree(ch.res_class)
          return " ".join(parts).lower()

      def get_title(win):
          ptr = ctypes.c_void_p()
          if x11.XFetchName(dpy, win, ctypes.byref(ptr)) and ptr.value:
              s = ctypes.string_at(ptr.value).decode("utf-8", "ignore")
              x11.XFree(ptr.value)
              return s.lower()
          return ""

      try:
          while running and os.getppid() != 1:
              r_ret, p_ret = ctypes.c_ulong(), ctypes.c_ulong()
              children = ctypes.POINTER(ctypes.c_ulong)()
              nchildren = ctypes.c_uint()
              if x11.XQueryTree(dpy, root, ctypes.byref(r_ret), ctypes.byref(p_ret), ctypes.byref(children), ctypes.byref(nchildren)):
                  moved = False
                  for i in range(nchildren.value):
                      w = children[i]
                      attr = XWindowAttributes()
                      if not x11.XGetWindowAttributes(dpy, w, ctypes.byref(attr)):
                          continue
                      if attr.map_state != 2 or attr.override_redirect != 0:
                          continue
                      cls = get_class(w)
                      if any(ide in cls for ide in ("jetbrains", "sun-awt", "java-lang")):
                          continue
                      title = get_title(w)
                      transient_for = ctypes.c_ulong()
                      is_transient = bool(x11.XGetTransientForHint(dpy, w, ctypes.byref(transient_for)) and transient_for.value)

                      if ("chrome" in cls or "chromium" in cls) and not is_transient and attr.width >= 200 and attr.height >= 200:
                          if (attr.x, attr.y, attr.width, attr.height) != (dev_x, dev_y, dev_w, dev_h):
                              x11.XMoveResizeWindow(dpy, w, dev_x, dev_y, dev_w, dev_h)
                              x11.XRaiseWindow(dpy, w)
                              moved = True
                              print(f"Tiled Chrome window 0x{w:x} to ({dev_x}, {dev_y}, {dev_w}x{dev_h})")
                      elif "emulator" in cls or "qemu" in cls or "android emulator" in title:
                          if not is_transient and attr.width >= 200 and attr.height >= 300:
                              tx = dev_x + max(8, (dev_w - attr.width - 56) // 2)
                              ty = dev_y + max(0, (dev_h - attr.height) // 2)
                              if abs(attr.x - tx) > 4 or abs(attr.y - ty) > 4:
                                  x11.XMoveWindow(dpy, w, tx, ty)
                                  x11.XRaiseWindow(dpy, w)
                                  moved = True
                                  print(f"Moved Emulator window 0x{w:x} to ({tx}, {ty})")
                          elif attr.x < dev_x and attr.width >= 40 and attr.height >= 40:
                              tx = dev_x + max(8, (dev_w - attr.width) // 2)
                              ty = min(max(dev_y, attr.y), max(dev_y, dev_y + dev_h - attr.height))
                              x11.XMoveWindow(dpy, w, tx, ty)
                              x11.XRaiseWindow(dpy, w)
                              moved = True
                              print(f"Moved Emulator companion window 0x{w:x} to ({tx}, {ty})")
                  if children:
                      x11.XFree(children)
                  if moved:
                      x11.XFlush(dpy)
              time.sleep(0.3)
      finally:
          x11.XCloseDisplay(dpy)
    """.trimIndent()
  }
}

@Remote("java.awt.GraphicsEnvironment")
internal interface GraphicsEnvironmentRef {
  fun getLocalGraphicsEnvironment(): GraphicsEnvironmentRef
  fun getMaximumWindowBounds(): Rectangle
}

@Remote("java.awt.Frame")
internal interface FrameRef {
  fun setExtendedState(state: Int)
}
