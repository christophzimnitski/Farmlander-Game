#!/bin/bash
# Double-click this file to launch Farmlander.
# It runs the game via the Gradle wrapper from the project folder.
cd "$(dirname "$0")" || exit 1
echo "Launching Farmlander..."
exec ./gradlew run --console=plain
