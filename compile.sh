#!/bin/bash -e

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

#Java executables for mac environment
export JAVAC=$JAVA17_HOME/bin/javac
export JAR=$JAVA17_HOME/bin/jar
export JAVA=$JAVA17_HOME/bin/java

echo "# --- COMPILATION & PACKAGING ---"

echo " # creating clean directories"
rm -rf target
mkdir target
mkdir target/classes
rm -rf target/test-classes
mkdir target/test-classes
rm -rf mods
mkdir mods
rm -rf libs/datasketches-memory-3.0.0.jar
rm -rf libs/datasketches-memory-3.0.0-tests.jar

echo " # compile classes from src/main/java"
$JAVAC \
  -d target/classes \
  $(find src/main/java -name '*.java')

echo " # create jar datasketches-memory-3.0.0.jar from src/main/java"
$JAR --create \
  --file mods/datasketches-memory-3.0.0.jar \
  -C target/classes .

echo " # compile tests from src/test/java"
$JAVAC \
  --class-path 'libs/*' \
  -d target/test-classes \
  $(find src/test/java -name '*.java')

echo " # create datasketches-memory-3.0.0-tests.jar"
$JAR --create \
  --file libs/datasketches-memory-3.0.0-tests.jar \
  -C target/test-classes .


