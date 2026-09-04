/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.jetbrains.lang.dart.lsp

import com.jetbrains.lang.dart.DartCodeInsightFixtureTestCase
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class DartLspDiagnosticConverterTest : DartCodeInsightFixtureTestCase() {

    fun testConvertErrorDiagnostic() {
        val testFile = myFixture.addFileToProject(
            "lib/test_diag.dart",
            """
            void main() {
              int x = "string";
            }
            """.trimIndent()
        )
        val fileUri = "file://${testFile.virtualFile.path}"
        val das = DartAnalysisServerService.getInstance(project)

        val diagnostic = Diagnostic(
            Range(Position(1, 10), Position(1, 18)),
            "A value of type 'String' can't be assigned to a variable of type 'int'.",
            DiagnosticSeverity.Error,
            "dart",
            "invalid_assignment"
        )

        val error = DartLspDiagnosticConverter.convertDiagnosticToAnalysisError(
            project,
            das,
            fileUri,
            diagnostic
        )

        assertEquals("ERROR", error.severity)
        assertEquals("COMPILE_TIME_ERROR", error.type)
        assertEquals("invalid_assignment", error.code)
        assertEquals("A value of type 'String' can't be assigned to a variable of type 'int'.", error.message)
        assertEquals(2, error.location.startLine) // 1-indexed line number in table
        assertEquals(2, error.location.endLine)
    }

    fun testConvertTodoDiagnostic() {
        val testFile = myFixture.addFileToProject(
            "lib/test_todo.dart",
            "// TODO: fix this"
        )
        val fileUri = "file://${testFile.virtualFile.path}"
        val das = DartAnalysisServerService.getInstance(project)

        val diagnostic = Diagnostic(
            Range(Position(0, 3), Position(0, 17)),
            "TODO: fix this",
            DiagnosticSeverity.Information,
            "dart",
            "todo"
        )

        val error = DartLspDiagnosticConverter.convertDiagnosticToAnalysisError(
            project,
            das,
            fileUri,
            diagnostic
        )

        assertEquals("INFO", error.severity)
        assertEquals("TODO", error.type)
        assertEquals("todo", error.code)
        assertEquals(1, error.location.startLine)
    }
}
