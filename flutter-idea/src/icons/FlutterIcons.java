package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class FlutterIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, FlutterIcons.class);
  }

  public static final Icon FlutterDefault = load("/icons/expui/flutter.svg");
  public static final Icon DevToolsDeepLinks = load("/icons/expui/deepLinks.svg");
  public static final Icon DevTools = load("/icons/expui/devTools.svg");
  public static final Icon DevToolsExtensions = load("/icons/expui/extensions.svg");
  public static final Icon DevToolsInspector = load("icons/expui/inspector.svg");
  public static final Icon PropertyEditor = load("/icons/expui/propertyEditor.svg");

  public static final Icon Flutter_13_2x = load("/icons/flutter_13@2x.png");
  public static final Icon Flutter_64 = load("/icons/flutter_64.png");
  public static final Icon Flutter_64_2x = load("/icons/flutter_64@2x.png");
  public static final Icon Flutter = load("/icons/flutter.png");
  public static final Icon Flutter_2x = load("/icons/flutter@2x.png");
  public static final Icon Flutter_inspect = load("/icons/flutter_inspect.png");
  public static final Icon Flutter_test = load("/icons/flutter_test.png");
  public static final Icon Flutter_badge = load("/icons/flutter_badge.png");

  public static final Icon Phone = load("/icons/phone.png");
  public static final Icon Feedback = load("/icons/feedback.png");
  public static final Icon RefreshItems = load("/icons/refresh_items.png");

  public static final Icon Dart_16 = load("/icons/dart_16.svg");

  public static final Icon HotReload = load("/icons/hot-reload.png");
  public static final Icon HotRestart = load("/icons/hot-restart.png");

  public static final Icon HotReloadRun = load("/icons/reload_run.png");
  public static final Icon HotReloadDebug = load("/icons/reload_debug.png");

  public static final Icon DebugBanner = load("/icons/debugBanner.png");
  public static final Icon DebugPaint = load("/icons/debugPaint.png");
  public static final Icon RepaintRainbow = load("/icons/repaintRainbow.png");

  public static final Icon BazelRun = load("/icons/bazel_run.png");

  public static final Icon CustomClass = load("/icons/custom/class.png");
  public static final Icon CustomClassAbstract = load("/icons/custom/class_abstract.png");
  public static final Icon CustomFields = load("/icons/custom/fields.png");
  public static final Icon CustomInterface = load("/icons/custom/interface.png");
  public static final Icon CustomMethod = load("/icons/custom/method.png");
  public static final Icon CustomMethodAbstract = load("/icons/custom/method_abstract.png");
  public static final Icon CustomProperty = load("/icons/custom/property.png");
  public static final Icon CustomInfo = load("/icons/custom/info.png");

  public static final Icon AndroidStudioNewProject = load("/icons/template_new_project.png");
  public static final Icon AndroidStudioNewPackage = load("/icons/template_new_package.png");
  public static final Icon AndroidStudioNewPlugin = load("/icons/template_new_plugin.png");
  public static final Icon AndroidStudioNewModule = load("/icons/template_new_module.png");

  public static final Icon AttachDebugger = load("/icons/attachDebugger.png");
}
