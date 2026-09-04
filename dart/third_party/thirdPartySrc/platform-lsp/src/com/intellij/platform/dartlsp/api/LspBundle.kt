// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.dartlsp.api

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE = "messages.DartLspBundle"

@ApiStatus.Internal
object LspBundle {
  private val instance = DynamicBundle(LspBundle::class.java, BUNDLE)

  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
    instance.getMessage(key, *params)

  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<String> =
    instance.getLazyMessage(key, *params)
}
