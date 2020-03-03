/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.project

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.builder.model.SourceProvider
import com.android.tools.idea.projectsystem.AndroidModuleTemplate
import java.io.File

class SourceProviderAdapter(private val name: String, private val paths: AndroidModuleTemplate) : SourceProvider {

  override fun getName(): String {
    return name
  }

  override fun getManifestFile(): File {
    return File(paths.manifestDirectory, ANDROID_MANIFEST_XML)
  }

  override fun getJavaDirectories(): Collection<File> {
    val srcDirectory = paths.getSrcDirectory(null)
    return if (srcDirectory == null) emptyList() else setOf(srcDirectory)
  }

  override fun getResourcesDirectories(): Collection<File> {
    return emptyList()
  }

  override fun getAidlDirectories(): Collection<File> {
    val aidlDirectory = paths.getAidlDirectory(null)
    return if (aidlDirectory == null) emptyList() else setOf(aidlDirectory)
  }

  override fun getRenderscriptDirectories(): Collection<File> {
    return emptyList()
  }

  override fun getCDirectories(): Collection<File> {
    return emptyList()
  }

  override fun getCppDirectories(): Collection<File> {
    return emptyList()
  }

  override fun getResDirectories(): Collection<File> {
    return paths.resDirectories
  }

  override fun getAssetsDirectories(): Collection<File> {
    return emptyList()
  }

  override fun getJniLibsDirectories(): Collection<File> {
    return emptyList()
  }

  override fun getShadersDirectories(): Collection<File> {
    return emptyList()
  }
}
