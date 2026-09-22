#!/usr/bin/env bash
set -euo pipefail

echo "=== V25 MODDED reconstruct ==="
(
set -euo pipefail
cat \
  runtime-test/chunks/part00 \
  runtime-test/chunks/part01 \
  runtime-test/chunks/part02 \
  runtime-test/chunks/part03 \
  runtime-test/chunks/part04 \
  runtime-test/chunks/part05 \
  runtime-test/chunks/t6_00 \
  runtime-test/chunks/t6_01 \
  runtime-test/chunks/t6_02 \
  runtime-test/chunks/t6_03 \
  runtime-test/chunks/t6_04 \
  runtime-test/chunks/t7_00 \
  runtime-test/chunks/t7_01 \
  runtime-test/chunks/t7_02 \
  runtime-test/chunks/t7_03 \
  runtime-test/chunks/t7_04 \
  runtime-test/chunks/t8_00 \
  runtime-test/chunks/t8_01 \
  runtime-test/chunks/t8_02 \
  runtime-test/chunks/t8_03 \
  runtime-test/chunks/t8_04 \
  runtime-test/chunks/t9_00 > /tmp/hit-v3.b64
base64 -d /tmp/hit-v3.b64 > /tmp/hit_indicator-1.21.1-neoforge-v3.jar
echo "a38aafa9387b0378072dc81dbccc8e36d69c8303ba2a2294800b19f3d432889f  /tmp/hit_indicator-1.21.1-neoforge-v3.jar" | sha256sum -c -
unzip -t /tmp/hit_indicator-1.21.1-neoforge-v3.jar
python runtime-test/patch_v4.py /tmp/hit_indicator-1.21.1-neoforge-v3.jar /tmp/hit_indicator-1.21.1-neoforge-v4.jar
unzip -t /tmp/hit_indicator-1.21.1-neoforge-v4.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v4.jar -verbose com.misanthropy.hit_indicator.client.WindupTracker > /tmp/windup-v4.javap
grep -q 'NOTE_BLOCK_HAT:Lnet/minecraft/core/Holder[$]Reference;' /tmp/windup-v4.javap
grep -q 'NOTE_BLOCK_BASS:Lnet/minecraft/core/Holder[$]Reference;' /tmp/windup-v4.javap
grep -q 'NOTE_BLOCK_CHIME:Lnet/minecraft/core/Holder[$]Reference;' /tmp/windup-v4.javap
grep -q 'NOTE_BLOCK_BASEDRUM:Lnet/minecraft/core/Holder[$]Reference;' /tmp/windup-v4.javap
grep -q 'NOTE_BLOCK_BIT:Lnet/minecraft/core/Holder[$]Reference;' /tmp/windup-v4.javap
grep -q 'NOTE_BLOCK_BELL:Lnet/minecraft/core/Holder[$]Reference;' /tmp/windup-v4.javap
grep -q 'CROSSBOW_LOADING_START:Lnet/minecraft/core/Holder;' /tmp/windup-v4.javap
python runtime-test/patch_v5.py /tmp/hit_indicator-1.21.1-neoforge-v4.jar /tmp/hit_indicator-1.21.1-neoforge-v5.jar
unzip -t /tmp/hit_indicator-1.21.1-neoforge-v5.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v5.jar -verbose com.misanthropy.hit_indicator.server.AttackInterceptor > /tmp/attack-v5.javap
grep -q 'ServerPlayer.connection:Lnet/minecraft/server/network/ServerGamePacketListenerImpl;' /tmp/attack-v5.javap
! grep -q 'ServerPlayer.connection:Lnet/minecraft/server/level/ServerGamePacketListenerImpl;' /tmp/attack-v5.javap
)

echo "=== V25 MODDED prepare/build ==="
(
set -euo pipefail
git clone --depth 1 https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle.git mdk
cd mdk
sed -i 's/^neo_version=.*/neo_version=21.1.250/' gradle.properties
sed -i 's/^mod_id=.*/mod_id=hitindicatortest/' gradle.properties
sed -i 's/^mod_name=.*/mod_name=Hit Indicator Runtime Test/' gradle.properties
sed -i 's/^mod_version=.*/mod_version=1.0.0/' gradle.properties
sed -i 's/^mod_group_id=.*/mod_group_id=com.example.examplemod/' gradle.properties

rm -rf src/main/java/com/example/examplemod
mkdir -p src/main/java/com/example/examplemod libs run/mods
cp ../runtime-test/RuntimeTestMod.java src/main/java/com/example/examplemod/RuntimeTestMod.java
cp ../runtime-test/RuntimeTestClient.java src/main/java/com/example/examplemod/RuntimeTestClient.java
cp /tmp/hit_indicator-1.21.1-neoforge-v5.jar libs/
cp /tmp/hit_indicator-1.21.1-neoforge-v5.jar run/mods/

sed -i '/dependencies {/a\    compileOnly files("libs/hit_indicator-1.21.1-neoforge-v5.jar")' build.gradle
cat >> build.gradle <<'GRADLE'

repositories {
    maven { url = uri("https://api.modrinth.com/maven") }
    maven { url = uri("https://www.cursemaven.com") }
}

dependencies {
    implementation "maven.modrinth:5sy6g3kz:VhIOvcXP"
    implementation "maven.modrinth:gedNE4y2:HJZB6bmA"
    implementation "maven.modrinth:9s6osm5g:DqgODWcH"
    implementation "curse.maven:better-mob-combat-reimagined-1606434:8867297"
}
GRADLE

sed -i "/^[[:space:]]*client()$/a\\            programArguments.addAll '--quickPlaySingleplayer', 'RuntimeTest', '--width', '854', '--height', '480'" build.gradle

mkdir -p src/main/java/com/misanthropy/hit_indicator/server
cp ../runtime-test/ParticleCompat.java src/main/java/com/misanthropy/hit_indicator/server/ParticleCompat.java
cp ../runtime-test/LearnedAttackPredictor.java src/main/java/com/misanthropy/hit_indicator/server/LearnedAttackPredictor.java
mkdir -p src/main/java/com/misanthropy/hit_indicator/mixin/client
cp ../runtime-test/LivingEntityFreezeMixin.java src/main/java/com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.java
cp ../runtime-test/MobFreezeMixin.java src/main/java/com/misanthropy/hit_indicator/mixin/client/MobFreezeMixin.java
mkdir -p src/main/java/com/misanthropy/hit_indicator/client
cp ../runtime-test/AnimationFreezeContext.java src/main/java/com/misanthropy/hit_indicator/client/AnimationFreezeContext.java
cp ../runtime-test/LivingEntityRenderContextMixin.java src/main/java/com/misanthropy/hit_indicator/mixin/client/LivingEntityRenderContextMixin.java
cp ../runtime-test/PlayerAnimatorInterpolationFreezeMixin.java src/main/java/com/misanthropy/hit_indicator/mixin/client/PlayerAnimatorInterpolationFreezeMixin.java
cp ../runtime-test/FrozenEntityMaintenance.java src/main/java/com/misanthropy/hit_indicator/client/FrozenEntityMaintenance.java
cp ../runtime-test/FrozenRenderClock.java src/main/java/com/misanthropy/hit_indicator/client/FrozenRenderClock.java
cp ../runtime-test/ClientLevelTimeStopMixin.java src/main/java/com/misanthropy/hit_indicator/mixin/client/ClientLevelTimeStopMixin.java
cp ../runtime-test/LevelRendererTimeStopMixin.java src/main/java/com/misanthropy/hit_indicator/mixin/client/LevelRendererTimeStopMixin.java
cp ../runtime-test/StasisDesaturationRenderer.java src/main/java/com/misanthropy/hit_indicator/client/StasisDesaturationRenderer.java
cp ../runtime-test/StasisDesaturationClientEvents.java src/main/java/com/misanthropy/hit_indicator/client/StasisDesaturationClientEvents.java
cp ../runtime-test/EmfPoseFreeze.java src/main/java/com/misanthropy/hit_indicator/client/EmfPoseFreeze.java
cp ../runtime-test/EmfModelAnimationFreezeMixin.java src/main/java/com/misanthropy/hit_indicator/mixin/client/EmfModelAnimationFreezeMixin.java
mkdir -p src/main/resources/assets/minecraft/optifine/cem
cp ../runtime-test/emf_probe_zombie.jem src/main/resources/assets/minecraft/optifine/cem/zombie.jem
mkdir -p src/main/java/dev/kosmx/playerAnim/core/impl
cp ../runtime-test/AnimationProcessor.java src/main/java/dev/kosmx/playerAnim/core/impl/AnimationProcessor.java
mkdir -p src/main/java/com/example/examplemod/mixin
cp ../runtime-test/MobAnimationProbe.java src/main/java/com/example/examplemod/MobAnimationProbe.java
cp ../runtime-test/MobAnimationProbeMixin.java src/main/java/com/example/examplemod/mixin/MobAnimationProbeMixin.java
cp ../runtime-test/hitindicatortest.mixins.json src/main/resources/hitindicatortest.mixins.json
printf '\n[[mixins]]\nconfig = "hitindicatortest.mixins.json"\n' >> src/main/templates/META-INF/neoforge.mods.toml

chmod +x gradlew
./gradlew --no-daemon compileJava

echo "=== exact mod JAR verification ==="
BC_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/5sy6g3kz/VhIOvcXP" -type f -name '*.jar' | head -1)"
PAL_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/gedNE4y2/HJZB6bmA" -type f -name '*.jar' | head -1)"
BMC_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/curse.maven/better-mob-combat-reimagined-1606434/8867297" -type f -name '*.jar' | head -1)"
test -n "$BC_JAR" && test -f "$BC_JAR"
test -n "$PAL_JAR" && test -f "$PAL_JAR"
test -n "$BMC_JAR" && test -f "$BMC_JAR"
echo "0446e9faa98b764ba010ac31e337d14ac96bbb2fbfeff9b599e1efd510dfde5a  $BC_JAR" | sha256sum -c -
echo "dbe5de45f5cd60c0e5e47af14e6d564534a98456e973cf670cb881f6938eee92  $PAL_JAR" | sha256sum -c -
echo "74c15f6b61ddbf2f08a42e8d340f70ff9b802d4fd17e067dadb5baa774f3f187  $BMC_JAR" | sha256sum -c -
echo "EXACT_MODDED_STACK_SHA256_PASS"

test -f build/classes/java/main/com/misanthropy/hit_indicator/server/ParticleCompat.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/MobFreezeMixin.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/client/AnimationFreezeContext.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LivingEntityRenderContextMixin.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/PlayerAnimatorInterpolationFreezeMixin.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/client/FrozenEntityMaintenance.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/client/FrozenRenderClock.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/ClientLevelTimeStopMixin.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LevelRendererTimeStopMixin.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/client/StasisDesaturationRenderer.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/client/StasisDesaturationClientEvents.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/client/EmfPoseFreeze.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/EmfModelAnimationFreezeMixin.class
test -f build/classes/java/main/dev/kosmx/playerAnim/core/impl/AnimationProcessor.class
test -f build/classes/java/main/com/example/examplemod/mixin/MobAnimationProbeMixin.class
python ../runtime-test/patch_v6.py \
  /tmp/hit_indicator-1.21.1-neoforge-v5.jar \
  build/classes/java/main/com/misanthropy/hit_indicator/server/ParticleCompat.class \
  /tmp/hit_indicator-1.21.1-neoforge-v6.jar
unzip -t /tmp/hit_indicator-1.21.1-neoforge-v6.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v6.jar -c -p \
  com.misanthropy.hit_indicator.server.AttackInterceptor > /tmp/attack-v6.javap
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v6.jar -c -p \
  com.misanthropy.hit_indicator.server.ParticleCompat > /tmp/particle-compat-v6.javap
grep -q 'ParticleCompat.groundParticleSpec' /tmp/attack-v6.javap
grep -q 'NbtUtils.writeBlockState' /tmp/particle-compat-v6.javap

# ParticleCompat belongs only inside the patched Hit Indicator jar.
# Remove the temporary compiler input/output from the runtime-test module
# so JPMS does not see a split package across two modules.
rm -f src/main/java/com/misanthropy/hit_indicator/server/ParticleCompat.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/server/ParticleCompat.class

# Build v10 from the proven v6 baseline: animated timing ring, gold untinted ring,
# no shadow/background, and transparent center icons.
python -m pip install --quiet pillow
mkdir -p /tmp/v10-renderer/com/misanthropy/hit_indicator/client /tmp/v10-patcher-classes
unzip -p /tmp/hit_indicator-1.21.1-neoforge-v6.jar \
  com/misanthropy/hit_indicator/client/WindupIndicatorRenderer.class \
  > /tmp/v10-renderer/com/misanthropy/hit_indicator/client/WindupIndicatorRenderer.class

javac \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -d /tmp/v10-patcher-classes \
  ../runtime-test/PatchRendererV8.java ../runtime-test/PatchRendererV10Safe.java ../runtime-test/PatchRendererV11.java ../runtime-test/PatchRendererV12.java

java \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -cp /tmp/v10-patcher-classes \
  PatchRendererV8 \
  /tmp/v10-renderer/com/misanthropy/hit_indicator/client/WindupIndicatorRenderer.class

java \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -cp /tmp/v10-patcher-classes \
  PatchRendererV10Safe \
  /tmp/v10-renderer/com/misanthropy/hit_indicator/client/WindupIndicatorRenderer.class

java \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -cp /tmp/v10-patcher-classes \
  PatchRendererV11 \
  /tmp/v10-renderer/com/misanthropy/hit_indicator/client/WindupIndicatorRenderer.class

java \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -cp /tmp/v10-patcher-classes \
  PatchRendererV12 \
  /tmp/v10-renderer/com/misanthropy/hit_indicator/client/WindupIndicatorRenderer.class

python ../runtime-test/PackageV10.py \
  /tmp/hit_indicator-1.21.1-neoforge-v6.jar \
  /tmp/v10-renderer/com/misanthropy/hit_indicator/client/WindupIndicatorRenderer.class \
  /tmp/hit_indicator-1.21.1-neoforge-v12.jar

unzip -t /tmp/hit_indicator-1.21.1-neoforge-v12.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v12.jar -c -p \
  com.misanthropy.hit_indicator.client.WindupIndicatorRenderer > /tmp/renderer-v12.javap
grep -q 'timingFrame' /tmp/renderer-v12.javap
grep -q 'float 2.0332f' /tmp/renderer-v12.javap
grep -q 'float 0.84f' /tmp/renderer-v12.javap
grep -q 'float 2.4f' /tmp/renderer-v12.javap
grep -q 'float 0.5f' /tmp/renderer-v12.javap
grep -q 'float 0.75f' /tmp/renderer-v12.javap
grep -q 'float 0.25f' /tmp/renderer-v12.javap
grep -q 'APPEAR_ALPHA' /tmp/renderer-v12.javap
test "$(unzip -Z1 /tmp/hit_indicator-1.21.1-neoforge-v12.jar 'assets/hit_indicator/textures/indicator/timing/frame_*.png' | wc -l)" -eq 32

python ../runtime-test/VerifyV10.py /tmp/hit_indicator-1.21.1-neoforge-v12.jar

mkdir -p /tmp/v14-attack/com/misanthropy/hit_indicator/server /tmp/v14-patcher-classes
unzip -p /tmp/hit_indicator-1.21.1-neoforge-v12.jar \
  com/misanthropy/hit_indicator/server/AttackInterceptor.class \
  > /tmp/v14-attack/com/misanthropy/hit_indicator/server/AttackInterceptor.class

javac \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -d /tmp/v14-patcher-classes \
  ../runtime-test/PatchAttackPredictorV14.java

java \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -cp /tmp/v14-patcher-classes \
  PatchAttackPredictorV14 \
  /tmp/v14-attack/com/misanthropy/hit_indicator/server/AttackInterceptor.class

python ../runtime-test/PackageV14.py \
  /tmp/hit_indicator-1.21.1-neoforge-v12.jar \
  /tmp/v14-attack/com/misanthropy/hit_indicator/server/AttackInterceptor.class \
  build/classes/java/main/com/misanthropy/hit_indicator/server \
  /tmp/hit_indicator-1.21.1-neoforge-v15.jar

unzip -t /tmp/hit_indicator-1.21.1-neoforge-v15.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v15.jar -c -p \
  com.misanthropy.hit_indicator.server.AttackInterceptor > /tmp/attack-v15.javap
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v15.jar -c -p \
  com.misanthropy.hit_indicator.server.LearnedAttackPredictor > /tmp/predictor-v15.javap
grep -q 'LearnedAttackPredictor.prepareIncoming' /tmp/attack-v15.javap
grep -q 'LearnedAttackPredictor.tick' /tmp/attack-v15.javap
grep -q 'LearnedAttackPredictor.adjustDuration' /tmp/attack-v15.javap
grep -q 'LearnedAttackPredictor.sendWindupMaybe' /tmp/attack-v15.javap
grep -q 'LearnedAttackPredictor.deliverMobOrCaptured' /tmp/attack-v15.javap
grep -q 'LearnedAttackPredictor.shutdown' /tmp/attack-v15.javap
grep -q 'hit_indicator_learned_attacks.json' /tmp/predictor-v15.javap
grep -q 'PREDICT' /tmp/predictor-v15.javap
python -c "import json,zipfile; z=zipfile.ZipFile('/tmp/hit_indicator-1.21.1-neoforge-v15.jar'); m=json.loads(z.read('hit_indicator.mixins.json')); assert m.get('client') == [], m; print('V15_NO_FREEZE_MIXIN_PASS')"

# v16: deterministic fixed-delay interception, no learned predictor.
mkdir -p /tmp/v16-attack/com/misanthropy/hit_indicator/server /tmp/v16-patcher-classes
unzip -p /tmp/hit_indicator-1.21.1-neoforge-v15.jar \
  com/misanthropy/hit_indicator/server/AttackInterceptor.class \
  > /tmp/v16-attack/com/misanthropy/hit_indicator/server/AttackInterceptor.class

javac \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -d /tmp/v16-patcher-classes \
  ../runtime-test/PatchUniversalInterceptV16.java

java \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -cp /tmp/v16-patcher-classes \
  PatchUniversalInterceptV16 \
  /tmp/v16-attack/com/misanthropy/hit_indicator/server/AttackInterceptor.class

python ../runtime-test/PackageV16.py \
  /tmp/hit_indicator-1.21.1-neoforge-v15.jar \
  /tmp/v16-attack/com/misanthropy/hit_indicator/server/AttackInterceptor.class \
  /tmp/hit_indicator-1.21.1-neoforge-v16.jar

unzip -t /tmp/hit_indicator-1.21.1-neoforge-v16.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v16.jar -c -p \
  com.misanthropy.hit_indicator.server.AttackInterceptor > /tmp/attack-v16.javap
! grep -q 'LearnedAttackPredictor' /tmp/attack-v16.javap
grep -q 'HitIndicatorNetwork.sendWindup' /tmp/attack-v16.javap
grep -q 'deliverCaptured' /tmp/attack-v16.javap
python -c "import zipfile; z=zipfile.ZipFile('/tmp/hit_indicator-1.21.1-neoforge-v16.jar'); bad=[n for n in z.namelist() if 'LearnedAttackPredictor' in n]; assert not bad,bad; print('V16_NO_LEARNER_CLASSES_PASS')"
python -c "import json,zipfile; z=zipfile.ZipFile('/tmp/hit_indicator-1.21.1-neoforge-v16.jar'); m=json.loads(z.read('hit_indicator.mixins.json')); assert m.get('client') == [], m; print('V16_NO_FREEZE_MIXIN_PASS')"

# v17: restore the reference mod's exact client animation-freeze mechanism.
python ../runtime-test/PackageV17.py \
  /tmp/hit_indicator-1.21.1-neoforge-v16.jar \
  build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.class \
  /tmp/hit_indicator-1.21.1-neoforge-v17.jar

unzip -t /tmp/hit_indicator-1.21.1-neoforge-v17.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v17.jar -v -p \
  com.misanthropy.hit_indicator.mixin.client.LivingEntityFreezeMixin > /tmp/freeze-v17.javap
grep -q 'WindupTracker.shouldFreeze' /tmp/freeze-v17.javap
grep -q 'CallbackInfo.cancel' /tmp/freeze-v17.javap
grep -q 'method=\["tick"\]' /tmp/freeze-v17.javap
grep -q 'value="HEAD"' /tmp/freeze-v17.javap
grep -q 'cancellable=true' /tmp/freeze-v17.javap
python -c "import json,zipfile; z=zipfile.ZipFile('/tmp/hit_indicator-1.21.1-neoforge-v17.jar'); m=json.loads(z.read('hit_indicator.mixins.json')); assert m.get('client') == ['client.LivingEntityFreezeMixin'], m; assert 'com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.class' in z.namelist(); print('V17_REFERENCE_FREEZE_MIXIN_PASS')"

# v18: freeze Mob.tick before lower-priority animation hooks (e.g. Better Mob Combat: Reimagined).
python ../runtime-test/PackageV18.py \
  /tmp/hit_indicator-1.21.1-neoforge-v17.jar \
  build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/MobFreezeMixin.class \
  /tmp/hit_indicator-1.21.1-neoforge-v18.jar

unzip -t /tmp/hit_indicator-1.21.1-neoforge-v18.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v18.jar -v -p \
  com.misanthropy.hit_indicator.mixin.client.MobFreezeMixin > /tmp/mob-freeze-v18.javap
grep -q 'WindupTracker.shouldFreeze' /tmp/mob-freeze-v18.javap
grep -q 'CallbackInfo.cancel' /tmp/mob-freeze-v18.javap
grep -q 'value=\[class Lnet/minecraft/world/entity/Mob;\]' /tmp/mob-freeze-v18.javap
grep -q 'priority=500' /tmp/mob-freeze-v18.javap
grep -q 'method=\["tick"\]' /tmp/mob-freeze-v18.javap
grep -q 'value="HEAD"' /tmp/mob-freeze-v18.javap
grep -q 'cancellable=true' /tmp/mob-freeze-v18.javap
python -c "import json,zipfile; z=zipfile.ZipFile('/tmp/hit_indicator-1.21.1-neoforge-v18.jar'); m=json.loads(z.read('hit_indicator.mixins.json')); assert m.get('client') == ['client.MobFreezeMixin','client.LivingEntityFreezeMixin'], m; assert 'com/misanthropy/hit_indicator/mixin/client/MobFreezeMixin.class' in z.namelist(); print('V18_MOB_HEAD_FREEZE_PASS')"

# Better Combat 2.4.0 / Player Animation Lib 2.0.4 compatibility:
# freeze Player Animator's fractional render interpolation while the windup mob renders.
python ../runtime-test/PackageV19.py \
  /tmp/hit_indicator-1.21.1-neoforge-v18.jar \
  build/classes/java/main/com/misanthropy/hit_indicator/client/AnimationFreezeContext.class \
  build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LivingEntityRenderContextMixin.class \
  build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/PlayerAnimatorInterpolationFreezeMixin.class \
  /tmp/hit_indicator-1.21.1-neoforge-v19.jar

unzip -t /tmp/hit_indicator-1.21.1-neoforge-v19.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v19.jar -v -p \
  com.misanthropy.hit_indicator.mixin.client.PlayerAnimatorInterpolationFreezeMixin > /tmp/playeranim-freeze-v19.javap
grep -q 'dev.kosmx.playerAnim.core.impl.AnimationProcessor' /tmp/playeranim-freeze-v19.javap
grep -q 'setTickDelta(F)V' /tmp/playeranim-freeze-v19.javap
grep -q 'AnimationFreezeContext.shouldFreezeCurrent' /tmp/playeranim-freeze-v19.javap

# Remove the original 2-tick grace period from shouldFreeze().
mkdir -p /tmp/v20-tracker/com/misanthropy/hit_indicator/client /tmp/v20-patcher-classes
unzip -p /tmp/hit_indicator-1.21.1-neoforge-v19.jar \
  com/misanthropy/hit_indicator/client/WindupTracker.class \
  > /tmp/v20-tracker/com/misanthropy/hit_indicator/client/WindupTracker.class
javac \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -d /tmp/v20-patcher-classes \
  ../runtime-test/PatchImmediateFreezeV20.java
java \
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -cp /tmp/v20-patcher-classes \
  PatchImmediateFreezeV20 \
  /tmp/v20-tracker/com/misanthropy/hit_indicator/client/WindupTracker.class
python ../runtime-test/PackageV20.py \
  /tmp/hit_indicator-1.21.1-neoforge-v19.jar \
  /tmp/v20-tracker/com/misanthropy/hit_indicator/client/WindupTracker.class \
  /tmp/hit_indicator-1.21.1-neoforge-v20.jar
unzip -t /tmp/hit_indicator-1.21.1-neoforge-v20.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v20.jar -c -p \
  com.misanthropy.hit_indicator.client.WindupTracker > /tmp/windup-v20.javap
python -c "text=open('/tmp/windup-v20.javap').read(); block=text[text.index('public static boolean shouldFreeze'):text.index('public static void onClientTick')]; assert 'long 2l' not in block, block; assert 'long 0l' in block, block; print('V20_IMMEDIATE_FREEZE_THRESHOLD_PASS')"



python ../runtime-test/PackageV21.py \
  /tmp/hit_indicator-1.21.1-neoforge-v20.jar \
  build/classes/java/main/com/misanthropy/hit_indicator/client/FrozenEntityMaintenance.class \
  build/classes/java/main/com/misanthropy/hit_indicator/client/FrozenRenderClock.class \
  build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/ClientLevelTimeStopMixin.class \
  build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LevelRendererTimeStopMixin.class \
  /tmp/hit_indicator-1.21.1-neoforge-v21.jar

unzip -t /tmp/hit_indicator-1.21.1-neoforge-v21.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v21.jar -v -p \
  com.misanthropy.hit_indicator.mixin.client.ClientLevelTimeStopMixin > /tmp/clientlevel-time-stop-v21.javap
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v21.jar -v -p \
  com.misanthropy.hit_indicator.mixin.client.LevelRendererTimeStopMixin > /tmp/levelrenderer-time-stop-v21.javap
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v21.jar -c -p \
  com.misanthropy.hit_indicator.client.FrozenEntityMaintenance > /tmp/frozen-maintenance-v21.javap
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v21.jar -c -p \
  com.misanthropy.hit_indicator.client.FrozenRenderClock > /tmp/frozen-render-clock-v21.javap

grep -q 'tickNonPassenger' /tmp/clientlevel-time-stop-v21.javap
grep -q 'FrozenEntityMaintenance.sync' /tmp/clientlevel-time-stop-v21.javap
grep -q 'CallbackInfo.cancel' /tmp/clientlevel-time-stop-v21.javap
grep -q 'priority=500' /tmp/clientlevel-time-stop-v21.javap
grep -q 'renderEntity' /tmp/levelrenderer-time-stop-v21.javap
grep -q 'EntityRenderDispatcher;render' /tmp/levelrenderer-time-stop-v21.javap
grep -q 'FrozenRenderClock.partialFor' /tmp/levelrenderer-time-stop-v21.javap
grep -q 'xOld' /tmp/frozen-maintenance-v21.javap
grep -q 'walkDistO' /tmp/frozen-maintenance-v21.javap
grep -q 'yBodyRotO' /tmp/frozen-maintenance-v21.javap
grep -q 'oAttackAnim' /tmp/frozen-maintenance-v21.javap
python - <<'PY'
import json,zipfile
z=zipfile.ZipFile('/tmp/hit_indicator-1.21.1-neoforge-v21.jar')
m=json.loads(z.read('hit_indicator.mixins.json'))
assert m.get('client') == ['client.ClientLevelTimeStopMixin','client.LevelRendererTimeStopMixin'], m
for old in [
 'com/misanthropy/hit_indicator/mixin/client/MobFreezeMixin.class',
 'com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.class',
 'com/misanthropy/hit_indicator/mixin/client/PlayerAnimatorInterpolationFreezeMixin.class',
]:
    assert old not in z.namelist(), old
print('V21_STABLE_TIME_STOP_PACKAGE_PASS')
PY


python ../runtime-test/PackageV22.py \
  /tmp/hit_indicator-1.21.1-neoforge-v21.jar \
  build/classes/java/main/com/misanthropy/hit_indicator/client/FrozenRenderClock.class \
  build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/ClientLevelTimeStopMixin.class \
  /tmp/hit_indicator-1.21.1-neoforge-v22.jar

unzip -t /tmp/hit_indicator-1.21.1-neoforge-v22.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v22.jar -c -p \
  com.misanthropy.hit_indicator.client.FrozenRenderClock > /tmp/frozen-render-clock-v22.javap
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v22.jar -c -p \
  com.misanthropy.hit_indicator.mixin.client.ClientLevelTimeStopMixin > /tmp/clientlevel-time-stop-v22.javap
grep -q 'shouldHoldReleaseTick' /tmp/frozen-render-clock-v22.javap
grep -q 'isReleaseBridge' /tmp/frozen-render-clock-v22.javap
grep -q 'Math.min' /tmp/frozen-render-clock-v22.javap
grep -q 'FrozenRenderClock.shouldHoldReleaseTick' /tmp/clientlevel-time-stop-v22.javap
python - <<'PY'
import zipfile
z=zipfile.ZipFile('/tmp/hit_indicator-1.21.1-neoforge-v22.jar')
for p in [
 'com/misanthropy/hit_indicator/client/FrozenRenderClock.class',
 'com/misanthropy/hit_indicator/client/FrozenRenderClock$Release.class',
 'com/misanthropy/hit_indicator/mixin/client/ClientLevelTimeStopMixin.class',
]:
    assert p in z.namelist(), p
print('V22_CONTINUOUS_RESUME_PACKAGE_PASS')
PY


python ../runtime-test/PackageV23.py \
  /tmp/hit_indicator-1.21.1-neoforge-v22.jar \
  build/classes/java/main/com/misanthropy/hit_indicator/client/StasisDesaturationClientEvents.class \
  build/classes/java/main/com/misanthropy/hit_indicator/client/StasisDesaturationRenderer.class \
  build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LevelRendererTimeStopMixin.class \
  ../runtime-test/stasis_desaturate.json \
  ../runtime-test/stasis_desaturate.vsh \
  ../runtime-test/stasis_desaturate.fsh \
  /tmp/hit_indicator-1.21.1-neoforge-v23.jar \
  "v23 universal stasis grayscale"

unzip -t /tmp/hit_indicator-1.21.1-neoforge-v23.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v23.jar -c -p \
  com.misanthropy.hit_indicator.client.StasisDesaturationRenderer > /tmp/stasis-desaturation-v23.javap
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v23.jar -v -p \
  com.misanthropy.hit_indicator.client.StasisDesaturationClientEvents > /tmp/stasis-events-v23.javap
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v23.jar -v -p \
  com.misanthropy.hit_indicator.mixin.client.LevelRendererTimeStopMixin > /tmp/levelrenderer-stasis-v23.javap

grep -q 'WindupTracker.shouldFreeze' /tmp/stasis-desaturation-v23.javap
grep -q 'FrozenRenderClock.isReleaseBridge' /tmp/stasis-desaturation-v23.javap
grep -q 'RegisterShadersEvent' /tmp/stasis-events-v23.javap
grep -q 'TextureTarget' /tmp/stasis-desaturation-v23.javap
grep -q 'copyDepthFrom' /tmp/stasis-desaturation-v23.javap
grep -q 'StasisDesaturationRenderer.begin' /tmp/levelrenderer-stasis-v23.javap
grep -q 'StasisDesaturationRenderer.end' /tmp/levelrenderer-stasis-v23.javap

python - <<'PY'
import zipfile
z=zipfile.ZipFile('/tmp/hit_indicator-1.21.1-neoforge-v23.jar')
required=[
 'com/misanthropy/hit_indicator/client/StasisDesaturationClientEvents.class',
 'com/misanthropy/hit_indicator/client/StasisDesaturationRenderer.class',
 'com/misanthropy/hit_indicator/mixin/client/LevelRendererTimeStopMixin.class',
 'assets/hit_indicator/shaders/core/stasis_desaturate.json',
 'assets/hit_indicator/shaders/core/stasis_desaturate.vsh',
 'assets/hit_indicator/shaders/core/stasis_desaturate.fsh',
 'META-INF/hit-indicator-v23-marker.txt',
]
for p in required:
    assert p in z.namelist(), p
frag=z.read('assets/hit_indicator/shaders/core/stasis_desaturate.fsh').decode()
assert '0.92' in frag and 'dot(color.rgb' in frag
print('V23_UNIVERSAL_STASIS_GRAYSCALE_PACKAGE_PASS')
PY

python ../runtime-test/PackageV25.py \
  /tmp/hit_indicator-1.21.1-neoforge-v23.jar \
  build/classes/java/main/com/misanthropy/hit_indicator/client/EmfPoseFreeze.class \
  build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/EmfModelAnimationFreezeMixin.class \
  /tmp/hit_indicator-1.21.1-neoforge-v25.jar

unzip -t /tmp/hit_indicator-1.21.1-neoforge-v25.jar
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v25.jar -c -p \
  com.misanthropy.hit_indicator.client.EmfPoseFreeze > /tmp/emf-pose-v25.javap
javap -classpath /tmp/hit_indicator-1.21.1-neoforge-v25.jar -v -p \
  com.misanthropy.hit_indicator.mixin.client.EmfModelAnimationFreezeMixin > /tmp/emf-pose-mixin-v25.javap
grep -q 'ModelPart.visible' /tmp/emf-pose-v25.javap
grep -q 'ModelPart.skipDraw' /tmp/emf-pose-v25.javap
grep -q 'getAllParts' /tmp/emf-pose-v25.javap
grep -q 'traben.entity_model_features.models.parts.EMFModelPartRoot' /tmp/emf-pose-mixin-v25.javap
! grep -q 'registerPauseCondition' /tmp/emf-pose-v25.javap
python - <<'PY'
import json, zipfile
z=zipfile.ZipFile('/tmp/hit_indicator-1.21.1-neoforge-v25.jar')
m=json.loads(z.read('hit_indicator.mixins.json'))
assert m.get('client') == [
 'client.ClientLevelTimeStopMixin',
 'client.LevelRendererTimeStopMixin',
 'client.EmfModelAnimationFreezeMixin'
], m
for p in [
 'com/misanthropy/hit_indicator/client/EmfPoseFreeze.class',
 'com/misanthropy/hit_indicator/client/EmfPoseFreeze$PartState.class',
 'com/misanthropy/hit_indicator/client/EmfPoseFreeze$PoseSnapshot.class',
 'com/misanthropy/hit_indicator/mixin/client/EmfModelAnimationFreezeMixin.class',
 'META-INF/hit-indicator-v25-marker.txt',
]:
    assert p in z.namelist(), p
assert all('EmfStasisCompat' not in n for n in z.namelist())
print('V25_EMF_POSE_FREEZE_PACKAGE_PASS')
PY



rm -f src/main/java/com/misanthropy/hit_indicator/server/LearnedAttackPredictor.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/server/LearnedAttackPredictor*.class
rm -f src/main/java/com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.class
rm -f src/main/java/com/misanthropy/hit_indicator/mixin/client/MobFreezeMixin.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/MobFreezeMixin.class
rm -f src/main/java/com/misanthropy/hit_indicator/client/AnimationFreezeContext.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/client/AnimationFreezeContext.class
rm -f src/main/java/com/misanthropy/hit_indicator/mixin/client/LivingEntityRenderContextMixin.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LivingEntityRenderContextMixin.class
rm -f src/main/java/com/misanthropy/hit_indicator/mixin/client/PlayerAnimatorInterpolationFreezeMixin.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/PlayerAnimatorInterpolationFreezeMixin.class
rm -f src/main/java/com/misanthropy/hit_indicator/client/FrozenEntityMaintenance.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/client/FrozenEntityMaintenance.class
rm -f src/main/java/com/misanthropy/hit_indicator/client/FrozenRenderClock.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/client/FrozenRenderClock.class
rm -f src/main/java/com/misanthropy/hit_indicator/mixin/client/ClientLevelTimeStopMixin.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/ClientLevelTimeStopMixin.class
rm -f src/main/java/com/misanthropy/hit_indicator/mixin/client/LevelRendererTimeStopMixin.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LevelRendererTimeStopMixin.class
rm -f src/main/java/com/misanthropy/hit_indicator/client/StasisDesaturationRenderer.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/client/StasisDesaturationRenderer.class
rm -f src/main/java/com/misanthropy/hit_indicator/client/StasisDesaturationClientEvents.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/client/StasisDesaturationClientEvents.class
rm -f src/main/java/com/misanthropy/hit_indicator/client/EmfPoseFreeze.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/client/EmfPoseFreeze*.class
rm -f src/main/java/com/misanthropy/hit_indicator/mixin/client/EmfModelAnimationFreezeMixin.java
rm -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/EmfModelAnimationFreezeMixin.class

# Remove the synthetic PlayerAnimator compile stub before launching. The
# compatibility run must use the real Player Animation Lib implementation.
rm -f src/main/java/dev/kosmx/playerAnim/core/impl/AnimationProcessor.java
rm -f build/classes/java/main/dev/kosmx/playerAnim/core/impl/AnimationProcessor.class

# Subsequent Gradle run tasks may recompile RuntimeTestMod. Point its
# compileOnly/runtime now point at the final v23 universal-stasis-grayscale binary.
# The predictor source/classes are compiler-only intermediates and are removed below.
cp /tmp/hit_indicator-1.21.1-neoforge-v25.jar libs/
sed -i 's/hit_indicator-1.21.1-neoforge-v5.jar/hit_indicator-1.21.1-neoforge-v25.jar/g' build.gradle

rm -f run/mods/hit_indicator-1.21.1-neoforge-v5.jar
cp /tmp/hit_indicator-1.21.1-neoforge-v25.jar run/mods/
)

echo "=== V25 MODDED world generation ==="
(
set -uo pipefail
cd mdk
mkdir -p run
echo 'eula=true' > run/eula.txt
cat > run/server.properties <<'EOF'
level-name=world
online-mode=false
difficulty=normal
gamemode=survival
spawn-monsters=true
enable-command-block=false
view-distance=4
simulation-distance=4
EOF

setsid ./gradlew --no-daemon runServer > ../server-console.log 2>&1 &
SERVER_PID=$!
READY=0
for i in $(seq 1 180); do
  if grep -qE 'Done \(.*\)!|For help, type' run/logs/latest.log 2>/dev/null; then
    READY=1
    break
  fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done
cp run/logs/latest.log ../server-latest.log 2>/dev/null || true
kill -- -"$SERVER_PID" 2>/dev/null || true
sleep 3

if [ "$READY" -ne 1 ]; then
  echo "Dedicated server did not reach ready state"
  tail -200 ../server-console.log || true
  exit 1
fi
test -f run/world/level.dat
rm -rf run/saves/RuntimeTest
mkdir -p run/saves/RuntimeTest
cp -a run/world/. run/saves/RuntimeTest/
)

echo "=== V25 EMF ETF client-only setup ==="
(
set -euo pipefail
cd mdk
cat >> build.gradle <<'GRADLE'

dependencies {
    implementation "maven.modrinth:BVzZfTc1:YEMROAHv"
    implementation "maven.modrinth:4I1XuqiY:PAYgk63v"
}
GRADLE

./gradlew --no-daemon compileJava

ETF_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/BVzZfTc1/YEMROAHv" -type f -name '*.jar' | head -1)"
EMF_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/4I1XuqiY/PAYgk63v" -type f -name '*.jar' | head -1)"
test -n "$ETF_JAR" && test -f "$ETF_JAR"
test -n "$EMF_JAR" && test -f "$EMF_JAR"
echo "b2e396543c678b8378a2d5557a0cd3d24790364bff7e96edaf17921112d6176b  $ETF_JAR" | sha256sum -c -
echo "84c22f2be06ee6bfab265a443e075225878bba9f408843030dc6de3152e9dedb  $EMF_JAR" | sha256sum -c -
echo "EXACT_EMF_ETF_SHA256_PASS"
)

echo "=== V25 MODDED literal client ==="
(
set -uo pipefail
cd mdk
mkdir -p ../screenshots

cat > run/options.txt <<'EOT'
version:3955
onboardAccessibility:false
EOT

Xvfb :99 -screen 0 1280x720x24 -ac +extension GLX +render -noreset > ../xvfb.log 2>&1 &
export DISPLAY=:99
openbox > ../openbox.log 2>&1 &
sleep 2
glxinfo -B > ../glxinfo.txt 2>&1 || true

setsid ./gradlew --no-daemon runClient > ../client-console.log 2>&1 &
CLIENT_PID=$!

STARTED=0
for i in $(seq 1 240); do
  if grep -q '\[HI-MATRIX\] SCENARIO_START MELEE' run/logs/latest.log 2>/dev/null; then
    STARTED=1
    break
  fi
  if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done

if [ "$STARTED" -eq 1 ]; then
  xdotool search --name 'Minecraft' windowactivate 2>/dev/null || true
  for n in $(seq -w 1 70); do
    import -window root "../screenshots/frame_$n.png" 2>/dev/null || true
    sleep 0.08
  done
fi

RESULT=0
for i in $(seq 1 300); do
  if grep -q '\[HI-MATRIX\] STASIS_GRAYSCALE .*executed=true' run/logs/latest.log 2>/dev/null \
      && [ ! -f ../screenshots/stasis_grayscale.png ]; then
    import -window root ../screenshots/stasis_grayscale.png 2>/dev/null || true
  fi
  if grep -q '\[HI-MATRIX\] CONTINUOUS_RELEASE_RESUME .*liveClock=true' run/logs/latest.log 2>/dev/null \
      && [ ! -f ../screenshots/stasis_release.png ]; then
    import -window root ../screenshots/stasis_release.png 2>/dev/null || true
  fi
  if grep -q '\[HI-MATRIX\] SERVER_ALL_SCENARIOS_PASS' run/logs/latest.log 2>/dev/null; then
    RESULT=1
    sleep 5
    break
  fi
  if grep -q '\[HI-MATRIX\] FAIL' run/logs/latest.log 2>/dev/null; then
    break
  fi
  if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done

import -window root ../screenshots/final.png 2>/dev/null || true
cp run/logs/latest.log ../client-latest.log 2>/dev/null || true
grep -E '\[HI-MATRIX\]|hit_indicator|NoSuchFieldError|AbstractMethodError|InvalidMixinException|ERROR|FATAL' ../client-latest.log > ../filtered-runtime.log 2>/dev/null || true

kill -- -"$CLIENT_PID" 2>/dev/null || true
sleep 3

{
  echo "=== REQUIRED MARKERS ==="
  grep -E '\[HI-MATRIX\]' ../client-latest.log || true
  echo
  echo "=== HIT INDICATOR / FATALS ==="
  grep -E 'hit_indicator|NoSuchFieldError|AbstractMethodError|InvalidMixinException|FATAL' ../client-latest.log || true
} > ../runtime-summary.txt

if [ "$RESULT" -ne 1 ]; then
  echo "Runtime behavior matrix did not reach PASS"
  cat ../runtime-summary.txt
  exit 1
fi
)

echo "=== V25 MODDED assertions ==="
(
set -euo pipefail
grep -q '\[HI-MATRIX\] SERVER_ALL_SCENARIOS_PASS' client-latest.log
grep -q '\[HI-MATRIX\] MODDED_STACK_LOADED bettercombat=true playeranimator=true betterMobCombat=true' client-latest.log
grep -q 'better_mob_combat_reimagined' client-latest.log
grep -q 'bettercombat' client-latest.log
grep -q 'playeranimator' client-latest.log
for scenario in MELEE CANCEL FREEZE LUNGE DODGE SLAM SURE PARRY WHIFF RANGED CROWD UNIVERSAL_REPLAY; do
  grep -q "\[HI-MATRIX\] SCENARIO_PASS $scenario" client-latest.log
done
grep -q '\[HI-MATRIX\] CLIENT_TIME_STOP_DIRECT_PROBE .*tickCanceled=true .*maintenanceStable=true .*externalHeadBlocked=true' client-latest.log
grep -Eq '\[HI-MATRIX\] CLIENT_FREEZE_TRUE scenario=FREEZE .*kind=MELEE .*elapsed=[01]$' client-latest.log
grep -q '\[HI-MATRIX\] FROZEN_RENDER_CLOCK .*stable=true' client-latest.log
grep -q '\[HI-MATRIX\] EMF_POSE_PARTSTATE_SELFTEST pass=true' client-latest.log
grep -Eq '\[HI-MATRIX\] EMF_POSE_RUNTIME captures=[1-9][0-9]* restores=[1-9][0-9]* clears=[0-9]+ mismatches=0' client-latest.log
grep -q 'entity_model_features' client-latest.log
grep -q 'entity_texture_features' client-latest.log
grep -Eq '\[HI-MATRIX\] STASIS_GRAYSCALE shaderReady=true targetReady=true beginCount=[1-9][0-9]* passCount=[1-9][0-9]* executed=true entityPixels=[1-9][0-9]* beforeAlpha=2[4-5][0-9] .*pixelDesaturated=true' client-latest.log
test -f screenshots/stasis_grayscale.png
grep -q '\[HI-MATRIX\] CONTINUOUS_RELEASE_BRIDGE .*continuous=true' client-latest.log
grep -q '\[HI-MATRIX\] CONTINUOUS_RELEASE_RESUME bridgeCleared=true tickResumed=true externalResumed=true .*liveClock=true' client-latest.log
grep -q '\[HI-MATRIX\] UNIVERSAL_TRIGGER .*pending=true .*ticksLeft=10' client-latest.log
grep -q '\[HI-MATRIX\] UNIVERSAL_DELIVERED dealt=7.0' client-latest.log
! grep -q '\[HI-MATRIX\] FAIL' client-latest.log
! grep -Eq 'NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|VerifyError|InvalidMixinException' client-latest.log
! grep -Eq 'NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|VerifyError|InvalidMixinException' server-latest.log
)

echo "FULL_RUNTIME_MATRIX_PASS" >> runtime-summary.txt
echo "STABLE_WORLD_TIME_STOP_PASS" >> runtime-summary.txt
echo "STABLE_RENDER_CLOCK_PASS" >> runtime-summary.txt
echo "CONTINUOUS_RESUME_BRIDGE_PASS" >> runtime-summary.txt
echo "UNIVERSAL_STASIS_GRAYSCALE_PASS" >> runtime-summary.txt
echo "EXACT_BETTER_COMBAT_STACK_PASS" >> runtime-summary.txt
echo "EXACT_EMF_ETF_POSE_FREEZE_PASS" >> runtime-summary.txt
echo "EMF_MISSING_LIMB_REGRESSION_PASS" >> runtime-summary.txt
echo "IMMEDIATE_FREEZE_NO_2TICK_DELAY_PASS" >> runtime-summary.txt

python3 - <<'PY'
from pathlib import Path
import zipfile
out=Path('/tmp/hit-indicator-v25-emf-pose-freeze-modded-evidence.zip')
paths=[
    Path('/tmp/hit_indicator-1.21.1-neoforge-v25.jar'),
    Path('/tmp/windup-v20.javap'),
    Path('/tmp/clientlevel-time-stop-v21.javap'),
    Path('/tmp/levelrenderer-time-stop-v21.javap'),
    Path('/tmp/frozen-maintenance-v21.javap'),
    Path('/tmp/frozen-render-clock-v21.javap'),
    Path('/tmp/frozen-render-clock-v22.javap'),
    Path('/tmp/clientlevel-time-stop-v22.javap'),
    Path('/tmp/stasis-desaturation-v23.javap'),
    Path('/tmp/stasis-events-v23.javap'),
    Path('/tmp/levelrenderer-stasis-v23.javap'),
    Path('/tmp/emf-pose-v25.javap'),
    Path('/tmp/emf-pose-mixin-v25.javap'),
    Path('client-latest.log'),
    Path('server-latest.log'),
    Path('runtime-summary.txt'),
    Path('filtered-runtime.log'),
]
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
    for p in paths:
        if p.exists():
            z.write(p, p.name)
    shots=Path('screenshots')
    if shots.exists():
        for p in shots.glob('*.png'):
            z.write(p, 'screenshots/'+p.name)
print(out)
PY
