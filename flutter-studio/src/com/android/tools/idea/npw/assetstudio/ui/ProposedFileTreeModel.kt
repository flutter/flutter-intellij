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

import com.intellij.icons.AllIcons
import com.intellij.util.PlatformIcons
import java.io.File
import javax.swing.Icon
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/**
 * Default [Icon] that [ProposedFileTreeModel] marks non-directory [File]s
 * with when a proposedFileToIcon mapping isn't specified.
 */
val DEFAULT_ICON = AllIcons.FileTypes.Any_type!!
/**
 * The [Icon] that [ProposedFileTreeModel] uses to mark directories.
 */
val DIR_ICON = PlatformIcons.FOLDER_ICON!!

/**
 * A [TreeModel] representing the sub-tree of the file system relevant to a pre-determined set of
 * proposed new [File]s relative to a given root directory. The model keeps track of which of the
 * non-directory [File]s already existed **when the model was created**. If a path to a proposed
 * file contains non-existing intermediate directories, these will also be considered new files.
 *
 * The model marks each relevant file and directory with an [Icon] for rendering (see
 * [ProposedFileTreeCellRenderer]). By default, proposed files are marked with either [DIR_ICON]
 * for directories or [DEFAULT_ICON] for regular files. However, callers can specify the [Icon]
 * corresponding to each proposed [File] by passing a [Map] to the appropriate constructor.
 *
 * Directory contents preserve the iteration order of the [Set] or [Map] used to construct the model.
 * For example, if the model is built from the sorted map
 *
 * { File("root/sub1/file1"): icon1, File("root/sub2/file2"): icon2 }
 *
 * then *sub1* will appear before *sub2* in the model of *root*'s children, since it appeared first
 * when iterating over the map entries.
 */
class ProposedFileTreeModel private constructor(private val rootNode: Node): TreeModel {
  constructor(rootDir: File, proposedFileToIcon: Map<File, Icon>)
    : this(Node.makeTree(rootDir, proposedFileToIcon.keys, proposedFileToIcon::get))

  constructor(rootDir: File, proposedFiles: Set<File>)
    : this(Node.makeTree(rootDir, proposedFiles) { null })

  /**
   * Returns true if any of the non-directory proposed [File]s in the tree already exist.
   */
  fun hasConflicts() = rootNode.hasConflicts()

  /**
   * A vertex in a [ProposedFileTreeModel]'s underlying tree structure. Each node corresponds either
   * to a directory, in which case it will also keep track of a list of nodes corresponding to that
   * directory's children, or to a proposed normal [File], in which case it records whether or not the
   * proposed [File] is in conflict with an existing file.
   *
   * By definition,[Node]s corresponding to directories are not conflicted (though they may represent
   * a [conflictedTree]), and [Node]s corresponding to normal (non-directory) files can have no children.
   *
   * @property file The proposed [File] corresponding to this node
   * @property icon The [Icon] with which the [File] should be marked
   * @property children If this node corresponds to a directory, a list of nodes corresponding
   *           to the directory's children. Otherwise, this list is empty.
   * @property conflicted true if this node corresponds to a proposed [File] that already exists
   *           as a normal (non-directory) file.
   * @property conflictedTree true if this node or any of its descendants correspond to a proposed
   *           [File] that already exists as a normal (non-directory) file.
   */
  data class Node(val file: File,
                  val conflicted: Boolean,
                  private var icon: Icon,
                  private val children: MutableList<Node> = mutableListOf(),
                  private var conflictedTree: Boolean = conflicted) {

    fun hasConflicts() = conflictedTree

    fun getIcon() = icon

    fun isLeaf() = children.isEmpty()

    fun getChildCount() = children.size

    fun getIndexOfChild(child: Node) = children.indexOf(child)

    fun getChild(index: Int) = children[index]

    private fun findChildByFile(childFile: File) = children.find { it.file == childFile }

    private fun addChild(childNode: Node) {
      if (icon == DEFAULT_ICON) {
        // The file this node corresponds to was originally thought to be a regular file,
        // but now we know it's a directory. Change the node's icon to reflect this.
        icon = DIR_ICON
      }
      children.add(childNode)
    }

    /**
     * Given a node-relative path to a proposed [File] and an [Icon] with which to mark it,
     * this function recursively builds all the missing intermediate directory nodes
     * between this node and a newly-constructed leaf node corresponding to the proposed [File].
     *
     * @param relativePath a list of path segments pointing to the proposed [File]
     * @param icon the [Icon] with which the proposed [File] should be marked, or null if the
     *             proposed [File] should be marked with a default [Icon].
     */
    private fun addDescendant(relativePath: List<String>, icon: Icon?) {
      if (relativePath.isEmpty()) return

      val childFile = file.resolve(relativePath[0])
      var childNode = findChildByFile(childFile)

      if (relativePath.size == 1) {
        if (childNode != null) {
          // If a node for the descendant we're adding already exists, the descendant is an
          // intermediate directory that appeared in the path of another proposed file. Mark it
          // with the caller-specified icon, if one was given.
          if (icon != null) {
            childNode.icon = icon
          }
        }
        else {
          childNode = Node(childFile, childFile.isFile, when {
            icon != null -> icon
            childFile.isDirectory -> DIR_ICON
            else -> DEFAULT_ICON
          })
          addChild(childNode)
        }
      }
      else {
        if (childNode == null) {
          // If a node for the intermediate directory doesn't exist yet, make one.
          childNode = Node(childFile, false, DIR_ICON)
          addChild(childNode)
        }

        childNode.addDescendant(relativePath.drop(1), icon)
      }

      if (childNode.conflictedTree) {
        conflictedTree = true
      }
    }

    companion object {
      /**
       * Constructs a tree rooted at [rootDir] that contains nodes corresponding to each proposed
       * file in [proposedFiles] and any intermediate directories.
       *
       * @param rootDir the directory the root node of the tree should correspond to
       * @param proposedFiles a set of proposed files. Each file must be a descendant of [rootDir].
       * @param getIconForFile a function which, given a file, returns either the icon that it
       *        should be marked with or null to use a default icon
       *
       * @throws IllegalArgumentException if any of the files in [proposedFiles] is not a descendant
       *         of [rootDir]
       */
      fun makeTree(rootDir: File, proposedFiles: Set<File>, getIconForFile: (File) -> Icon?): Node {
        val rootNode = Node(rootDir, false, DIR_ICON)

        for (file in proposedFiles) {
          val icon = getIconForFile(file)
          val relativeFile = if (file.isAbsolute) file.relativeTo(rootDir) else file.normalize()

          if (relativeFile.startsWith("..")) throw IllegalArgumentException("$rootDir is not an ancestor of $file")
          rootNode.addDescendant(relativeFile.invariantSeparatorsPath.split("/"), icon)
        }

        return rootNode
      }
    }
  }

  override fun getRoot() = rootNode

  override fun isLeaf(node: Any?) = (node as Node).isLeaf()

  override fun getChildCount(parent: Any?) = (parent as Node).getChildCount()

  override fun getIndexOfChild(parent: Any?, child: Any?) = (parent as Node).getIndexOfChild(child as Node)

  override fun getChild(parent: Any?, index: Int) = (parent as Node).getChild(index)

  override fun valueForPathChanged(path: TreePath?, newValue: Any?) { }

  override fun removeTreeModelListener(l: TreeModelListener?) { }

  override fun addTreeModelListener(l: TreeModelListener?) { }
}