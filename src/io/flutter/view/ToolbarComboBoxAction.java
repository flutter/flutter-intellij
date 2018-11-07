// This code is forked from
// com.intellij.openapi.actionSystem.ex.ComboBoxAction
// to create a ComboBoxAction that dispays in a toolbar without a button border.

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package io.flutter.view;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class ToolbarComboBoxAction extends AnAction implements CustomComponentAction {
  private static Icon myIcon = null;
  private static Icon myDisabledIcon = null;
  private static Icon myWin10ComboDropTriangleIcon = null;

  public static Icon getArrowIcon(boolean enabled) {
    // We want to use a darker icon when the combo box is enabled.
    return enabled ? AllIcons.General.Combo2 : ComboBoxAction.getArrowIcon(enabled);
  }

  private boolean mySmallVariant = true;
  private String myPopupTitle;

  protected ToolbarComboBoxAction() {
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    JFrame frame = WindowManager.getInstance().getFrame(project);
    if (!(frame instanceof IdeFrame)) return;

    ListPopup popup = createActionPopup(e.getDataContext(), ((IdeFrame)frame).getComponent(), null);
    popup.showCenteredInCurrentWindow(project);
  }

  @NotNull
  private ListPopup createActionPopup(@NotNull DataContext context, @NotNull JComponent component, @Nullable Runnable disposeCallback) {
    DefaultActionGroup group = createPopupActionGroup(component, context);
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      myPopupTitle, group, context, false, shouldShowDisabledActions(), false, disposeCallback, getMaxRows(), getPreselectCondition());
    popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
    return popup;
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation) {
    JPanel panel = new JPanel(new GridBagLayout());
    ToolbarComboBoxButton button = createComboBoxButton(presentation);
    panel.add(button,
              new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insets(0, 3), 0, 0));
    return panel;
  }

  protected ToolbarComboBoxButton createComboBoxButton(Presentation presentation) {
    return new ToolbarComboBoxButton(presentation);
  }

  public boolean isSmallVariant() {
    return mySmallVariant;
  }

  public void setSmallVariant(boolean smallVariant) {
    mySmallVariant = smallVariant;
  }

  public void setPopupTitle(String popupTitle) {
    myPopupTitle = popupTitle;
  }

  protected boolean shouldShowDisabledActions() {
    return false;
  }

  @NotNull
  protected abstract DefaultActionGroup createPopupActionGroup(JComponent button);

  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button, @NotNull  DataContext dataContext) {
    return createPopupActionGroup(button);
  }

  protected int getMaxRows() {
    return 30;
  }

  protected int getMinHeight() {
    return 1;
  }

  protected int getMinWidth() {
    return 1;
  }

  protected class ToolbarComboBoxButton extends JButton implements UserActivityProviderComponent {
    private final Presentation myPresentation;
    private boolean myForcePressed = false;
    private PropertyChangeListener myButtonSynchronizer;

    @Override
    public void setUI(ButtonUI ui) {
      // We use the BasicButtonUI so we can display a button without a
      // background.
      super.setUI(new BasicButtonUI());
    }

    public ToolbarComboBoxButton(Presentation presentation) {
      myPresentation = presentation;
      setModel(new MyButtonModel());
      getModel().setEnabled(myPresentation.isEnabled());
      setVisible(presentation.isVisible());
      setHorizontalAlignment(LEFT);
      setFocusable(ScreenReader.isActive());
      setBorder(JBUI.Borders.empty());
      putClientProperty("styleCombo", ToolbarComboBoxAction.this);
      setMargin(JBUI.insets(0, 0, 0, 2));
      if (isSmallVariant()) {
        // TODO(jacobr): it would be better to use
        // JBUI.Fonts.toolbarSmallComboBoxFont but it isn't availabe in all
        // versions of IntelliJ we support.
        setFont(JBUI.Fonts.miniFont());
      }

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            e.consume();
            doClick();
          }
        }
      });
      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
          mouseMoved(MouseEventAdapter.convert(e, e.getComponent(),
                                               MouseEvent.MOUSE_MOVED,
                                               e.getWhen(),
                                               e.getModifiers() | e.getModifiersEx(),
                                               e.getX(),
                                               e.getY()));
        }
      });
    }

    @Override
    protected void fireActionPerformed(ActionEvent event) {
      if (!myForcePressed) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> showPopup());
      }
    }

    @NotNull
    private Runnable setForcePressed() {
      myForcePressed = true;
      repaint();

      return () -> {
        // give the button a chance to handle action listener
        ApplicationManager.getApplication().invokeLater(() -> {
          myForcePressed = false;
          repaint();
        }, ModalityState.any());
        repaint();
        fireStateChanged();
      };
    }

    @Nullable
    @Override
    public String getToolTipText() {
      return myForcePressed || Registry.is("ide.helptooltip.enabled") ? null : super.getToolTipText();
    }

    public void showPopup() {
      JBPopup popup = createPopup(setForcePressed());
      if (Registry.is("ide.helptooltip.enabled")) {
        HelpTooltip.setMasterPopup(this, popup);
      }

      popup.showUnderneathOf(this);
    }

    protected JBPopup createPopup(Runnable onDispose) {
      return createActionPopup(getDataContext(), this, onDispose);
    }

    protected DataContext getDataContext() {
      return DataManager.getInstance().getDataContext(this);
    }

    @Override
    public void removeNotify() {
      if (myButtonSynchronizer != null) {
        myPresentation.removePropertyChangeListener(myButtonSynchronizer);
        myButtonSynchronizer = null;
      }
      super.removeNotify();
    }

    @Override
    public void addNotify() {
      super.addNotify();
      if (myButtonSynchronizer == null) {
        myButtonSynchronizer = new MyButtonSynchronizer();
        myPresentation.addPropertyChangeListener(myButtonSynchronizer);
      }
      initButton();
    }

    private void initButton() {
      setIcon(myPresentation.getIcon());
      setText(myPresentation.getText());
      updateTooltipText(myPresentation.getDescription());
      updateButtonSize();
    }

    private void updateTooltipText(String description) {
      String tooltip = KeymapUtil.createTooltipText(description, ToolbarComboBoxAction.this);
      if (Registry.is("ide.helptooltip.enabled") && StringUtil.isNotEmpty(tooltip)) {
        HelpTooltip.dispose(this);
        new HelpTooltip().setDescription(tooltip).setLocation(HelpTooltip.Alignment.BOTTOM).installOn(this);
      } else {
        setToolTipText(!tooltip.isEmpty() ? tooltip : null);
      }
    }

    protected class MyButtonModel extends DefaultButtonModel {
      @Override
      public boolean isPressed() {
        return myForcePressed || super.isPressed();
      }

      @Override
      public boolean isArmed() {
        return myForcePressed || super.isArmed();
      }
    }

    private class MyButtonSynchronizer implements PropertyChangeListener {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if (Presentation.PROP_TEXT.equals(propertyName)) {
          setText((String)evt.getNewValue());
          updateButtonSize();
        }
        else if (Presentation.PROP_DESCRIPTION.equals(propertyName)) {
          updateTooltipText((String)evt.getNewValue());
        }
        else if (Presentation.PROP_ICON.equals(propertyName)) {
          setIcon((Icon)evt.getNewValue());
          updateButtonSize();
        }
        else if (Presentation.PROP_ENABLED.equals(propertyName)) {
          setEnabled(((Boolean)evt.getNewValue()).booleanValue());
        }
      }
    }

    @Override
    public boolean isOpaque() {
      return !isSmallVariant();
    }

    @Override
    public int getIconTextGap() {
      return 0;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension prefSize = super.getPreferredSize();
      int width = prefSize.width
                  + (myPresentation != null && isArrowVisible(myPresentation) ? getArrowIcon(isEnabled()).getIconWidth() : 0)
                  + (StringUtil.isNotEmpty(getText()) ? getIconTextGap() : 0)
                  + (UIUtil.isUnderWin10LookAndFeel() ? JBUI.scale(6) : 0);

      Dimension size = new Dimension(width, isSmallVariant() ? JBUI.scale(24) : Math.max(JBUI.scale(24), prefSize.height));
      JBInsets.addTo(size, getMargin());
      return size;
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(super.getMinimumSize().width, getPreferredSize().height);
    }

    @Override
    public Font getFont() {
      // TODO(jacobr): it would be better to use
      // JBUI.Fonts.toolbarSmallComboBoxFont but it isn't availabe in all
      // versions of IntelliJ we support.
      return isSmallVariant() ? JBUI.Fonts.miniFont() : UIUtil.getLabelFont();
    }

    @Override
    protected Graphics getComponentGraphics(Graphics graphics) {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (!isArrowVisible(myPresentation)) {
        return;
      }
      Icon icon = getArrowIcon(isEnabled());
      int x = getWidth() - icon.getIconWidth() - getInsets().right - getMargin().right;

      icon.paintIcon(null, g, x, (getHeight() - icon.getIconHeight()) / 2);
    }

    protected boolean isArrowVisible(@NotNull Presentation presentation) {
      return true;
    }

    @Override public void updateUI() {
      super.updateUI();
      setMargin(JBUI.insets(0, 0, 0, 2));
      updateButtonSize();
    }

    protected void updateButtonSize() {
      invalidate();
      repaint();
      setSize(getPreferredSize());
      repaint();
    }
  }

  protected Condition<AnAction> getPreselectCondition() { return null; }
}
