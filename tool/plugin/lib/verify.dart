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
    // run './gradlew verifyPlugin'
    // run './gradlew verifyPluginConfiguration'
    var result = 0;
    for (var spec in specs) {
      log('\nverifyPluginConfiguration for $spec:');
      result = await runner
          .runGradleCommand(['verifyPluginConfiguration'], spec, '1', 'false');
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
