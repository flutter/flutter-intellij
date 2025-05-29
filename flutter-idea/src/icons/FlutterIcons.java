package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class FlutterIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, FlutterIcons.class);
  }

  public static final Icon DevToolsDeepLinks = load("/icons/expui/deepLinks.svg");
  public static final Icon DevTools = load("/icons/expui/devTools.svg");
  public static final Icon DevToolsExtensions = load("/icons/expui/extensions.svg");
  public static final Icon DevToolsInspector = load("icons/expui/inspector.svg");
  public static final Icon PropertyEditor = load("/icons/expui/propertyEditor.svg");
  public static final Icon Flutter = load("/icons/flutter.png");
  public static final Icon Flutter_2x = load("/icons/flutter@2x.png");
  public static final Icon Flutter_test = load("/icons/flutter_test.png");
  public static final Icon Phone = load("/icons/phone.png");
  public static final Icon RefreshItems = load("/icons/refresh_items.png");

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
}
