package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class FlutterIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, FlutterIcons.class);
  }

  public static final Icon WidgetPreview = load("/icons/expui/widgetPreview.svg");
  public static final Icon DevToolsDeepLinks = load("/icons/expui/deepLinks.svg");
  public static final Icon DevTools = load("/icons/expui/devTools.svg");
  public static final Icon DevToolsExtensions = load("/icons/expui/extensions.svg");
  public static final Icon DevToolsInspector = load("icons/expui/inspector.svg");
  public static final Icon PropertyEditor = load("/icons/expui/propertyEditor.svg");
  public static final Icon Flutter = load("/icons/flutter.png");
  public static final Icon Flutter_2x = load("/icons/flutter@2x.png");
  public static final Icon Flutter_test = load("/icons/flutter_test.png");
  public static final Icon RefreshItems = load("/icons/refresh_items.png");

  public static final Icon Android = load("/icons/android.svg");
  public static final Icon IOS = load("/icons/ios.svg");
  public static final Icon Mobile = load("/icons/mobile.svg");
  public static final Icon Desktop = load("/icons/desktop.svg");
  public static final Icon Web = load("/icons/web.svg");

  public static final Icon Dart_16 = load("/icons/dart_16.svg");

  public static final Icon HotReload = load("/icons/hot-reload.png");
  public static final Icon HotRestart = load("/icons/hot-restart.png");
  public static final Icon BazelRun = load("/icons/bazel_run.png");

  public static final Icon CustomClass = load("/icons/custom/class.png");
  public static final Icon CustomClassAbstract = load("/icons/custom/class_abstract.png");
  public static final Icon CustomMethod = load("/icons/custom/method.png");
  public static final Icon CustomMethodAbstract = load("/icons/custom/method_abstract.png");
  public static final Icon CustomInfo = load("/icons/custom/info.png");
  public static final Icon AttachDebugger = load("/icons/attachDebugger.png");

  public static final Icon Phone = load("/icons/phone.png");
  public static final Icon DebugPaint = load("/icons/debugPaint.png");
  public static final Icon Text = load("/icons/inspector/textArea.png");

  public static class State {
    public static final Icon RedProgr_1 = load("/icons/perf/RedProgr_1.png");
    public static final Icon RedProgr_2 = load("/icons/perf/RedProgr_2.png");
    public static final Icon RedProgr_3 = load("/icons/perf/RedProgr_3.png");
    public static final Icon RedProgr_4 = load("/icons/perf/RedProgr_4.png");
    public static final Icon RedProgr_5 = load("/icons/perf/RedProgr_5.png");
    public static final Icon RedProgr_6 = load("/icons/perf/RedProgr_6.png");
    public static final Icon RedProgr_7 = load("/icons/perf/RedProgr_7.png");
    public static final Icon RedProgr_8 = load("/icons/perf/RedProgr_8.png");

    public static final Icon YellowProgr_1 = load("/icons/perf/YellowProgr_1.png");
    public static final Icon YellowProgr_2 = load("/icons/perf/YellowProgr_2.png");
    public static final Icon YellowProgr_3 = load("/icons/perf/YellowProgr_3.png");
    public static final Icon YellowProgr_4 = load("/icons/perf/YellowProgr_4.png");
    public static final Icon YellowProgr_5 = load("/icons/perf/YellowProgr_5.png");
    public static final Icon YellowProgr_6 = load("/icons/perf/YellowProgr_6.png");
    public static final Icon YellowProgr_7 = load("/icons/perf/YellowProgr_7.png");
    public static final Icon YellowProgr_8 = load("/icons/perf/YellowProgr_8.png");

    public static final Icon GreyProgr_1 = load("/icons/perf/GreyProgr_1.png");
    public static final Icon GreyProgr_2 = load("/icons/perf/GreyProgr_2.png");
    public static final Icon GreyProgr_3 = load("/icons/perf/GreyProgr_3.png");
    public static final Icon GreyProgr_4 = load("/icons/perf/GreyProgr_4.png");
    public static final Icon GreyProgr_5 = load("/icons/perf/GreyProgr_5.png");
    public static final Icon GreyProgr_6 = load("/icons/perf/GreyProgr_6.png");
    public static final Icon GreyProgr_7 = load("/icons/perf/GreyProgr_7.png");
    public static final Icon GreyProgr_8 = load("/icons/perf/GreyProgr_8.png");
  }
}
