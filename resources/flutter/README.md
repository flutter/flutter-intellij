The contents from this directory are copied from
https://github.com/flutter/tools_metadata/tree/master/resources.

Contents:

- `colors/`: Material, Cupertino, and CSS color tables (`.json` and `.properties`).
- `icons/`: Material and Cupertino icon images (`material/`, `cupertino/`) and
  their name-to-file maps ([`material.properties`](icons/material.properties),
  [`cupertino.properties`](icons/cupertino.properties)).
- [`catalog/widgets.json`](catalog/widgets.json): Widget catalog.
- [`version.json`](version.json): The Flutter version the copied metadata was
  generated from.

Only the `.properties` files are loaded by the plugin, in
[`FlutterColors`](../../src/io/flutter/editor/FlutterColors.java),
[`FlutterCupertinoColors`](../../src/io/flutter/editor/FlutterCupertinoColors.java),
[`FlutterMaterialIcons`](../../src/io/flutter/editor/FlutterMaterialIcons.java),
and [`FlutterCupertinoIcons`](../../src/io/flutter/editor/FlutterCupertinoIcons.java).
Nothing in `src/` reads `catalog/widgets.json`, `version.json`, or the color
`.json` files.

When updating, copy the upstream files and update `version.json` so it
records which upstream version this copy matches.
