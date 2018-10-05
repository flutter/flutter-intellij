package icons;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class FlutterIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, FlutterIcons.class);
  }

  public static final Icon Flutter_13 = load("/icons/flutter_13.png");
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

  public static final Icon OpenObservatory = load("/icons/observatory.png");
  public static final Icon OpenObservatoryGroup = load("/icons/observatory_overflow.png");

  public static final Icon OpenTimeline = load("/icons/timeline.png");

  public static final Icon HotReload = load("/icons/hot-reload.png");
  public static final Icon HotRestart = AllIcons.Actions.Restart;

  public static final Icon HotReloadRun = load("/icons/reload_run.png");
  public static final Icon HotReloadDebug = load("/icons/reload_debug.png");

  public static final Icon BazelRun = load("/icons/bazel_run.png");

  public static final Icon CustomClass = load("/icons/custom/class.png");
  public static final Icon CustomClassAbstract = load("/icons/custom/class_abstract.png");
  public static final Icon CustomFields = load("/icons/custom/fields.png");
  public static final Icon CustomInterface = load("/icons/custom/interface.png");
  public static final Icon CustomMethod = load("/icons/custom/method.png");
  public static final Icon CustomMethodAbstract = load("/icons/custom/method_abstract.png");
  public static final Icon CustomProperty = load("/icons/custom/property.png");
  public static final Icon CustomInfo = load("/icons/custom/info.png");

  public static final Icon AndroidStudioNewModule = load("/icons/template_new_project.png");
  public static final Icon AndroidStudioNewPackage = load("/icons/template_new_package.png");
  public static final Icon AndroidStudioNewPlugin = load("/icons/template_new_plugin.png");

  public static final Icon AttachDebugger = load("/icons/attachDebugger.png");

  // Flutter Inspector Widget Icons.
  public static final Icon Accessibility = load("/icons/inspector/balloonInformation.png");
  public static final Icon Animation = load("/icons/inspector/resume.png");
  public static final Icon Assets = load("/icons/inspector/any_type.png");
  public static final Icon Async = load("/icons/inspector/threads.png");
  public static final Icon Diagram = load("/icons/inspector/diagram.png");
  public static final Icon Input = load("/icons/inspector/renderer.png");
  public static final Icon Painting = load("/icons/inspector/colors.png");
  public static final Icon Scrollbar = load("/icons/inspector/scrollbar.png");
  public static final Icon Stack = load("/icons/inspector/value.png");
  public static final Icon Styling = load("/icons/inspector/atrule.png");
  public static final Icon Text = load("/icons/inspector/textArea.png");

  public static final Icon ExpandProperty = load("/icons/inspector/expand_property.png");
  public static final Icon CollapseProperty = load("/icons/inspector/collapse_property.png");

  // Flutter Outline Widget Icons.
  public static final Icon Column = load("/icons/preview/column.png");
  public static final Icon Padding = load("/icons/preview/padding.png");
  public static final Icon RemoveWidget = load("/icons/preview/remove_widget.png");
  public static final Icon Row = load("/icons/preview/row.png");
  public static final Icon Center = load("/icons/preview/center.png");
  public static final Icon Container = load("/icons/preview/container.png");
  public static final Icon Up = load("/icons/preview/up.png");
  public static final Icon Down = load("/icons/preview/down.png");
  public static final Icon ExtractMethod = load("/icons/preview/extract_method.png");

  public static class State {
    public static final Icon RedProgr = load("/icons/perf/RedProgr.png"); // 16x16
    public static final Icon RedProgr_1 = load("/icons/perf/RedProgr_1.png"); // 16x16
    public static final Icon RedProgr_2 = load("/icons/perf/RedProgr_2.png"); // 16x16
    public static final Icon RedProgr_3 = load("/icons/perf/RedProgr_3.png"); // 16x16
    public static final Icon RedProgr_4 = load("/icons/perf/RedProgr_4.png"); // 16x16
    public static final Icon RedProgr_5 = load("/icons/perf/RedProgr_5.png"); // 16x16
    public static final Icon RedProgr_6 = load("/icons/perf/RedProgr_6.png"); // 16x16
    public static final Icon RedProgr_7 = load("/icons/perf/RedProgr_7.png"); // 16x16
    public static final Icon RedProgr_8 = load("/icons/perf/RedProgr_8.png"); // 16x16
  }
}
