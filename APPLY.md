# Apply AniCiel MediaHub NEW

This is a **new** tree. Do not mix with Netplay patches.

```bash
cd /storage/emulated/0/Download
rm -rf AniCiel-MediaHub-NEW
unzip -o AniCiel-MediaHub-NEW.zip -d AniCiel-MediaHub-NEW
ls AniCiel-MediaHub-NEW/
# SOURCES.md README.md APPLY.md app engine sources docs
```

Copy into a clean project (recommended: new branch or new repo, not Netplay-main):

```bash
# example if you still use folder AniCiel-main as shell only:
cd AniCiel-main
cp -f ../AniCiel-MediaHub-NEW/app/src/main/java/com/aniciel/app/ui/theme/Theme.kt app/src/main/java/com/aniciel/app/ui/theme/
cp -f ../AniCiel-MediaHub-NEW/app/src/main/java/com/aniciel/app/*.kt app/src/main/java/com/aniciel/app/
cp -f ../AniCiel-MediaHub-NEW/app/src/main/res/values/colors.xml app/src/main/res/values/
mkdir -p engine/src/providers/moviebox
cp -f ../AniCiel-MediaHub-NEW/engine/src/providers/moviebox/paths.rs engine/src/providers/moviebox/
mkdir -p docs/mediahub sources
cp -f ../AniCiel-MediaHub-NEW/SOURCES.md .
cp -f ../AniCiel-MediaHub-NEW/sources/* sources/
```
