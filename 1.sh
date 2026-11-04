#!/usr/bin/env bash
set -e

echo "===================================================="
echo " Correction : Passage de Java 21 a Java 25 dans le build..."
echo "===================================================="

# Réécriture propre de .github/workflows/build.yml avec Java 25
cat << 'EOF' > .github/workflows/build.yml
name: Cloud Build Fabric Lifesteal

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Setup Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Setup Gradle Automatically (Gradle 8.11.1)
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.11.1'

      - name: Compile Fabric Mod
        run: gradle build --no-daemon

      - name: Upload Compiled JAR
        uses: actions/upload-artifact@v4
        with:
          name: Lifesteal-Fabric-26.3-Jar
          path: |
            build/libs/*.jar
            !build/libs/*-sources.jar
            !build/libs/*-dev.jar
EOF

# Mise à jour du target Java dans build.gradle pour s'assurer qu'il compile bien en 25
sed -i 's/it.options.release = 21/it.options.release = 25/g' build.gradle
sed -i 's/sourceCompatibility = JavaVersion.VERSION_21/sourceCompatibility = JavaVersion.VERSION_25/g' build.gradle
sed -i 's/targetCompatibility = JavaVersion.VERSION_21/targetCompatibility = JavaVersion.VERSION_25/g' build.gradle

echo " Envoi des modifications sur GitHub..."
git add .
git commit -m "Fix: Upgrade Java version from 21 to 25 for Minecraft 26.3"
git push origin main

echo " Relance du workflow et suivi en direct..."
sleep 3
gh workflow run build.yml || true
gh run watch

echo " Téléchargement automatique du JAR compilé..."
gh run download -n Lifesteal-Fabric-26.3-Jar

echo "===================================================="
echo " Termine ! Le JAR 26.3 compile sous Java 25."
echo "===================================================="
ls -lh build/libs/*.jar 2>/dev/null || ls -lh *.jar 2>/dev/null || true
