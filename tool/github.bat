rem Copyright 2020 The Chromium Authors. All rights reserved.
rem Use of this source code is governed by a BSD-style license that can be
rem found in the LICENSE file.

@echo on
echo "JAVA_HOME=%JAVA_HOME%"

echo "install dart"
choco install dart-sdk > choco.log
rem Use "call" to run the script otherwise this job ends when the script exits.
call RefreshEnv.cmd
echo "JAVA_HOME=%JAVA_HOME%"

echo "dart version"
dart --version

cd tool\plugin
echo "dart pub get"
rem Use "call" to run the script otherwise this job ends when the script exits.
call dart pub get --no-precompile
cd ..\..

echo "run tests"
set JAVA_HOME=%JAVA_HOME_11_X64%
echo "JAVA_HOME=%JAVA_HOME%"
dart tool\plugin\bin\main.dart test

echo "exit"
