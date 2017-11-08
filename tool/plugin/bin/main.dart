import 'package:plugin/plugin.dart' as plugin;

/// Run from IntelliJ with a run configuration that has the working directory
/// set to the project root directory.
/// TODO(messick) Write a bash script to drive it from the command line.
main(List<String> arguments) async {
  await plugin.main(arguments);
}
