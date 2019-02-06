#!/bin/bash
sudo mvn -e clean install -DskipTests
cp ./target/arap-workflow-prescan-1.0.0.jar .
cp ./src/main/resources/*.xml .
./run.sh
