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

  VerifyCommand(this.runner) : super('verify') {
    argParser.addOption(
      'only-version',
      abbr: 'o',
      help: 'Only verify the specified IntelliJ version; useful for sharding '
          'builds on CI systems.',
    );
  }

  @override
  String get description =>
      'Execute the verify Gradle tasks for each build spec.';

  @override
  Future<int> doit() async {
    final argResults = this.argResults!;

    // Check to see if we should only be building a specific version.
    // (this only-version logic was copied from the make command in plugin.dart)
    final onlyVersion = argResults.option('only-version');

    var buildSpecs = specs;
    if (onlyVersion != null && onlyVersion.isNotEmpty) {
      buildSpecs = specs.where((spec) => spec.version == onlyVersion).toList();
      if (buildSpecs.isEmpty) {
        log("No spec found for version '$onlyVersion'");
        return 1;
      }
    }

    // run './gradlew verifyPluginProjectConfiguration'
    // run './gradlew verifyPluginStructure'
    // run './gradlew verifyPluginSignature'
    // run './gradlew verifyPlugin'
    var result = 0;
    for (var spec in buildSpecs) {
      log('\nverifyPluginProjectConfiguration for $spec:');
      result = await runner.runGradleCommand(
        ['verifyPluginProjectConfiguration'],
        spec,
        '1',
        'false',
      );
      if (result != 0) {
        return result;
      }
      log('\nverifyPluginStructure for $spec:');
      result = await runner.runGradleCommand(
        ['verifyPluginStructure'],
        spec,
        '1',
        'false',
      );
      if (result != 0) {
        return result;
      }
      log('\nverifyPluginSignature for $spec:');
      result = await runner.runGradleCommand(
        ['verifyPluginSignature'],
        spec,
        '1',
        'false',
      );
      if (result != 0) {
        return result;
      }
      log('\nverifyPlugin for $spec:');
      result = await runner.runGradleCommand(
        ['verifyPlugin'],
        spec,
        '1',
        'false',
      );
      if (result != 0) {
        return result;
      }
    }

    var verifiedVersions =
        buildSpecs.map((spec) => spec.name).toList().join(', ');
    log(
      '\nVerification of the ${buildSpecs.length} builds was '
      'successful: $verifiedVersions.',
    );
    return result;
  }
}
