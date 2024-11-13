import 'package:plugin_tool/plugin.dart';
import 'package:plugin_tool/runner.dart';

import 'util.dart';

/// Run the verification tasks for each of the build specifications for the
/// Flutter Plugin for IntelliJ, see:
/// https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-verifyplugin
/// https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#tasks-verifypluginconfiguration
class VerifyCommand extends ProductCommand {
  @override
  final BuildCommandRunner runner;

  VerifyCommand(this.runner) : super('verify');

  @override
  String get description =>
      'Execute the verifyPlugin and verifyPluginConfiguration Gradle tasks for each build spec.';

  @override
  Future<int> doit() async {
    // run './gradlew verifyPluginProjectConfiguration'
    // run './gradlew verifyPluginStructure'
    // run './gradlew verifyPluginSignature'
    // run './gradlew verifyPlugin'
    var result = 0;
    for (var spec in specs) {
      log('\nverifyPluginProjectConfiguration for $spec:');
      result = await runner
          .runGradleCommand(['verifyPluginProjectConfiguration'], spec, '1', 'false');
      if (result != 0) {
        return result;
      }
      log('\nverifyPluginStructure for $spec:');
      result =
          await runner.runGradleCommand(['verifyPluginStructure'], spec, '1', 'false');
      if (result != 0) {
        return result;
      }
      log('\nverifyPluginSignature for $spec:');
      result =
      await runner.runGradleCommand(['verifyPluginSignature'], spec, '1', 'false');
      if (result != 0) {
        return result;
      }
      log('\nverifyPlugin for $spec:');
      result =
      await runner.runGradleCommand(['verifyPlugin'], spec, '1', 'false');
      if (result != 0) {
        return result;
      }
    }
    log('\nSummary: verification of all ${specs
        .length} build specifications was successful.');
    return result;
  }
}
