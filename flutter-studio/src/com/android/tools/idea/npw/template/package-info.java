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
 * Android project components depend heavily on FreeMarker templates and classes in the
 * {@link com.android.tools.idea.templates} namespace - using that framework, Android-aware
 * configurations are loaded, initialized, modified, and then rendered into the projct.
 *
 * This package contains the UI views that interact with the users on one side and Freemarker on
 * the other.
 */
package com.android.tools.idea.npw.template;