// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.ide.editor;

import com.intellij.formatting.Indent;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.codeStyle.SemanticEditorPosition;
import com.intellij.psi.impl.source.codeStyle.lineIndent.IndentCalculator;
import com.intellij.psi.impl.source.codeStyle.lineIndent.JavaLikeLangLineIndentProvider;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.DartTokenTypesSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.psi.impl.source.codeStyle.lineIndent.JavaLikeLangLineIndentProvider.JavaLikeElement.*;

public final class DartLineIndentProvider extends JavaLikeLangLineIndentProvider {

    private static volatile Map<IElementType, SemanticEditorPosition.SyntaxElement> SYNTAX_MAP;

    private static @NotNull Map<IElementType, SemanticEditorPosition.SyntaxElement> getSyntaxMap() {
        if (SYNTAX_MAP == null) {
            synchronized (DartLineIndentProvider.class) {
                if (SYNTAX_MAP == null) {
                    final Map<IElementType, SemanticEditorPosition.SyntaxElement> map = new HashMap<>(32);
                    map.put(DartTokenTypesSets.WHITE_SPACE, Whitespace);
                    map.put(DartTokenTypes.SEMICOLON, Semicolon);
                    map.put(DartTokenTypes.LBRACE, BlockOpeningBrace);
                    map.put(DartTokenTypes.RBRACE, BlockClosingBrace);
                    map.put(DartTokenTypes.LBRACKET, ArrayOpeningBracket);
                    map.put(DartTokenTypes.RBRACKET, ArrayClosingBracket);
                    map.put(DartTokenTypes.LPAREN, LeftParenthesis);
                    map.put(DartTokenTypes.RPAREN, RightParenthesis);
                    map.put(DartTokenTypes.COLON, Colon);
                    map.put(DartTokenTypes.CASE, SwitchCase);
                    map.put(DartTokenTypes.DEFAULT, SwitchDefault);
                    map.put(DartTokenTypes.IF, IfKeyword);
                    map.put(DartTokenTypes.ELSE, ElseKeyword);
                    map.put(DartTokenTypes.FOR, ForKeyword);
                    map.put(DartTokenTypes.DO, DoKeyword);
                    map.put(DartTokenTypes.TRY, TryKeyword);
                    map.put(DartTokenTypesSets.MULTI_LINE_COMMENT, BlockComment);
                    map.put(DartTokenTypesSets.MULTI_LINE_COMMENT_END, DocBlockEnd);
                    map.put(DartTokenTypesSets.MULTI_LINE_DOC_COMMENT_START, DocBlockStart);
                    map.put(DartTokenTypes.COMMA, Comma);
                    map.put(DartTokenTypesSets.SINGLE_LINE_COMMENT, LineComment);
                    map.put(DartTokenTypesSets.SINGLE_LINE_DOC_COMMENT, LineComment);
                    SYNTAX_MAP = map;
                }
            }
        }
        return SYNTAX_MAP;
    }

  @Override
  protected @Nullable SemanticEditorPosition.SyntaxElement mapType(@NotNull IElementType tokenType) {
      return getSyntaxMap().get(tokenType);
  }

  @Override
  public boolean isSuitableForLanguage(@NotNull Language language) {
    return language.is(DartLanguage.INSTANCE);
  }

  @Override
  protected Indent.Type getIndentTypeInBrackets() {
    return Indent.Type.NORMAL;
  }

  @Override
  protected @Nullable IndentCalculator getIndent(@NotNull Project project, @NotNull Editor editor, @Nullable Language language, int offset) {
    if (getPosition(editor, offset).matchesRule(position -> position.before().isAt(LeftParenthesis))) {
      // Need to skip a common logic and force formatter-based line indent provider to work, because
      // there may be different indents inside parentheses, see https://github.com/dart-lang/dart_style/issues/551
      return null;
    }
    return super.getIndent(project, editor, language, offset);
  }
}
