#!/bin/bash

# Build two distribution files
#   artifacts/flutter-intellij.jar
#   artifacts/flutter-studio.zip

rm -rf build/*
ant -Ddart.plugin.version=171.4424.10 -Didea.version=171.4333198 -Didea.product=android-studio-ide \
  -DDEPENDS=\<depends\>com.intellij.modules.androidstudio\</depends\> \
  -DPLUGINID=io.flutter.as -DSINCE=171.1 -DUNTIL=171.\*
mv build/flutter-studio.zip artifacts

rm -rf build/*
ant -Ddart.plugin.version=172.3317.48 -Didea.version=2017.2 -Didea.product=ideaIC
mv build/flutter-intellij.jar artifacts
