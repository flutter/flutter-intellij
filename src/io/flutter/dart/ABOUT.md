<!--* freshness: { reviewed: '2026-09-07' } *-->
# Dart Analysis Integration

## Overview
Wrappers and managers for interacting with the Dart Analysis Server, parsing Dart files, and tracking SDK settings.

## Interface
- `DartPlugin.getInstance()`
- `DartPlugin.getDartSdk(Project)`
- `DartPlugin.isDartSdkEnabled(Module)`
- `DartPlugin.enableDartSdk(Module)`
- `DartPlugin.ensureDartSdkConfigured(Project, String)`
- `DartPlugin.setPubActionInProgress(boolean)`
- `DartPlugin.getDartPluginVersion()`
- `DartPlugin.isDartRunConfiguration(ConfigurationType)`
- `DartPlugin.isDartTestConfiguration(ConfigurationType)`
- `DartPluginVersion.DartPluginVersion(String)`
- `DartPluginVersion.supportsPropertyEditor()`
- `DartPluginVersion.compareTo(DartPluginVersion)`
- `DartPsiUtil.parseLiteralNumber(String)`
- `DartPsiUtil.getNewExprFromType(PsiElement)`
- `DartPsiUtil.getValueOfPositionalArgument(DartArguments, int)`
- `DartPsiUtil.getPositionalArgument(DartArguments, int)`
- `DartPsiUtil.getValueOfNamedArgument(DartArguments, String)`
- `DartPsiUtil.getNamedArgumentExpression(DartArguments, String)`
- `DartPsiUtil.topmostReferenceExpression(PsiElement)`
- `DartSyntax.findEnclosingFunctionCall(PsiElement, String)`
- `DartSyntax.findEnclosingFunctionCall(PsiElement, Pattern)`
- `DartSyntax.findClosestEnclosingFunctionCall(PsiElement)`
- `DartSyntax.findEnclosingNewExpression(PsiElement)`
- `DartSyntax.findEnclosingReferenceExpression(PsiElement)`
- `DartSyntax.getArgument(DartCallExpression, int, Class)`
- `DartSyntax.isCallToFunctionNamed(DartCallExpression, String)`
- `DartSyntax.isCallToFunctionMatching(DartCallExpression, Pattern)`
- `DartSyntax.isMainFunctionDeclaration(PsiElement)`
- `DartSyntax.unquote(DartStringLiteralExpression)`
- `DartSyntax.getCalledFunctionName(DartCallExpression)`
- `DtdRequest.REGISTER_VM_SERVICE`
- `DtdRequest.UNREGISTER_VM_SERVICE`
- `DtdUtils.readyDtdService(Project)`

## Invariants
- DartPlugin is a singleton accessed via getInstance().
- DartPsiUtil and DartSyntax are static utility classes with no instances.
- DtdUtils uses a ConcurrentHashMap to cache CompletableFutures per Project, ensuring only one waiter per project is created.

## Side Effects
- DartPlugin.enableDartSdk modifies the IntelliJ module configuration to enable Dart support.
- DartPlugin.ensureDartSdkConfigured modifies the Project's Dart SDK configuration.
- DartPlugin.setPubActionInProgress mutates a global static state tracking pub actions.
- DtdUtils.readyDtdService schedules polling and timeout background tasks on the application scheduled executor service.
