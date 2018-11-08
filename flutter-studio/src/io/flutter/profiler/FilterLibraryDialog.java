/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.profiler;

import com.android.tools.adtui.stdui.CommonButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import org.jetbrains.annotations.NotNull;


public class FilterLibraryDialog extends DialogWrapper {
  // String to represent all Dart libraries in filter dialog.
  public static final String ALL_DART_LIBRARIES = "dart:*";
  public static final String DART_LIBRARY_PREFIX = "dart:";

  private String[] allLibraries;
  private PopupLibraryFilter popupDialog;
  private Set<String> previousSelectedLibraries;

  public FilterLibraryDialog(@NotNull Component parent, String[] myLibraries, @NotNull Set<String> selectedLibraries) {
    super(parent, true);

    this.allLibraries = myLibraries;
    this.previousSelectedLibraries = selectedLibraries;

    setTitle("Filter Libraries");
    init();
  }

  public Set<String> selectedLibraries() {
    return popupDialog.librariesToFilter();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

    popupDialog = new PopupLibraryFilter(allLibraries);

    panel.add(popupDialog);

    Border buttonBorder = BorderFactory.createLineBorder(Color.LIGHT_GRAY);

    CommonButton check = new CommonButton("Select All");
    check.setForeground(Color.BLACK);
    check.setBorder(buttonBorder);
    check.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        popupDialog.checkBoxList.setCheckStateAllItems(true);
        popupDialog.checkBoxList.updateUI();
      }
    });

    CommonButton uncheck = new CommonButton("Unselect All");
    uncheck.setForeground(Color.BLACK);
    uncheck.setBorder(buttonBorder);
    uncheck.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        popupDialog.checkBoxList.setCheckStateAllItems(false);
        popupDialog.checkBoxList.updateUI();
      }
    });

    // Check the libraries that are being filtered.
    popupDialog.checkBoxList.setCheckStateAllItems(previousSelectedLibraries);

    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 50, 0));
    buttonPanel.add(check);
    buttonPanel.add(uncheck);
    panel.add(buttonPanel);

    return panel;
  }
}

/**
 * Handle filtering of which Dart classes based on libraries
 */
class PopupLibraryFilter extends JPanel {
  DefaultListModel<JCheckBox> model;
  JCheckBoxList checkBoxList;

  PopupLibraryFilter(String[] libraries) {
    model = new DefaultListModel<JCheckBox>();

    for (int index = 0; index < libraries.length; index++) {
      model.addElement(new JCheckBox(libraries[index]));
    }

    checkBoxList = new JCheckBoxList(model);

    JScrollPane scrollPane = new JBScrollPane(checkBoxList);
    this.add(scrollPane);

    this.setVisible(true);
  }

  Set<String> librariesToFilter() {
    Set<String> checkedLibraries = new HashSet<>();

    for (int index = 0; index < model.getSize(); index++) {
      JCheckBox cb = model.get(index);
      if (cb.isSelected()) {
        checkedLibraries.add(cb.getText());
      }
    }

    return checkedLibraries;
  }

  protected class JCheckBoxList extends JList<JCheckBox> {
    protected final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);

    public JCheckBoxList() {
      setCellRenderer(new CellRenderer());
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          int index = locationToIndex(e.getPoint());
          if (index != -1) {
            JCheckBox checkbox = (JCheckBox)getModel().getElementAt(index);
            checkbox.setSelected(!checkbox.isSelected());
            repaint();
          }
        }
      });
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    public JCheckBoxList(ListModel<JCheckBox> model) {
      this();
      setModel(model);
    }

    public void setCheckStateAllItems(boolean isChecked) {
      int modelSize = this.getModel().getSize();
      for (int index = 0; index < modelSize; index++) {
        this.getModel().getElementAt(index).setSelected(isChecked);
      }
    }

    public void setCheckStateAllItems(Set<String> librariesSelected) {
      int modelSize = this.getModel().getSize();
      for (int index = 0; index < modelSize; index++) {
        JCheckBox checkbox = this.getModel().getElementAt(index);
        String libraryToFind = checkbox.getText();
        if (librariesSelected.contains(libraryToFind)) {
          // This library is being filtered, check it.
          this.getModel().getElementAt(index).setSelected(true);
        }
      }
    }

    protected class CellRenderer implements ListCellRenderer<JCheckBox> {
      @Override
      public Component getListCellRendererComponent(
        JList<? extends JCheckBox> list, JCheckBox value, int index,
        boolean isSelected, boolean cellHasFocus) {
        JCheckBox checkbox = value;

        //Drawing checkbox, change the appearance here
        checkbox.setBackground(isSelected ? getSelectionBackground()
                                          : getBackground());
        checkbox.setForeground(isSelected ? getSelectionForeground()
                                          : getForeground());
        checkbox.setEnabled(isEnabled());
        checkbox.setFont(getFont());
        checkbox.setFocusPainted(false);
        checkbox.setBorderPainted(true);
        checkbox.setBorder(isSelected ? UIManager.getBorder("List.focusCellHighlightBorder") : noFocusBorder);
        return checkbox;
      }
    }
  }
}
