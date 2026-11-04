#!/usr/bin/env bash
set -e

echo "===================================================="
echo " 🔥 Patch Minecraft 26.3 Unobfuscated (Sans Mappings)"
echo "===================================================="

BUILD_GRADLE="build.gradle"

# 1. Récupérer la dernière version de fabric-loom
LATEST_LOOM=$(curl -s https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml | grep -oP '(?<=<latest>).*?(?=</latest>)' || echo "1.19-SNAPSHOT")
[ -z "$LATEST_LOOM" ] && LATEST_LOOM="1.19-SNAPSHOT"

# 2. Réécriture de build.gradle avec le plugin non-obfusqué et SANS ligne 'mappings'
echo "-> Configuration de $BUILD_GRADLE en mode non-obfusqué (net.fabricmc.fabric-loom)..."
cat << GRADLE_EOF > "$BUILD_GRADLE"
plugins {
    id 'net.fabricmc.fabric-loom' version '${LATEST_LOOM}'
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
    implementation "net.fabricmc:fabric-loader:\${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:\${project.fabric_version}"

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

# 3. Commit & Push
echo "-> Commit et Push sur GitHub..."
git add -A
git commit -m "Fix: Use unobfuscated net.fabricmc.fabric-loom without mappings for MC 26.3" || true
git push origin main

# 4. Attente et suivi du workflow déclenché par le push
echo "-> Attente du lancement du workflow..."
sleep 5
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId --jq '.[0].databaseId')
echo "-> Suivi du run ID: $RUN_ID..."
if ! gh run watch "$RUN_ID" --exit-status; then
    echo "===================================================="
    echo " ❌ Le build a échoué ! Voici l'erreur exacte :"
    echo "===================================================="
    JOB_ID=$(gh run view "$RUN_ID" --json jobs --jq '.jobs[0].databaseId')
    gh run view --job="$JOB_ID" --log-failed | grep -E "(error:|FAILURE|What went wrong|Could not|> )" -A 8 -B 2 || gh run view --job="$JOB_ID" --log-failed | tail -n 60
    exit 1
fi

# 5. Téléchargement du JAR
echo "-> Téléchargement du JAR compilé..."
mkdir -p ./build_output
gh run download "$RUN_ID" -D ./build_output
echo "===================================================="
echo " ✅ Terminé ! Ton JAR est dans ./build_output"
echo "===================================================="
