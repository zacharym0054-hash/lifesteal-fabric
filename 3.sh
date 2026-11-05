cat << 'EOF' > fix-cloud-and-local.sh
#!/usr/bin/env bash
set -e

echo "===================================================="
echo " Diagnostic & Réparation Auto - Lifesteal Fabric"
echo "===================================================="

# 1. Gestion des permissions (si lancé avec ou sans sudo)
REAL_USER="${SUDO_USER:-$USER}"
REAL_GROUP=$(id -gn "$REAL_USER")
echo "[1/7] Correction des permissions pour $REAL_USER..."
if [ "$EUID" -eq 0 ]; then
    chown -R "$REAL_USER:$REAL_GROUP" .
else
    sudo chown -R "$REAL_USER:$REAL_GROUP" .
fi

# 2. Vérification de la présence de build.gradle
echo "[2/7] Analyse des fichiers du projet..."
if [ ! -f "build.gradle" ]; then
    echo "ERREUR : Aucun fichier build.gradle trouvé dans $(pwd)"
    exit 1
fi

echo "--- Aperçu des 15 premières lignes de build.gradle ---"
head -n 15 build.gradle
echo "------------------------------------------------------"

# 3. Réparation de settings.gradle (Obligatoire pour les plugins -SNAPSHOT de Fabric)
echo "[3/7] Vérification et correction de settings.gradle..."
if [ ! -f "settings.gradle" ]; then
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
    echo " -> settings.gradle créé avec le dépôt Maven de Fabric."
elif ! grep -q "maven.fabricmc.net" settings.gradle; then
    cp settings.gradle settings.gradle.bak
    cat << 'SETTINGS' > settings.gradle.tmp
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
    cat settings.gradle >> settings.gradle.tmp
    mv settings.gradle.tmp settings.gradle
    echo " -> Dépôt Maven Fabric injecté en haut de settings.gradle."
else
    echo " -> settings.gradle contient déjà le dépôt Fabric."
fi

# 4. Correction de l'ID du plugin Loom dans build.gradle si nécessaire
echo "[4/7] Vérification du bloc plugins dans build.gradle..."
if grep -q "net.fabricmc.fabric-loom" build.gradle; then
    echo " -> Plugin net.fabricmc.fabric-loom détecté (OK avec settings.gradle corrigé)."
fi

# 5. Installation du Gradle Wrapper 9.0 (Remplace le vieux Gradle 4.4.1 de Debian)
echo "[5/7] Installation du Gradle Wrapper 9.0..."
NEED_WRAPPER=1
if [ -f "./gradlew" ] && [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    if grep -q "gradle-9" gradle/wrapper/gradle-wrapper.properties || grep -q "gradle-8.1" gradle/wrapper/gradle-wrapper.properties; then
        echo " -> Un Gradle Wrapper récent est déjà présent."
        NEED_WRAPPER=0
    fi
fi

if [ "$NEED_WRAPPER" -eq 1 ]; then
    echo " -> Téléchargement de Gradle 9.0 pour générer le wrapper..."
    wget -q --show-progress https://services.gradle.org/distributions/gradle-9.0-bin.zip -O /tmp/gradle-9.0.zip
    unzip -q -o /tmp/gradle-9.0.zip -d /tmp/
    /tmp/gradle-9.0/bin/gradle wrapper --gradle-version 9.0 --no-daemon
    rm -rf /tmp/gradle-9.0.zip /tmp/gradle-9.0
    echo " -> Gradle Wrapper 9.0 généré avec succès."
fi
chmod +x gradlew

# 6. Patch de 2.sh pour utiliser ./gradlew au lieu du vieux gradle d'apt
echo "[6/7] Vérification de ton script 2.sh..."
if [ -f "2.sh" ]; then
    cp 2.sh 2.sh.bak
    # Remplace l'appel au binaire 'gradle' global par './gradlew'
    sed -i 's/\bgradle\b/\.\/gradlew/g' 2.sh
    # Évite les doublons '././gradlew' si déjà présent
    sed -i 's|\./\./gradlew|\./gradlew|g' 2.sh
    chmod +x 2.sh
    echo " -> 2.sh mis à jour pour utiliser ./gradlew (sauvegarde dans 2.sh.bak)."
fi

# 7. Création du Workflow GitHub Actions (Compilation Cloud + compilation directe sans wrapper externe)
echo "[7/7] Génération du workflow GitHub Actions (.github/workflows/build.yml)..."
mkdir -p .github/workflows
cat << 'WORKFLOW' > .github/workflows/build.yml
name: Build Lifesteal Fabric

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
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

      - name: Compilation du mod
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

# Remettre toutes les permissions au propre à la fin
if [ "$EUID" -eq 0 ]; then
    chown -R "$REAL_USER:$REAL_GROUP" .
else
    sudo chown -R "$REAL_USER:$REAL_GROUP" .
fi

echo ""
echo "===================================================="
echo " DIAGNOSTIC FINAL :"
echo "===================================================="
echo "- Java installé : $(java -version 2>&1 | head -n 1)"
echo "- Wrapper Gradle : $(./gradlew --version | grep 'Gradle ' || echo 'Erreur')"
echo "- Fichier settings.gradle : OK (Maven Fabric configuré)"
echo "- Workflow Cloud : .github/workflows/build.yml prêt"
echo ""
echo "Que veux-tu faire maintenant ?"
echo "1) Tester en local : ./gradlew build (ou bash 2.sh SANS sudo)"
echo "2) Compiler sur le Cloud GitHub :"
echo "   git add . && git commit -m 'Fix Gradle & Cloud Build' && git push"
echo "===================================================="
EOF

chmod +x fix-cloud-and-local.sh
./fix-cloud-and-local.sh

