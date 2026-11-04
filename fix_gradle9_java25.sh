#!/usr/bin/env bash
set -e

echo "===================================================="
echo " 🔥 Patch : Gradle 9.x (Support Java 25 natif) + Loom"
echo "===================================================="

WORKFLOW_FILE=".github/workflows/build.yml"

# 1. Récupérer la dernière version actuelle de Gradle (9.x) via l'API officielle de Gradle
echo "-> Détection de la dernière version de Gradle compatible Java 25..."
LATEST_GRADLE=$(curl -s https://services.gradle.org/versions/current | grep -oP '(?<="version" : ")[^"]+' || echo "9.2.1")
if [ -z "$LATEST_GRADLE" ]; then
    LATEST_GRADLE="9.2.1"
fi
echo "   Version de Gradle détectée : $LATEST_GRADLE"

# 2. Réécriture propre de .github/workflows/build.yml
echo "-> Mise à jour propre de $WORKFLOW_FILE..."
cat << WORKFLOW_EOF > "$WORKFLOW_FILE"
name: Cloud Build Fabric Lifesteal

on:
  push:
    branches: [ "main" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Setup Java 25
        uses: actions/setup-java@v5
        with:
          distribution: 'temurin'
          java-version: '25'

      - name: Setup Gradle ${LATEST_GRADLE}
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '${LATEST_GRADLE}'
          cache- disabled: true

      - name: Compile Fabric Mod
        run: gradle build --no-daemon --stacktrace

      - name: Upload Compiled JAR
        uses: actions/upload-artifact@v4
        with:
          name: lifesteal-mod-jar
          path: |
            build/libs/*.jar
            !build/libs/*-sources.jar
            !build/libs/*-dev.jar
WORKFLOW_EOF

# Corriger l'espace dans cache-disabled si besoin
sed -i 's/cache- disabled: true/cache-disabled: true/g' "$WORKFLOW_FILE"

# 3. Mise à jour du gradle-wrapper.properties s'il existe
if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    sed -i -E "s|gradle-[0-9.]+-bin\.zip|gradle-${LATEST_GRADLE}-bin.zip|g" gradle/wrapper/gradle-wrapper.properties
    sed -i -E "s|gradle-[0-9.]+-all\.zip|gradle-${LATEST_GRADLE}-bin.zip|g" gradle/wrapper/gradle-wrapper.properties
fi

# 4. Commit & Push
echo "-> Commit et Push sur GitHub..."
git add -A
git commit -m "Fix: Upgrade to Gradle $LATEST_GRADLE for native Java 25 and Loom 1.19 support" || true
git push origin main

# 5. Lancement et suivi du workflow
echo "-> Lancement du Cloud Build sur GitHub..."
gh workflow run build.yml --ref main
sleep 4

RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId --jq '.[0].databaseId')
echo "-> Suivi du run ID: $RUN_ID..."
if ! gh run watch "$RUN_ID" --exit-status; then
    echo "===================================================="
    echo " ❌ Le build a échoué ! Voici le log d'erreur exact :"
    echo "===================================================="
    JOB_ID=$(gh run view "$RUN_ID" --json jobs --jq '.jobs[0].databaseId')
    gh run view --job="$JOB_ID" --log-failed
    exit 1
fi

# 6. Téléchargement du JAR
echo "-> Téléchargement du JAR compilé..."
mkdir -p ./build_output
gh run download "$RUN_ID" -D ./build_output
echo "===================================================="
echo " ✅ Terminé ! Ton JAR est dans ./build_output"
echo "===================================================="
