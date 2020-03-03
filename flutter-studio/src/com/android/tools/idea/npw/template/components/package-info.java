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

/**
 * The step for configuring project parameters creates various UI components in order to collect
 * inputs from the user. This package houses the classes which generate those UI components.
 *
 * Admidst all the noise, there are two classes to pay close attention to -
 *
 * {@link com.android.tools.idea.npw.template.components.ComponentProvider} which is a top-level
 * interface that makes it easy to link Swing components and Swing properties.
 *
 * {@link com.android.tools.idea.npw.template.components.ParameterComponentProvider} which is a
 * ComponentProvider with extra logic for handling defaults provided by the
 * {@link com.android.tools.idea.templates.Parameter} class.
 */
package com.android.tools.idea.npw.template.components;