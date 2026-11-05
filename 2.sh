#!/usr/bin/env bash
# fix_lifesteal.sh : adapte le code (noms Yarn) aux noms Mojang de Minecraft 26.3
# Usage : bash fix_lifesteal.sh   (depuis la racine du projet)
set -euo pipefail

[[ -f build../gradlew ]] || { echo "build../gradlew introuvable : lance le script depuis la racine du projet."; exit 1; }
command -v java  >/dev/null || { echo "java introuvable."; exit 1; }
command -v unzip >/dev/null || { echo "unzip introuvable : sudo apt install unzip"; exit 1; }
java -version 2>&1 | head -n1

if [[ -x ./gradlew ]]; then
  GRADLE=./gradlew
elif command -v ./gradlew >/dev/null; then
  GRADLE=./gradlew
else
  echo "Ni ./gradlew ni ./gradlew trouvés. Installe Gradle ou génère le wrapper."; exit 1
fi
echo "Gradle : $GRADLE"

# 1. Sauvegarde
STAMP="$(date +%Y%m%d-%H%M%S)"
BK=".lifesteal-backup/$STAMP"
mkdir -p "$BK"
cp -r src "$BK/src"
cp build../gradlew ./gradlew.properties "$BK/"
echo "Sauvegarde : $BK"

# 2. Supprimer la variable inutile (aucune ligne mappings n'est active)
sed -i '/^yarn_mappings=/d' ./gradlew.properties

# 3. Afficher toutes les erreurs javac (100 par défaut)
if ! grep -q 'Xmaxerrs' build../gradlew; then
  cat >> build../gradlew <<'EOF'

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs.add("-Xmaxerrs")
    options.compilerArgs.add("1000")
}
EOF
  echo "build../gradlew : option -Xmaxerrs ajoutée"
fi

# 4. Première compilation pour remplir le cache Loom (l'échec est attendu)
echo "Préparation du cache Gradle (peut être long)..."
$GRADLE compileJava --no-daemon --console=plain > /tmp/lifesteal-prep.log 2>&1 || true

# 5. Trouver le jar Minecraft
JAR="$(find "$HOME/../gradlew" ../gradlew build -iname '*minecraft*.jar' 2>/dev/null \
  | while read -r j; do
      if unzip -Z1 "$j" 2>/dev/null | grep -qx 'net/minecraft/server/MinecraftServer.class'; then
        echo "$j"; break
      fi
    done)" || true

if [[ -z "$JAR" ]]; then
  echo "Jar Minecraft introuvable. Vérifie /tmp/lifesteal-prep.log (dépendances téléchargées ?)."
  exit 1
fi
echo "Jar : $JAR"
unzip -Z1 "$JAR" > /tmp/lifesteal-jar.txt

has_class() { grep -qxF "$(echo "$1" | tr . /).class" /tmp/lifesteal-jar.txt; }

# 6. Table : ancien nom | cibles candidates (la première présente dans le jar est retenue)
#    L'ordre compte : Registries doit être traité avant RegistryKeys.
MAP='
net.minecraft.server.network.ServerPlayerEntity|net.minecraft.server.level.ServerPlayer
net.minecraft.server.world.ServerWorld|net.minecraft.server.level.ServerLevel
net.minecraft.world.World|net.minecraft.world.level.Level
net.minecraft.world.GameMode|net.minecraft.world.level.GameType
net.minecraft.world.RaycastContext|net.minecraft.world.level.ClipContext
net.minecraft.text.Text|net.minecraft.network.chat.Component
net.minecraft.text.MutableText|net.minecraft.network.chat.MutableComponent
net.minecraft.text.ClickEvent|net.minecraft.network.chat.ClickEvent
net.minecraft.text.HoverEvent|net.minecraft.network.chat.HoverEvent
net.minecraft.util.Formatting|net.minecraft.ChatFormatting
net.minecraft.util.TypedActionResult|net.minecraft.world.InteractionResultHolder
net.minecraft.util.Identifier|net.minecraft.resources.Identifier net.minecraft.resources.ResourceLocation
net.minecraft.util.Rarity|net.minecraft.world.item.Rarity
net.minecraft.util.math.Vec3d|net.minecraft.world.phys.Vec3
net.minecraft.util.hit.BlockHitResult|net.minecraft.world.phys.BlockHitResult
net.minecraft.util.hit.HitResult|net.minecraft.world.phys.HitResult
net.minecraft.entity.EntityType|net.minecraft.world.entity.EntityType
net.minecraft.entity.attribute.EntityAttributes|net.minecraft.world.entity.ai.attributes.Attributes
net.minecraft.entity.attribute.EntityAttributeInstance|net.minecraft.world.entity.ai.attributes.AttributeInstance
net.minecraft.entity.decoration.DisplayEntity|net.minecraft.world.entity.Display
net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket|net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
net.minecraft.item.Item|net.minecraft.world.item.Item
net.minecraft.item.ItemStack|net.minecraft.world.item.ItemStack
net.minecraft.command.argument.EntityArgumentType|net.minecraft.commands.arguments.EntityArgument
net.minecraft.server.command.ServerCommandSource|net.minecraft.commands.CommandSourceStack
net.minecraft.server.command.CommandManager|net.minecraft.commands.Commands
net.minecraft.registry.Registries|net.minecraft.core.registries.BuiltInRegistries
net.minecraft.registry.Registry|net.minecraft.core.Registry
net.minecraft.registry.RegistryKey|net.minecraft.resources.ResourceKey
net.minecraft.registry.RegistryKeys|net.minecraft.core.registries.Registries
net.minecraft.component.DataComponentTypes|net.minecraft.core.component.DataComponents
'

echo "Renommage des imports et des noms de classes..."
while IFS='|' read -r OLD CANDS; do
  if [[ -z "$OLD" || "$OLD" == \#* ]]; then continue; fi
  NEW=""
  for C in $CANDS; do
    if has_class "$C"; then NEW="$C"; break; fi
  done
  if [[ -z "$NEW" ]]; then
    echo "  [ignoré] $OLD : aucune cible trouvée dans le jar"
    continue
  fi
  OLD_S="${OLD##*.}"; NEW_S="${NEW##*.}"
  OLD_RE="${OLD//./\\.}"
  FILES="$(find src -name '*.java' -print0 | xargs -0 grep -l -F "import $OLD;" || true)"
  if [[ -z "$FILES" ]]; then continue; fi
  while IFS= read -r f; do
    sed -i "s/^import ${OLD_RE};/import ${NEW};/" "$f"
    if [[ "$OLD_S" != "$NEW_S" ]]; then
      sed -i -E "s/\b${OLD_S}\b/${NEW_S}/g" "$f"
    fi
  done <<< "$FILES"
  echo "  [ok] $OLD -> $NEW"
done <<< "$MAP"

# 7. Méthodes et constantes Yarn -> Mojang (suppositions, à vérifier via les erreurs)
echo "Renommage des méthodes et constantes..."
find src -name '*.java' -exec sed -i -E \
  -e 's/\.getPlayerManager\(\)\.getPlayers\(\)/.getPlayerList().getPlayers()/g' \
  -e 's/\.getEyePos\(\)/.getEyePosition()/g' \
  -e 's/\.getUuid\(\)/.getUUID()/g' \
  -e 's/\.networkHandler\.sendPacket\(/.connection.send(/g' \
  -e 's/\.getWorld\(\)\.raycast\(/.level().clip(/g' \
  -e 's/\.getWorld\(\)/.level()/g' \
  -e 's/\.setBillboardMode\(/.setBillboardConstraints(/g' \
  -e 's/\bDisplay\.TextDisplayEntity\b/Display.TextDisplay/g' \
  -e 's/\bDisplay\.BillboardMode\b/Display.BillboardConstraints/g' \
  -e 's/\bAttributes\.GENERIC_MAX_HEALTH\b/Attributes.MAX_HEALTH/g' \
  -e 's/\bItem\.Settings\b/Item.Properties/g' \
  -e 's/\bClipContext\.ShapeType\.VISUAL\b/ClipContext.Block.VISUAL/g' \
  -e 's/\bClipContext\.FluidHandling\.NONE\b/ClipContext.Fluid.NONE/g' \
  -e 's/\b(Identifier|ResourceLocation)\.of\(/\1.fromNamespaceAndPath(/g' \
  {} +

# 8. Compilation
echo "Compilation..."
set +e
$GRADLE compileJava --no-daemon --console=plain > build-local.log 2>&1
RC=$?
set -e

if [[ $RC -eq 0 ]]; then
  echo "✅ Compilation réussie"
  exit 0
fi

echo "❌ Échec. Erreurs les plus fréquentes :"
grep -E 'error:' build-local.log | sed -E 's/^.*error: //' | sort | uniq -c | sort -rn | head -n 30
echo
echo "Log complet      : build-local.log"
echo "Modifications    : diff -r $BK/src src | less"
