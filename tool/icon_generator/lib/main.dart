// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';

import 'cupertino.dart' as cupertino;
import 'material.dart' as material;

const String root =
    '/Users/devoncarew/projects/intellij/flutter-intellij/resources/flutter';

Future main() async {
  MyIconApp app = MyIconApp(material.icons, cupertino.icons);
  runApp(app);

  await pumpEventQueue();

  for (material.IconTuple icon in material.icons) {
    await findAndSave(icon.smallKey, '$root/material/${icon.name}.png',
        small: true);
    await findAndSave(icon.largeKey, '$root/material/${icon.name}@2x.png',
        small: false);
  }

  for (cupertino.IconTuple icon in cupertino.icons) {
    await findAndSave(icon.smallKey, '$root/cupertino/${icon.name}.png',
        small: true);
    await findAndSave(icon.largeKey, '$root/cupertino/${icon.name}@2x.png',
        small: false);
  }
}

class MyIconApp extends StatelessWidget {
  MyIconApp(this.materialIcons, this.cupertinoIcons)
      : super(key: new UniqueKey());

  final List<material.IconTuple> materialIcons; // 16x16
  final List<cupertino.IconTuple> cupertinoIcons; // 32x32

  @override
  Widget build(BuildContext context) {
    const Color color = const Color(0xFF777777);

    Stack cupertinoSmallStack = new Stack(
      children: cupertinoIcons.map<Widget>((cupertino.IconTuple icon) {
        return RepaintBoundary(
          child: Icon(
            icon.data,
            size: 16.0,
            color: color,
            key: icon.smallKey,
          ),
        );
      }).toList(),
    );

    Stack cupertinoLargeStack = new Stack(
      children: cupertinoIcons.map<Widget>((cupertino.IconTuple icon) {
        return RepaintBoundary(
          child: Icon(
            icon.data,
            size: 32.0,
            color: color,
            key: icon.largeKey,
          ),
        );
      }).toList(),
    );

    Stack materialSmallStack = new Stack(
      children: materialIcons.map<Widget>((material.IconTuple icon) {
        return RepaintBoundary(
          child: Icon(
            icon.data,
            size: 16.0,
            color: color,
            key: icon.smallKey,
          ),
        );
      }).toList(),
    );

    Stack materialLargeStack = new Stack(
      children: materialIcons.map<Widget>((material.IconTuple icon) {
        return RepaintBoundary(
          child: Icon(
            icon.data,
            size: 32.0,
            color: color,
            key: icon.largeKey,
          ),
        );
      }).toList(),
    );

    return MaterialApp(
      title: 'Flutter Demo',
      home: Center(
        child: new Column(
          children: <Widget>[
            new Row(children: <Widget>[
              cupertinoSmallStack,
              materialSmallStack,
            ]),
            new Row(children: <Widget>[
              cupertinoLargeStack,
              materialLargeStack,
            ]),
          ],
        ),
      ),
    );
  }
}

Future findAndSave(Key key, String path, {bool small: true}) async {
  Finder finder = find.byKey(key);

  final Iterable<Element> elements = finder.evaluate();
  Element element = elements.first;

  Future<ui.Image> imageFuture = _captureImage(element);

  final ui.Image image = await imageFuture;
  final ByteData bytes = await image.toByteData(format: ui.ImageByteFormat.png);

  new File(path).writeAsBytesSync(bytes.buffer.asUint8List());
  print('wrote $path');
}

Future<ui.Image> _captureImage(Element element) {
  RenderObject renderObject = element.renderObject;
  while (!renderObject.isRepaintBoundary) {
    renderObject = renderObject.parent;
    assert(renderObject != null);
  }

  assert(!renderObject.debugNeedsPaint);

  final OffsetLayer layer = renderObject.layer;
  return layer.toImage(renderObject.paintBounds);
}
