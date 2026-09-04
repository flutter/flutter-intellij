// This is a generated file. Not intended for manual editing.
package com.jetbrains.lang.dart.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DartClassMembers extends DartExecutionScope {

  @NotNull
  List<DartFactoryConstructorDeclaration> getFactoryConstructorDeclarationList();

  @NotNull
  List<DartGetterDeclaration> getGetterDeclarationList();

  @NotNull
  List<DartIncompleteDeclaration> getIncompleteDeclarationList();

  @NotNull
  List<DartMethodDeclaration> getMethodDeclarationList();

  @NotNull
  List<DartNamedConstructorDeclaration> getNamedConstructorDeclarationList();

  @NotNull
  List<DartNewConstructorDeclaration> getNewConstructorDeclarationList();

  @NotNull
  List<DartSetterDeclaration> getSetterDeclarationList();

  @NotNull
  List<DartThisDeclaration> getThisDeclarationList();

  @NotNull
  List<DartVarDeclarationList> getVarDeclarationListList();

}
