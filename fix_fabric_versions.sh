#!/usr/bin/env bash
set -e

echo "===================================================="
echo " 🔍 Récupération des vraies versions Fabric Meta"
echo "===================================================="

python3 - << 'PYEOF'
import urllib.request
import json
import xml.etree.ElementTree as ET
import re

def fetch_json(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

def fetch_text(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as resp:
        return resp.read().decode()

# 1. Vérifier les versions de Minecraft supportées par Fabric Meta
games = fetch_json("https://meta.fabricmc.net/v2/versions/game")
mc_versions = [g["version"] for g in games]

target_mc = "26.3"
if target_mc not in mc_versions:
    # Chercher une version 26.3* ou prendre la dernière version stable
    matches = [v for v in mc_versions if v.startswith("26.3") or v.startswith("26.")]
    stable_matches = [g["version"] for g in games if g.get("stable")]
    target_mc = matches[0] if matches else (stable_matches[0] if stable_matches else mc_versions[0])
    print(f"⚠️ MC 26.3 non trouvé tel quel, utilisation de : {target_mc}")
else:
    print(f"✅ Version Minecraft trouvée : {target_mc}")

# 2. Dernier Fabric Loader
loaders = fetch_json("https://meta.fabricmc.net/v2/versions/loader")
loader_version = loaders[0]["version"]
print(f"✅ Fabric Loader : {loader_version}")

# 3. Vérifier si Yarn existe pour cette version de MC
yarn_data = fetch_json(f"https://meta.fabricmc.net/v2/versions/yarn/{target_mc}")
yarn_version = yarn_data[0]["version"] if yarn_data else None
if yarn_version:
    print(f"✅ Yarn Mappings : {yarn_version}")
else:
    print("⚠️ Aucun Yarn trouvé pour cette version -> bascule sur loom.officialMojangMappings() !")

# 4. Trouver la vraie version de fabric-api dans le Maven Fabric
xml_data = fetch_text("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml")
root = ET.fromstring(xml_data)
all_fapi = [v.text for v in root.findall(".//version")]

fapi_matches = [v for v in all_fapi if v.endswith(f"+{target_mc}")]
if not fapi_matches:
    # Essayer avec le préfixe majeur (ex: +26.)
    prefix = target_mc.split('.')[0]
    fapi_matches = [v for v in all_fapi if f"+{prefix}." in v]
fabric_api_version = fapi_matches[-1] if fapi_matches else all_fapi[-1]
print(f"✅ Fabric API : {fabric_api_version}")

# 5. Mettre à jour gradle.properties
with open("gradle.properties", "r") as f:
    props = f.read()

props = re.sub(r'minecraft_version=.*', f'minecraft_version={target_mc}', props)
props = re.sub(r'loader_version=.*', f'loader_version={loader_version}', props)
props = re.sub(r'fabric_version=.*', f'fabric_version={fabric_api_version}', props)
if yarn_version:
    props = re.sub(r'yarn_mappings=.*', f'yarn_mappings={yarn_version}', props)

with open("gradle.properties", "w") as f:
    f.write(props)

# 6. Si Yarn n'existe pas, adapter build.gradle pour utiliser officialMojangMappings()
with open("build.gradle", "r") as f:
    bg = f.read()

if not yarn_version:
    bg = re.sub(r'mappings\s+"net\.fabricmc:yarn:.*?"', 'mappings loom.officialMojangMappings()', bg)
else:
    bg = re.sub(r'mappings\s+loom\.officialMojangMappings\(\)', 'mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"', bg)

with open("build.gradle", "w") as f:
    f.write(bg)
PYEOF

echo "-> Nouvelles valeurs dans gradle.properties :"
cat gradle.properties

echo "-> Commit et Push sur GitHub..."
git add -A
git commit -m "Fix: Resolve exact Fabric Meta versions for MC 26.3" || true
git push origin main

echo "-> Attente du lancement du workflow..."
sleep 5
RUN_ID=$(gh run list --workflow=build.yml --limit 1 --json databaseId --jq '.[0].databaseId')
echo "-> Suivi du run ID: $RUN_ID..."
if ! gh run watch "$RUN_ID" --exit-status; then
    echo "===================================================="
    echo " ❌ Le build a échoué ! Voici l'erreur exacte :"
    echo "===================================================="
    JOB_ID=$(gh run view "$RUN_ID" --json jobs --jq '.jobs[0].databaseId')
    gh run view --job="$JOB_ID" --log-failed | grep -E "(error:|FAILURE|What went wrong|Could not|> )" -A 5 -B 2 || gh run view --job="$JOB_ID" --log-failed | tail -n 50
    exit 1
fi

echo "-> Téléchargement du JAR compilé..."
mkdir -p ./build_output
gh run download "$RUN_ID" -D ./build_output
echo "===================================================="
echo " ✅ Terminé ! Ton JAR est dans ./build_output"
echo "===================================================="
