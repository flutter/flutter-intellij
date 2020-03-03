/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio.ui;

import static com.intellij.util.ArrayUtilRt.EMPTY_STRING_ARRAY;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.vectordrawable.VdIcon;
import com.android.tools.idea.npw.assetstudio.MaterialDesignIcons;
import com.google.common.base.Joiner;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A dialog to pick a pre-configured material icon in vector format.
 */
public final class IconPickerDialog extends DialogWrapper {
  @NotNull private static final String[] ICON_CATEGORIES = initIconCategories();

  private static String[] initIconCategories() {
    Collection<String> categories = MaterialDesignIcons.getCategories();

    Collection<String> allAndCategories = new ArrayList<>(categories.size() + 1);

    // "All" is not a real category. All the icons are categorized but there's no filtering
    // when "All" is selected. This is why the array is usually dereferenced starting from 1.
    allAndCategories.add("All");
    allAndCategories.addAll(categories);

    // noinspection SSBasedInspection
    return allAndCategories.toArray(EMPTY_STRING_ARRAY);
  }

  private static final String ALL_CATEGORY = ICON_CATEGORIES[0];

  private static final int COLUMN_NUMBER = 6;
  private static final int ICON_ROW_HEIGHT = JBUI.scale(48 + 16);

  /**
   * A mapping of all categories to their target icons.
   */
  private final Multimap<String, VdIcon> myCategoryIcons = TreeMultimap.create();

  /**
   * A list of all active icons (based on the currently selected category).
   */
  private final List<VdIcon> myIconList = new ArrayList<>(1000);
  private final List<VdIcon> myFilteredIconList = new ArrayList<>(1000);

  private final AbstractTableModel myModel = new AbstractTableModel() {
    @Override
    public String getColumnName(int column) {
      return null;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return VdIcon.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      int index = rowIndex * COLUMN_NUMBER + columnIndex;
      if (index < 0) {
        return null;
      }
      return myFilteredIconList.size() > index ? myFilteredIconList.get(index) : null;
    }

    @Override
    public int getRowCount() {
      return myFilteredIconList.size() / COLUMN_NUMBER + ((myFilteredIconList.size() % COLUMN_NUMBER == 0) ? 0 : 1);
    }

    @Override
    public int getColumnCount() {
      return COLUMN_NUMBER;
    }
  };

  private final JBTable myIconTable = new JBTable(myModel);

  private JPanel myContentPanel;
  private JPanel myCategoriesPanel;
  private JPanel myIconsPanel;
  @SuppressWarnings("unused") private JPanel myLicensePanel;
  private HyperlinkLabel myLicenseLabel;
  private SearchTextField mySearchField;

  @Nullable private VdIcon mySelectedIcon;

  public IconPickerDialog(@Nullable VdIcon selectedIcon) {
    super(false);

    setTitle("Select Icon");
    initializeIconMap();

    // On the left hand side, add the categories chooser.
    String[] categories = getCategoryNames();
    JBList<String> categoryList = new JBList<>(categories);
    JBScrollPane categoryPane = new JBScrollPane(categoryList);
    myCategoriesPanel.add(categoryPane);


    // The default panel color in darcula mode is too dark given that our icons are all black.
    // We provide a lighter color for higher contrast.
    Color iconBackgroundColor = UIUtil.getListBackground();

    TableCellRenderer tableRenderer = new DefaultTableCellRenderer() {
      @Override
      public void setValue(@Nullable Object value) {
        VdIcon icon = (VdIcon)value;
        setText("");
        setIcon(icon);
        String displayName = icon != null ? icon.getDisplayName() : "";
        AccessibleContextUtil.setName(this, displayName);
      }

      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        if (table.getValueAt(row, column) == null) {
          Component cell = super.getTableCellRendererComponent(table, value, false, false, row, column);
          cell.setFocusable(false);
          return cell;
        }
        else {
          JComponent component = (JComponent)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          component.setFont(JBUI.Fonts.miniFont());
          if (!isSelected) {
            component.setBackground(JBColor.WHITE);
          }
          component.setForeground(isSelected ? table.getSelectionForeground() : JBColor.GRAY);

          return component;
        }
      }
    };

    // For the main content area, display a grid if icons.
    myIconTable.setBackground(iconBackgroundColor);
    myIconTable.setDefaultRenderer(VdIcon.class, tableRenderer);
    myIconTable.setRowHeight(ICON_ROW_HEIGHT);
    myIconTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myIconTable.setCellSelectionEnabled(true);
    myIconsPanel.add(new JBScrollPane(myIconTable));
    myIconTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        char keyChar = e.getKeyChar();
        if (Character.isLetter(keyChar) || Character.isDigit(keyChar)) {
          mySearchField.setText(Character.toString(keyChar));
          mySearchField.requestFocus();
        }
        super.keyPressed(e);
      }
    });

    // Add license info at the bottom.
    myLicenseLabel.setHyperlinkText("These icons are available under the ", "Apache License Version 2.0", "");
    myLicenseLabel.setHyperlinkTarget("http://www.apache.org/licenses/LICENSE-2.0.txt");

    // Setup the picking interaction for the table.
    final ListSelectionModel selModel = myIconTable.getSelectionModel();
    myIconTable.getColumnModel().setColumnSelectionAllowed(true);
    myIconTable.setGridColor(iconBackgroundColor);
    myIconTable.setIntercellSpacing(JBUI.size(3, 3));
    myIconTable.setRowMargin(0);

    ListSelectionListener listener = e -> {
      if (e.getValueIsAdjusting()) {
        return;
      }
      int row = myIconTable.getSelectedRow();
      int col = myIconTable.getSelectedColumn();
      VdIcon icon = row != -1 && col != -1 ? (VdIcon)myIconTable.getValueAt(row, col) : null;
      mySelectedIcon = icon;
      setOKActionEnabled(icon != null);
    };

    selModel.addListSelectionListener(listener);
    ListSelectionModel colSelModel = myIconTable.getColumnModel().getSelectionModel();

    colSelModel.addListSelectionListener(listener);

    // Setup the picking interaction for the category list.
    categoryList.addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) {
        return;
      }
      int selectedIndex = categoryList.getSelectedIndex();
      if (selectedIndex >= 0) {
        String category = ICON_CATEGORIES[selectedIndex];
        updateIconList(category);
      }
    });
    categoryList.setSelectedIndex(0);

    selModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selModel.setSelectionInterval(0, 0);
    myIconTable.setColumnSelectionInterval(0, 0);
    myIconTable.requestFocusInWindow();

    if (selectedIcon != null) {
      initializeSelection(selectedIcon);
    }

    init();
  }

  @VisibleForTesting
  @NotNull
  static String[] getCategoryNames() {
    return Arrays.stream(ICON_CATEGORIES)
                 .map(category -> category.equals("av") ? "Audio/Video" : StringUtil.capitalize(category))
                 .collect(Collectors.toList()).toArray(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void createUIComponents() {
    mySearchField = new SearchTextField(false);
    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateFilter();
      }
    });
  }

  private void updateFilter() {
    String text = mySearchField.getText().trim();
    myFilteredIconList.clear();
    for (VdIcon icon : myIconList) {
      if (text.isEmpty() || StringUtil.containsIgnoreCase(icon.getDisplayName(), text)) {
        myFilteredIconList.add(icon);
      }
    }

    myModel.fireTableDataChanged();
  }

  private void initializeSelection(@NotNull VdIcon selectedIcon) {
    for (int r = 0; r < myIconTable.getRowCount(); r++) {
      for (int c = 0; c < myIconTable.getColumnCount(); c++) {
        VdIcon icon = (VdIcon)myIconTable.getValueAt(r, c);
        if (icon != null && icon.getURL().equals(selectedIcon.getURL())) {
          myIconTable.changeSelection(r, c, false, false);
          return;
        }
      }
    }
  }

  private void initializeIconMap() {
    for (int i = 1; i < ICON_CATEGORIES.length; i++) {
      String categoryName = ICON_CATEGORIES[i];
      for (String iconName : MaterialDesignIcons.getIconNames(categoryName)) {
        URL url = MaterialDesignIcons.getIcon(iconName, categoryName);

        try {
          VdIcon icon = new VdIcon(url);
          icon.setShowName(true);
          myCategoryIcons.put(categoryName, icon);
        }
        catch (IOException ignore) {
          // Skip this icon.
        }
      }
    }
    // Now that each category has been initialized, collect all icons into the "all" category
    myCategoryIcons.putAll(ALL_CATEGORY, myCategoryIcons.values());
  }

  @Nullable
  public VdIcon getSelectedIcon() {
    return mySelectedIcon;
  }

  @VisibleForTesting
  JTable getTable() {
    return myIconTable;
  }

  @VisibleForTesting
  void setFilter(String text) {
    mySearchField.setText(text);
  }

  private void updateIconList(@NotNull String categoryName) {
    myIconList.clear();
    assert myCategoryIcons.containsKey(categoryName) : String.format(
      "Category '%1$s' is not populated. List of populated categories: %2$s", categoryName,
      Joiner.on(",").join(myCategoryIcons.keySet()));
    myIconList.addAll(myCategoryIcons.get(categoryName));
    myIconTable.getColumnModel().setColumnSelectionAllowed(true);

    updateFilter();

    // Pick the left upper corner one as the default selected one.
    myIconTable.setColumnSelectionInterval(0, 0);
    myIconTable.getSelectionModel().setSelectionInterval(0, 0);
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }
}
