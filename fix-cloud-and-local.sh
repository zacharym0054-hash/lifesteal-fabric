#!/usr/bin/env bash
set -e

echo "===================================================="
echo " Diagnostic & Réparation V2 - Lifesteal Fabric"
echo "===================================================="

# 1. Gestion des permissions
REAL_USER="${SUDO_USER:-$USER}"
REAL_GROUP=$(id -gn "$REAL_USER")
echo "[1/6] Correction des permissions pour $REAL_USER..."
if [ "$EUID" -eq 0 ]; then
    chown -R "$REAL_USER:$REAL_GROUP" .
else
    sudo chown -R "$REAL_USER:$REAL_GROUP" .
fi

# 2. Forcer un settings.gradle propre en haut de fichier
echo "[2/6] Configuration de settings.gradle..."
if [ -f "settings.gradle" ]; then
    cp settings.gradle settings.gradle.bak
fi

cat << 'SETTINGS' > settings.gradle
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
SETTINGS

# Conserver rootProject.name s'il existait dans l'ancien settings.gradle
if [ -f "settings.gradle.bak" ]; then
    grep "rootProject.name" settings.gradle.bak >> settings.gradle || true
fi
echo " -> settings.gradle configuré proprement."

# 3. Installation du Gradle Wrapper sans faire planter l'évaluation du build.gradle
echo "[3/6] Téléchargement et installation du Gradle Wrapper..."
rm -rf /tmp/gradle-extract
mkdir -p /tmp/gradle-extract

# Si le zip est déjà dans /tmp, on le réutilise pour gagner du temps !
if [ ! -f "/tmp/gradle-9.0.zip" ]; then
    wget -q --show-progress https://services.gradle.org/distributions/gradle-9.0-bin.zip -O /tmp/gradle-9.0.zip
fi

unzip -q -o /tmp/gradle-9.0.zip -d /tmp/gradle-extract

# Trouver dynamiquement le binaire gradle (peu importe si le dossier s'appelle 9.0 ou 9.0.0)
GRADLE_BIN=$(find /tmp/gradle-extract -name "gradle" -type f | grep "/bin/gradle$" | head -n 1)

if [ -z "$GRADLE_BIN" ]; then
    echo "ERREUR : Binaire Gradle introuvable dans l'archive."
    exit 1
fi
chmod +x "$GRADLE_BIN"
echo " -> Binaire trouvé : $GRADLE_BIN"

# ASTUCE : On masque temporairement build.gradle et settings.gradle pour que Gradle génère
# les fichiers gradlew sans essayer de télécharger Fabric Loom tout de suite
mv build.gradle build.gradle.temp_hide
mv settings.gradle settings.gradle.temp_hide
touch build.gradle settings.gradle

"$GRADLE_BIN" wrapper --no-daemon

# On restaure immédiatement tes vrais fichiers
mv build.gradle.temp_hide build.gradle
mv settings.gradle.temp_hide settings.gradle
chmod +x gradlew
rm -rf /tmp/gradle-extract
echo " -> ./gradlew généré avec succès !"

# 4. Patch de 2.sh pour qu'il n'appelle JAMAIS le vieux gradle 4.4.1 de Debian
echo "[4/6] Patch de 2.sh..."
if [ -f "2.sh" ]; then
    cp 2.sh 2.sh.bak
    # Remplace les variables ou appels à gradle par ./gradlew
    sed -i 's/GRADLE_CMD="gradle"/GRADLE_CMD=".\/gradlew"/g' 2.sh
    sed -i 's/GRADLE="gradle"/GRADLE=".\/gradlew"/g' 2.sh
    sed -i 's/\bgradle\b/\.\/gradlew/g' 2.sh
    sed -i 's|\./\./gradlew|\./gradlew|g' 2.sh
    chmod +x 2.sh
    echo " -> 2.sh mis à jour pour forcer ./gradlew."
fi

# 5. Création du Workflow GitHub Actions (100% autonome)
echo "[5/6] Création du workflow GitHub Actions (.github/workflows/build.yml)..."
mkdir -p .github/workflows
cat << 'WORKFLOW' > .github/workflows/build.yml
name: Build Lifesteal Fabric

on:
  push:
    branches: [ "*" ]
  pull_request:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout du code
        uses: actions/checkout@v4

      - name: Configuration de Java 25 (Temurin EA)
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          check-latest: true

      - name: Configuration de Gradle 9.0
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '9.0'

      - name: Compilation du mod avec Gradle 9
        run: gradle build --no-daemon

      - name: Upload du fichier JAR compilé
        uses: actions/upload-artifact@v4
        with:
          name: lifesteal-fabric-26.3-jar
          path: |
            build/libs/*.jar
            !build/libs/*-sources.jar
            !build/libs/*-dev.jar
WORKFLOW
echo " -> Workflow Cloud créé."

# 6. Remise au propre des permissions
echo "[6/6] Finalisation des permissions..."
if [ "$EUID" -eq 0 ]; then
    chown -R "$REAL_USER:$REAL_GROUP" .
else
    sudo chown -R "$REAL_USER:$REAL_GROUP" .
fi

echo ""
echo "===================================================="
echo " VÉRIFICATION DU WRAPPER INSTALLÉ :"
./gradlew --version | grep -E "Gradle |JVM:"
echo "===================================================="
echo "Tout est corrigé !"
echo "-> Pour tester en local (SANS sudo) : ./gradlew build"
echo "-> Pour lancer ton script (SANS sudo) : bash 2.sh"
echo "-> Pour envoyer sur GitHub : git add . && git commit -m 'Build Cloud' && git push"
