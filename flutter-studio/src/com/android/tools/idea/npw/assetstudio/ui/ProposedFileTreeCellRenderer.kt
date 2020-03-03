/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.ui

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree

/**
 * Custom TreeCellRenderer for trees backed by a [ProposedFileTreeModel]. Given a
 * [ProposedFileTreeModel.Node] from the tree model, the corresponding rendered cell will be marked
 * with the node's Icon and will contain the name of the File associated with the node,
 * formatted differently depending on whether the File is an existing directory (regular text),
 * a new file or directory (italicized), or an already-existing file (red text).
 */
class ProposedFileTreeCellRenderer: ColoredTreeCellRenderer() {
  override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
                                     leaf: Boolean, row: Int, hasFocus: Boolean) {
    val node = value as ProposedFileTreeModel.Node

    append(node.file.name, when {
      node.conflicted -> SimpleTextAttributes.ERROR_ATTRIBUTES
      node.file.exists() -> SimpleTextAttributes.REGULAR_ATTRIBUTES
      else -> SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES
    })

    icon = node.getIcon()
  }
}