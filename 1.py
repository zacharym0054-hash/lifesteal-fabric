#!/usr/bin/env python3
"""
fix_lifesteal.py
Convertit les noms Yarn de src/ vers les noms officiels Mojang,
puis relance la compilation. Sauvegarde automatique dans .lifesteal-backup/.

Usage (à la racine du projet) :  python3 fix_lifesteal.py
"""
import datetime
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path.cwd()
SRC = ROOT / "src"
GRADLE_FILES = ["build.gradle", "build.gradle.kts", "gradle.properties",
                "settings.gradle", "settings.gradle.kts"]

# Ancien nom Yarn -> noms Mojang candidats (le premier présent dans le jar est retenu)
CLASS_MAP = {
    "net.minecraft.server.network.ServerPlayerEntity": ["net.minecraft.server.level.ServerPlayer"],
    "net.minecraft.server.world.ServerWorld": ["net.minecraft.server.level.ServerLevel"],
    "net.minecraft.world.World": ["net.minecraft.world.level.Level"],
    "net.minecraft.world.GameMode": ["net.minecraft.world.level.GameType"],
    "net.minecraft.world.RaycastContext": ["net.minecraft.world.level.ClipContext"],
    "net.minecraft.server.command.ServerCommandSource": ["net.minecraft.commands.CommandSourceStack"],
    "net.minecraft.server.command.CommandManager": ["net.minecraft.commands.Commands"],
    "net.minecraft.command.argument.EntityArgumentType": ["net.minecraft.commands.arguments.EntityArgument"],
    "net.minecraft.entity.EntityType": ["net.minecraft.world.entity.EntityType"],
    "net.minecraft.entity.attribute.EntityAttributes": ["net.minecraft.world.entity.ai.attributes.Attributes"],
    "net.minecraft.entity.attribute.EntityAttributeInstance": ["net.minecraft.world.entity.ai.attributes.AttributeInstance"],
    "net.minecraft.entity.decoration.DisplayEntity": ["net.minecraft.world.entity.Display"],
    "net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket":
        ["net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket"],
    "net.minecraft.text.Text": ["net.minecraft.network.chat.Component"],
    "net.minecraft.text.MutableText": ["net.minecraft.network.chat.MutableComponent"],
    "net.minecraft.text.ClickEvent": ["net.minecraft.network.chat.ClickEvent"],
    "net.minecraft.text.HoverEvent": ["net.minecraft.network.chat.HoverEvent"],
    "net.minecraft.util.Formatting": ["net.minecraft.ChatFormatting"],
    "net.minecraft.util.TypedActionResult": ["net.minecraft.world.InteractionResultHolder"],
    "net.minecraft.util.Identifier": ["net.minecraft.resources.Identifier",
                                      "net.minecraft.resources.ResourceLocation"],
    "net.minecraft.util.Rarity": ["net.minecraft.world.item.Rarity"],
    "net.minecraft.util.hit.BlockHitResult": ["net.minecraft.world.phys.BlockHitResult"],
    "net.minecraft.util.hit.HitResult": ["net.minecraft.world.phys.HitResult"],
    "net.minecraft.util.math.Vec3d": ["net.minecraft.world.phys.Vec3"],
    "net.minecraft.item.Item": ["net.minecraft.world.item.Item"],
    "net.minecraft.item.ItemStack": ["net.minecraft.world.item.ItemStack"],
    "net.minecraft.registry.Registries": ["net.minecraft.core.registries.BuiltInRegistries"],
    "net.minecraft.registry.Registry": ["net.minecraft.core.Registry"],
    "net.minecraft.registry.RegistryKey": ["net.minecraft.resources.ResourceKey"],
    "net.minecraft.registry.RegistryKeys": ["net.minecraft.core.registries.Registries"],
    "net.minecraft.component.DataComponentTypes": ["net.minecraft.core.component.DataComponents"],
}

# Références qualifiées à remplacer avant les noms simples
QUALIFIED = {
    "DisplayEntity.TextDisplayEntity": "Display.TextDisplay",
    "DisplayEntity.BillboardMode": "Display.BillboardConstraints",
    "EntityAttributes.GENERIC_MAX_HEALTH": "Attributes.MAX_HEALTH",
    "Item.Settings": "Item.Properties",
    "RaycastContext.ShapeType": "ClipContext.Block",
    "RaycastContext.FluidHandling": "ClipContext.Fluid",
}

# Appels de méthodes Yarn -> Mojang (regex)
METHODS = [
    (r"(\w+)\.getWorld\(\)\.raycast\(", r"\1.level().clip("),
    (r"\.getPlayerManager\(\)\.getPlayers\(\)", ".getPlayerList().getPlayers()"),
    (r"\.getEyePos\(\)", ".getEyePosition()"),
    (r"\.getUuid\(\)", ".getUUID()"),
    (r"\.networkHandler\.sendPacket\(", ".connection.send("),
    (r"\.getWorld\(\)", ".level()"),
    (r"\.setBillboardMode\(", ".setBillboardConstraints("),
]

IMPORT_RE = re.compile(r"^import\s+(net\.minecraft\.[\w.]+)\s*;")


def jar_path(fqn):
    return fqn.replace(".", "/") + ".class"


def find_minecraft_jar():
    """Cherche un jar Minecraft dans les caches Gradle/Loom."""
    for d in [Path.home() / ".gradle", ROOT / ".gradle", ROOT / "build"]:
        if not d.exists():
            continue
        for jar in d.rglob("*minecraft*.jar"):
            try:
                with zipfile.ZipFile(jar) as z:
                    if "net/minecraft/server/MinecraftServer.class" in z.namelist():
                        return jar
            except zipfile.BadZipFile:
                continue
    return None


def pick(candidates, names):
    if names is None:
        return candidates[0]
    for c in candidates:
        if jar_path(c) in names:
            return c
    return None


def build_maps(names):
    cls = {}
    for old, candidates in CLASS_MAP.items():
        new = pick(candidates, names)
        if new is None:
            print(f"  [!] aucune cible trouvée dans le jar pour {old}")
        elif new != old:
            cls[old] = new
    return cls


def transform(text, cls):
    lines = text.split("\n")
    simple = {}
    # 1. Imports : remplacer les FQN et mémoriser les noms simples
    for i, line in enumerate(lines):
        m = IMPORT_RE.match(line)
        if m and m.group(1) in cls:
            old, new = m.group(1), cls[m.group(1)]
            simple[old.rsplit(".", 1)[1]] = new.rsplit(".", 1)[1]
            lines[i] = f"import {new};"
    # 2. Corps du code
    simple_re = re.compile(r"\b(" + "|".join(map(re.escape, simple)) + r")\b") if simple else None
    for i, line in enumerate(lines):
        if line.startswith("import "):
            continue
        for q, r in QUALIFIED.items():
            line = line.replace(q, r)
        for pat, rep in METHODS:
            line = re.sub(pat, rep, line)
        if simple_re:
            line = simple_re.sub(lambda m: simple[m.group(1)], line)
        lines[i] = line
    return "\n".join(lines)


def main():
    if not (ROOT / "build.gradle").exists() and not (ROOT / "build.gradle.kts").exists():
        sys.exit("Lance ce script à la racine du projet (là où se trouve build.gradle).")

    gradle_files = [ROOT / f for f in GRADLE_FILES if (ROOT / f).exists()]
    gtext = "\n".join(p.read_text(errors="ignore") for p in gradle_files)
    if re.search(r"yarn", gtext, re.I):
        sys.exit("Yarn est configuré dans Gradle : les noms Yarn devraient fonctionner. "
                 "Le problème vient alors de la version (Minecraft/Yarn). Rien n'a été modifié.")

    jar = find_minecraft_jar()
    names = None
    if jar:
        with zipfile.ZipFile(jar) as z:
            names = set(z.namelist())
        if "net/minecraft/entity/EntityType.class" in names:
            sys.exit(f"Le jar {jar} utilise les noms Yarn : le code devrait compiler. Rien n'a été modifié.")
        print(f"Jar Minecraft trouvé : {jar}")
    else:
        print("[!] Aucun jar Minecraft local : les mappings seront appliqués sans vérification.")

    # Sauvegarde
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = ROOT / ".lifesteal-backup" / stamp
    shutil.copytree(SRC, backup / "src")
    for f in gradle_files:
        shutil.copy(f, backup / f.name)
    print(f"Sauvegarde : {backup}")

    # Conversion
    cls = build_maps(names)
    print(f"{len(cls)} correspondance(s) de classes appliquée(s)")
    changed = 0
    for java in SRC.rglob("*.java"):
        old = java.read_text(encoding="utf-8")
        new = transform(old, cls)
        if new != old:
            java.write_text(new, encoding="utf-8")
            changed += 1
    print(f"{changed} fichier(s) modifié(s)")

    # Compilation
    print("Compilation...")
    cmd = ["sh", "gradlew", "compileJava", "--no-daemon", "--console=plain"] \
        if (ROOT / "gradlew").exists() else ["gradle", "compileJava", "--no-daemon", "--console=plain"]
    r = subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT)
    log = r.stdout + r.stderr
    (ROOT / "build-local.log").write_text(log, encoding="utf-8")

    if r.returncode == 0:
        print("✅ Compilation réussie")
        return
    errs = list(dict.fromkeys(l.strip() for l in log.splitlines() if re.search(r"\berror:", l)))
    print(f"❌ {len(errs)} erreur(s) restante(s) (log complet : build-local.log)")
    for e in errs[:25]:
        print("  ", e)


if __name__ == "__main__":
    main()
