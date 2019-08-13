#!/bin/bash

# Copyright 2019 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail the script on failures.
set -e

if [[ $TRAVIS_OS_NAME == "windows" ]]; then

  export TOOLS_PATH=tools
  mkdir ${TOOLS_PATH}

  export JAVA_HOME=${JAVA_HOME:-$TOOLS_PATH/jdk}
  export JAVA_VERSION=${JAVA_VERSION:-11.0.2}

  pushd ${TOOLS_PATH}

  if [ ! -f "${TOOLS_PATH}/jdk-${JAVA_VERSION}-windows-i586.zip" ]; then
    echo "Downloading https://download.oracle.com/java/GA/jdk11/9/GPL/openjdk-${JAVA_VERSION}_windows-x64_bin.zip..."
    curl -fsS -o openjdk-${JAVA_VERSION}_windows-x64_bin.zip https://download.oracle.com/java/GA/jdk11/9/GPL/openjdk-${JAVA_VERSION}_windows-x64_bin.zip
    rm -rf ${JAVA_HOME}
  fi
  if [ ! -d "${JAVA_HOME}" ]; then
    echo "Extracting jdk-${JAVA_VERSION}-windows-i586.zip..."
    7z x openjdk-${JAVA_VERSION}_windows-x64_bin.zip -y -o${TOOLS_PATH}/
    mv ${TOOLS_PATH}/jdk-${JAVA_VERSION} ${JAVA_HOME}
  fi

  popd

  export PATH=${PATH}:${JAVA_HOME}

  ls -l ${JAVA_HOME}
  echo ${PATH}

  java -version

  # TODO: install ant

else

  sudo apt-get update
  sudo apt-get install ant

  echo $(ant -version)

fi
