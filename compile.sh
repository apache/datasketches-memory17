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
export BASEDIR=/Users/lrhodes/dev/git/Apache/datasketches-memory17
echo "# --- COMPILATION & PACKAGING ---"

echo " # creating clean directories"
rm -rf $BASEDIR/target2
mkdir $BASEDIR/target2
mkdir $BASEDIR/target2/classes

rm -rf $BASEDIR/target2/test-classes
mkdir $BASEDIR/target2/test-classes

rm -rf $BASEDIR/mods
mkdir $BASEDIR/mods

rm -rf $BASEDIR/libs/datasketches-memory-3.0.0.jar
rm -rf $BASEDIR/libs/datasketches-memory-tests-3.0.0.jar

echo " # compile classes from src/main/java"
$JAVAC \
  --add-modules jdk.incubator.foreign \
  -d $BASEDIR/target2/classes \
  $(find $BASEDIR/src/main/java -name '*.java')

echo " # create jar datasketches-memory-3.0.0.jar from src/main/java"
$JAR --create \
  --file $BASEDIR/libs/datasketches-memory-3.0.0.jar \
  -C $BASEDIR/target2/classes .

echo " # compile tests from src/test/java"
$JAVAC \
  --add-modules jdk.incubator.foreign \
  --class-path $BASEDIR/libs/* \
  -d $BASEDIR/target2/test-classes \
  $(find $BASEDIR/src/test/java -name *.java)

echo " # create datasketches-memory-tests-3.0.0.jar"
$JAR --create \
  --file $BASEDIR/libs/datasketches-memory-tests-3.0.0.jar \
  -C $BASEDIR/target2/test-classes . -C $BASEDIR/src/test/resources .

echo " # Run the tests"

$JAVA @$BASEDIR/scripts/myargfile.txt




