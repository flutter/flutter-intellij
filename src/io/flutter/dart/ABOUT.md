<!--* freshness: { reviewed: '2026-10-25' } *-->
# Dart Plugin Integration

## Overview
Thin wrappers around the JetBrains Dart plugin: Dart SDK configuration (`DartSdkLibUtil`), pub action state (`DartPubActionBase`), Dart PSI helpers, Dart plugin version checks, and the Dart Tooling Daemon (DTD). Nothing in this package talks to the Dart Analysis Server directly.

## Interface
- [`DartPlugin`](DartPlugin.java)
- `DartPlugin.getInstance()`
- `DartPlugin.getDartSdk(Project)`
- `DartPlugin.isDartSdkEnabled(Module)`
- `DartPlugin.enableDartSdk(Module)`
- `DartPlugin.ensureDartSdkConfigured(Project, String)`
- `DartPlugin.setPubActionInProgress(boolean)`
- `DartPlugin.getDartPluginVersion()`
- `DartPlugin.isDartRunConfiguration(ConfigurationType)`
- `DartPlugin.isDartTestConfiguration(ConfigurationType)`
- [`DartPluginVersion`](DartPluginVersion.java)
- `DartPluginVersion.DartPluginVersion(String)`
- `DartPluginVersion.supportsPropertyEditor()`
- `DartPluginVersion.compareTo(DartPluginVersion)`
- [`DartPsiUtil`](DartPsiUtil.java)
- `DartPsiUtil.parseLiteralNumber(String)`
- `DartPsiUtil.getNewExprFromType(PsiElement)`
- `DartPsiUtil.getValueOfPositionalArgument(DartArguments, int)`
- `DartPsiUtil.getPositionalArgument(DartArguments, int)`
- `DartPsiUtil.getValueOfNamedArgument(DartArguments, String)`
- `DartPsiUtil.getNamedArgumentExpression(DartArguments, String)`
- `DartPsiUtil.topmostReferenceExpression(PsiElement)`
- [`DartSyntax`](DartSyntax.java)
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
- [`DtdRequest`](DtdRequest.java)
- `DtdRequest.REGISTER_VM_SERVICE` (`type` = `ConnectedApp.registerVmService`)
- `DtdRequest.UNREGISTER_VM_SERVICE` (`type` = `ConnectedApp.unregisterVmService`)
- [`DtdUtils`](DtdUtils.java)
- `DtdUtils.readyDtdService(Project)` (instance method; callers use `new DtdUtils()`)

## Invariants
- All public DartPlugin methods are `static`; `getInstance()` returns a static singleton that adds no state. (The class Javadoc describing a per-project service is outdated.)
- DartPsiUtil and DartSyntax are static utility classes with no instances.
- `DartPluginVersion.supportsPropertyEditor()` requires at least `243.26753.1` for major 243, at least `251.23774.318` for major 251, and otherwise major >= 244. `compareTo` returns -1 whenever this version is null or unparseable.
- DtdUtils keeps a static ConcurrentHashMap of CompletableFutures per Project, so only one waiter per project is created. It returns immediately if the DTD websocket is already ready; otherwise it polls every 500 ms and fails after 20s.

## Side Effects
- DartPlugin.enableDartSdk modifies the IntelliJ module configuration to enable Dart support.
- DartPlugin.ensureDartSdkConfigured modifies the Project's Dart SDK configuration.
- DartPlugin.setPubActionInProgress mutates a global static state tracking pub actions.
- DtdUtils.readyDtdService schedules polling and timeout background tasks on the application scheduled executor service.
