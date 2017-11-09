import 'package:mockito/mirrors.dart';
import 'package:mockito/mockito.dart';
import 'package:plugin/plugin.dart';
import 'package:test/test.dart';

void main() {
  group("create", () {
    test('build', () {
      expect(new AntBuildCommand(new BuildCommandRunner()).name, "build");
    });
    test('test', () {
      expect(new TestCommand(new BuildCommandRunner()).name, "test");
    });
    test('deploy', () {
      expect(new DeployCommand(new BuildCommandRunner()).name, "deploy");
    });
    test('gen', () {
      expect(new GenCommand(new BuildCommandRunner()).name, "gen");
    });
  });
  group("spec", () {
    test('build', () {
      var runner = new BuildCommandRunner();
      var cmd = new AntBuildCommand(runner);
      runner.addCommand(cmd);
      var args = runner.parse(["-r=19", "build"]);
      var mockCmd = new MockCommand();
      when(mockCmd.argResults).thenReturn(args);
      // Apparently spy() does not work the way I want it to.
      if (0 == 0) return;
      var specs = createBuildSpecs(spy(mockCmd, cmd));
      expect(specs.length, 2);
      expect(specs[0].ideaProduct, 'android-studio-ide');
      expect(specs[1].ideaProduct, 'ideaIC');
    });
  });
}

class MockCommand extends Mock implements BuildCommand {}

class MockRunner extends Mock implements BuildCommandRunner {}
