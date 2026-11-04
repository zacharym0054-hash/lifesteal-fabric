#!/usr/bin/env bash
set -e

echo "===================================================="
echo " 🔧 Patch : Gradle 8.12 (Java 21) + Toolchain Java 25"
echo "===================================================="

WORKFLOW_FILE=".github/workflows/build.yml"
BUILD_GRADLE="build.gradle"

# 1. Patch du workflow GitHub Actions (.github/workflows/build.yml)
if [ -f "$WORKFLOW_FILE" ]; then
    echo "-> Mise à jour de $WORKFLOW_FILE..."
    sed -i -E 's|uses:[[:space:]]*actions/setup-java@v[0-9]+|uses: actions/setup-java@v5|g' "$WORKFLOW_FILE"
    sed -i -E "s|java-version:[[:space:]]*['\"]?25['\"]?|java-version: \|\n            25\n            21|g" "$WORKFLOW_FILE"
else
    echo "Erreur : $WORKFLOW_FILE introuvable !"
    exit 1
fi

# 2. Patch de build.gradle pour forcer le Toolchain Java 25 lors de la compilation
if [ -f "$BUILD_GRADLE" ]; then
    echo "-> Vérification du Toolchain Java 25 dans $BUILD_GRADLE..."
    if ! grep -q "JavaLanguageVersion.of(25)" "$BUILD_GRADLE"; then
        cat << 'GRADLE_EOF' >> "$BUILD_GRADLE"

// --- Auto-patch Toolchain Java 25 pour MC 26.3 ---
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 25
}
GRADLE_EOF
        echo "   Toolchain Java 25 ajouté à $BUILD_GRADLE."
    else
        echo "   Toolchain Java 25 déjà présent dans $BUILD_GRADLE."
    fi
fi

# 3. Commit & Push
echo "-> Commit et Push sur GitHub..."
git add "$WORKFLOW_FILE" "$BUILD_GRADLE" fix_java25_toolchain.sh
git commit -m "Fix: Run Gradle 8.12 on Java 21 with Java 25 toolchain for MC 26.3" || true
git push origin main

# 4. Déclenchement et suivi du workflow GitHub Actions
echo "-> Lancement du Cloud Build sur GitHub..."
gh workflow run build.yml --ref main
sleep 4

RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId --jq '.[0].databaseId')
echo "-> Suivi du run ID: $RUN_ID..."
gh run watch "$RUN_ID" --exit-status

# 5. Téléchargement du JAR compilé
echo "-> Téléchargement du JAR compilé..."
mkdir -p ./build_output
gh run download "$RUN_ID" -D ./build_output
echo "===================================================="
echo " ✅ Terminé ! Ton JAR est dans ./build_output"
echo "===================================================="
