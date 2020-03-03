package com.android.tools.idea.npw.project;

import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.wizard.deprecated.FirstRunWizard;
import com.android.tools.idea.wizard.dynamic.DialogWrapperHost;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.ui.components.JBLabel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class ConfigureAndroidSdkStep extends ModelWizardStep.WithoutModel {

  private JPanel myPanel;
  private JButton myInstallSDKButton;
  private JBLabel myMessage;
  private final BoolValueProperty myProperty = new BoolValueProperty(false);

  public ConfigureAndroidSdkStep() {
    super("Configure Android SDK");
    myInstallSDKButton.addActionListener(e -> {
      DialogWrapperHost host = new DialogWrapperHost(null);
      FirstRunWizard wizard = new FirstRunWizard(host, FirstRunWizardMode.MISSING_SDK);
      wizard.setTitle("SDK Setup");
      wizard.init();
      wizard.show();
      boolean success = IdeSdks.getInstance().getAndroidSdkPath() != null;
      myProperty.set(success);
      if (success) {
        myMessage.setText("Android SDK is installed successfully.");
        myInstallSDKButton.setVisible(false);
      }
    });
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myProperty;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myPanel;
  }
}
