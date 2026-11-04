#!/usr/bin/env bash
set -e

echo "===================================================="
echo " 🚀 Patch : Java 25 + Gradle 8.14.2 + Dernier Loom"
echo "===================================================="

WORKFLOW_FILE=".github/workflows/build.yml"
BUILD_GRADLE="build.gradle"

# 1. Détecter la dernière version de fabric-loom sur le Maven officiel de Fabric
echo "-> Recherche de la dernière version de fabric-loom..."
LATEST_LOOM=$(curl -s https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml | grep -oP '(?<=<latest>).*?(?=</latest>)' || echo "1.10-SNAPSHOT")
if [ -z "$LATEST_LOOM" ]; then
    LATEST_LOOM="1.10-SNAPSHOT"
fi
echo "   Version de Fabric Loom détectée : $LATEST_LOOM"

# 2. Réécriture propre de build.gradle
echo "-> Mise à jour de $BUILD_GRADLE avec fabric-loom $LATEST_LOOM..."
cat << GRADLE_EOF > "$BUILD_GRADLE"
plugins {
    id 'fabric-loom' version '${LATEST_LOOM}'
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    mavenCentral()
    maven {
        name = "OpenCollab Repository"
        url = "https://repo.opencollab.dev/main/"
    }
}

dependencies {
    minecraft "com.mojang:minecraft:\${project.minecraft_version}"
    mappings "net.fabricmc:yarn:\${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:\${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:\${project.fabric_version}"

    compileOnly "org.geysermc.floodgate:api:2.2.3-SNAPSHOT"
    compileOnly "org.geysermc.cumulus:cumulus:1.1.2"
}

processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") {
        expand "version": project.version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.encoding = "UTF-8"
    it.options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
GRADLE_EOF

# 3. Mise à jour du wrapper Gradle si présent
if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    echo "-> Mise à jour de gradle-wrapper.properties vers Gradle 8.14.2..."
    sed -i -E 's|gradle-[0-9.]+-bin\.zip|gradle-8.14.2-bin.zip|g' gradle/wrapper/gradle-wrapper.properties
    sed -i -E 's|gradle-[0-9.]+-all\.zip|gradle-8.14.2-bin.zip|g' gradle/wrapper/gradle-wrapper.properties
fi

# 4. Patch propre de .github/workflows/build.yml (Java 25 pur + Gradle 8.14.2)
echo "-> Mise à jour de $WORKFLOW_FILE (Java 25 + Gradle 8.14.2)..."
python3 - << 'PYEOF'
import re

with open(".github/workflows/build.yml", "r") as f:
    c = f.read()

# Remettre uniquement Java 25
c = re.sub(
    r'java-version:\s*\|\s*\n\s*25\s*\n\s*21',
    "java-version: '25'",
    c
)

# Remplacer Gradle 8.12 par 8.14.2 (premier Gradle supportant Java 25 class version 69)
c = re.sub(
    r'gradle-version:\s*[\'"]?8\.12[\'"]?',
    "gradle-version: '8.14.2'",
    c
)

with open(".github/workflows/build.yml", "w") as f:
    f.write(c)
PYEOF

# 5. Commit & Push
echo "-> Commit et Push sur GitHub..."
git add -A
git commit -m "Fix: Upgrade to Gradle 8.14.2 + Fabric Loom $LATEST_LOOM for Java 25" || true
git push origin main

# 6. Lancement et suivi du workflow
echo "-> Lancement du Cloud Build sur GitHub..."
gh workflow run build.yml --ref main
sleep 4

RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId --jq '.[0].databaseId')
echo "-> Suivi du run ID: $RUN_ID..."
gh run watch "$RUN_ID" --exit-status

# 7. Téléchargement du JAR
echo "-> Téléchargement du JAR compilé..."
mkdir -p ./build_output
gh run download "$RUN_ID" -D ./build_output
echo "===================================================="
echo " ✅ Terminé ! Ton JAR est dans ./build_output"
echo "===================================================="
