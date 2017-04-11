package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class FlutterIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, FlutterIcons.class);
  }

  public static final Icon Flutter_13 = load("/icons/flutter_13.png");
  public static final Icon Flutter = load("/icons/flutter.png");
  public static final Icon Flutter_2x = load("/icons/flutter@2x.png");

  public static final Icon Phone = load("/icons/phone.png");
  public static final Icon OpenObservatory = load("/icons/observatory.png");

  public static final Icon HotReload = load("/icons/hot-reload.png");

  public static final Icon BazelRun = load("/icons/bazel_run.png");
}
