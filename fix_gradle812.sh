#!/usr/bin/env bash
set -e

echo "===================================================="
echo " Application de Gradle 8.12 + Java 25 dans le build..."
echo "===================================================="

cat << 'WORKFLOW' > .github/workflows/build.yml
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

      - name: Setup Gradle Automatically (Gradle 8.12)
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.12'

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
WORKFLOW

git add .
git commit -m "Fix: Force Gradle 8.12 for Loom compatibility with Java 25"
git push origin main

echo " Pousse sur GitHub effectue ! Suivi du build en cours..."
sleep 3
gh workflow run build.yml || true
gh run watch

echo " Telechargement automatique du JAR compile..."
gh run download -n Lifesteal-Fabric-26.3-Jar
ls -lh build/libs/*.jar 2>/dev/null || ls -lh *.jar 2>/dev/null || true
