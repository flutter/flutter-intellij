package io.flutter.actions;

import static com.teamdev.jxbrowser.engine.RenderingMode.HARDWARE_ACCELERATED;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.engine.EngineOptions;
import com.teamdev.jxbrowser.view.swing.BrowserView;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.jetbrains.annotations.NotNull;

public class TestJxBrowserAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    System.setProperty("jxbrowser.license.key", "6P830J66YAO1XQRI1FNEXP8ZTHUOQTY2YUJRCN9RTARQ58QFAP63GRRH20B3P5PB7PC8");

    // Creating and running Chromium engine
    EngineOptions options =
      EngineOptions.newBuilder(HARDWARE_ACCELERATED).build();
    Engine engine = Engine.newInstance(options);
    Browser browser = engine.newBrowser();

    SwingUtilities.invokeLater(() -> {
      // Creating Swing component for rendering web content
      // loaded in the given Browser instance.
      BrowserView view = BrowserView.newInstance(browser);

      // Creating and displaying Swing app frame.
      JFrame frame = new JFrame("Hello World");
      // Close Engine and onClose app window
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          engine.close();
        }
      });
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      JTextField addressBar = new JTextField("https://www.google.com");
      addressBar.addActionListener(e ->
                                     browser.navigation().loadUrl(addressBar.getText()));
      frame.add(addressBar, BorderLayout.NORTH);
      frame.add(view, BorderLayout.CENTER);
      frame.setSize(800, 500);
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
    });

  }
}
