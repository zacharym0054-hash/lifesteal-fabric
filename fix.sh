cat << 'EOF' > fix_gradle812.sh
#!/usr/bin/env bash
set -e

echo "===================================================="
echo " Passage de Gradle 8.11.1 a Gradle 8.12 pour Loom..."
echo "===================================================="

# Met à jour le workflow avec Gradle 8.12 et Java 25
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
git commit -m "Fix: Upgrade Gradle version to 8.12 for Fabric Loom compatibility"
git push origin main

echo " Workflow mis a jour et envoye ! Suivi du build en cours..."
sleep 3
gh workflow run build.yml || true
gh run watch

echo " Telechargement du fichier JAR compile..."
gh run download -n Lifesteal-Fabric-26.3-Jar
ls -lh build/libs/*.jar 2>/dev/null || ls -lh *.jar 2>/dev/null || true
EOF

chmod +x fix_gradle812.sh
bash fix_gradle812.sh
