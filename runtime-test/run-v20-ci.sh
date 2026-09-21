#!/usr/bin/env bash
set -euo pipefail

echo "=== V20 reconstruct ==="
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

echo "=== V20 prepare/build ==="
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
mkdir -p src/main/java/dev/kosmx/playerAnim/core/impl
cp ../runtime-test/AnimationProcessor.java src/main/java/dev/kosmx/playerAnim/core/impl/AnimationProcessor.java
mkdir -p src/main/java/com/example/examplemod/mixin
cp ../runtime-test/MobAnimationProbe.java src/main/java/com/example/examplemod/MobAnimationProbe.java
cp ../runtime-test/MobAnimationProbeMixin.java src/main/java/com/example/examplemod/mixin/MobAnimationProbeMixin.java
cp ../runtime-test/hitindicatortest.mixins.json src/main/resources/hitindicatortest.mixins.json
printf '\n[[mixins]]\nconfig = "hitindicatortest.mixins.json"\n' >> src/main/templates/META-INF/neoforge.mods.toml

chmod +x gradlew
./gradlew --no-daemon compileJava

test -f build/classes/java/main/com/misanthropy/hit_indicator/server/ParticleCompat.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LivingEntityFreezeMixin.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/MobFreezeMixin.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/client/AnimationFreezeContext.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/LivingEntityRenderContextMixin.class
test -f build/classes/java/main/com/misanthropy/hit_indicator/mixin/client/PlayerAnimatorInterpolationFreezeMixin.class
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

# Subsequent Gradle run tasks may recompile RuntimeTestMod. Point its
# compileOnly/runtime now point at the final v20 immediate + Better Combat freeze binary.
# The predictor source/classes are compiler-only intermediates and are removed below.
cp /tmp/hit_indicator-1.21.1-neoforge-v20.jar libs/
sed -i 's/hit_indicator-1.21.1-neoforge-v5.jar/hit_indicator-1.21.1-neoforge-v20.jar/g' build.gradle

rm -f run/mods/hit_indicator-1.21.1-neoforge-v5.jar
cp /tmp/hit_indicator-1.21.1-neoforge-v20.jar run/mods/
)

echo "=== V20 world generation ==="
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

echo "=== V20 literal client ==="
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

echo "=== V20 assertions ==="
(
set -euo pipefail
grep -q '\[HI-MATRIX\] SERVER_ALL_SCENARIOS_PASS' client-latest.log
for scenario in MELEE CANCEL FREEZE LUNGE DODGE SLAM SURE PARRY WHIFF RANGED CROWD UNIVERSAL_REPLAY; do
  grep -q "\\[HI-MATRIX\\] SCENARIO_PASS $scenario" client-latest.log
done
grep -q '\[HI-MATRIX\] CLIENT_WINDUP scenario=MELEE .* kind=MELEE' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_WINDUP scenario=LUNGE .* kind=LUNGE' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_WINDUP scenario=SLAM .* kind=SLAM' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_WINDUP scenario=SURE .* kind=SURE' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_WINDUP scenario=RANGED .* kind=RANGED' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_RENDER scenario=FREEZE kind=MELEE' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_MIXIN_DIRECT_PROBE .*tickCanceled=true .*externalHeadBlocked=true' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_MIXIN_RELEASE_PROBE predicate=false released=true .*externalHeadResumed=true' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_FREEZE_TRUE scenario=FREEZE .*kind=MELEE' client-latest.log
grep -Eq '\[HI-MATRIX\] CLIENT_FREEZE_TRUE scenario=FREEZE .*kind=MELEE .*elapsed=[01]
grep -q '\[HI-MATRIX\] CLIENT_RENDER scenario=LUNGE kind=LUNGE' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_RENDER scenario=SLAM kind=SLAM' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_RENDER scenario=SURE kind=SURE' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_RENDER scenario=RANGED kind=RANGED' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_CANCEL_CLEARED' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_END scenario=DODGE .* reason=1' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_END scenario=WHIFF .* reason=2' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_END scenario=PARRY .* reason=3' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_CAMERA_SHAKE_STARTED' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_CAMERA_SHAKE_APPLIED' client-latest.log
grep -q '\[HI-MATRIX\] API_HEAVY_LANDED kind=LUNGE' client-latest.log
grep -q '\[HI-MATRIX\] API_HEAVY_LANDED kind=SLAM' client-latest.log
grep -q '\[HI-MATRIX\] API_HEAVY_LANDED kind=SURE_HIT' client-latest.log
grep -q '\[HI-MATRIX\] API_HEAVY_DODGED kind=LUNGE' client-latest.log
grep -q '\[HI-MATRIX\] API_HEAVY_PARRIED kind=SURE_HIT' client-latest.log
grep -q '\[HI-MATRIX\] API_DELIVERING_HEAVY kind=LUNGE' client-latest.log
grep -q '\[HI-MATRIX\] API_DELIVERING_HEAVY kind=SLAM' client-latest.log
grep -q '\[HI-MATRIX\] API_DELIVERING_HEAVY kind=SURE_HIT' client-latest.log
grep -q '\[HI-MATRIX\] UNIVERSAL_TRIGGER .*pending=true .*ticksLeft=10' client-latest.log
grep -q '\[HI-MATRIX\] UNIVERSAL_DELIVERED dealt=7.0' client-latest.log
grep -q '\[HI-MATRIX\] SCENARIO_PASS UNIVERSAL_REPLAY .*UNIVERSAL_CAPTURED_DAMAGE_DELAYED_AND_REPLAYED' client-latest.log
! grep -q '\[HI-LEARN\]' client-latest.log
! grep -q '\[HI-MATRIX\] FAIL' client-latest.log
! grep -q 'NoSuchFieldError' client-latest.log
! grep -q 'AbstractMethodError' client-latest.log
! grep -q 'InvalidMixinException' client-latest.log
! grep -q '/FATAL]' client-latest.log
! grep -q 'cannot parse particle' client-latest.log
! grep -Eq 'NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|VerifyError' client-latest.log
! grep -Eq 'NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|VerifyError' server-latest.log
! grep -Eq '\[[^]]+/(WARN|ERROR|FATAL)\] \[com\.misanthropy\.hit_indicator' client-latest.log
! grep -Eq '\[[^]]+/(WARN|ERROR|FATAL)\] \[com\.misanthropy\.hit_indicator' server-latest.log
echo 'UNIVERSAL_REPLAY_PASS' >> runtime-summary.txt
echo 'FULL_RUNTIME_MATRIX_PASS' >> runtime-summary.txt
echo 'GROUND_PARTICLE_PARSE_PASS' >> runtime-summary.txt
echo 'EXTERNAL_MOB_HEAD_ANIMATION_FREEZE_PASS' >> runtime-summary.txt client-latest.log
grep -q '\[HI-MATRIX\] PLAYERANIM_INTERPOLATION_FREEZE requested=0.75 applied=0.0 frozen=true' client-latest.log
grep -q '\[HI-MATRIX\] PLAYERANIM_INTERPOLATION_RELEASE requested=0.75 applied=0.75 resumed=true' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_RENDER scenario=LUNGE kind=LUNGE' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_RENDER scenario=SLAM kind=SLAM' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_RENDER scenario=SURE kind=SURE' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_RENDER scenario=RANGED kind=RANGED' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_CANCEL_CLEARED' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_END scenario=DODGE .* reason=1' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_END scenario=WHIFF .* reason=2' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_END scenario=PARRY .* reason=3' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_CAMERA_SHAKE_STARTED' client-latest.log
grep -q '\[HI-MATRIX\] CLIENT_CAMERA_SHAKE_APPLIED' client-latest.log
grep -q '\[HI-MATRIX\] API_HEAVY_LANDED kind=LUNGE' client-latest.log
grep -q '\[HI-MATRIX\] API_HEAVY_LANDED kind=SLAM' client-latest.log
grep -q '\[HI-MATRIX\] API_HEAVY_LANDED kind=SURE_HIT' client-latest.log
grep -q '\[HI-MATRIX\] API_HEAVY_DODGED kind=LUNGE' client-latest.log
grep -q '\[HI-MATRIX\] API_HEAVY_PARRIED kind=SURE_HIT' client-latest.log
grep -q '\[HI-MATRIX\] API_DELIVERING_HEAVY kind=LUNGE' client-latest.log
grep -q '\[HI-MATRIX\] API_DELIVERING_HEAVY kind=SLAM' client-latest.log
grep -q '\[HI-MATRIX\] API_DELIVERING_HEAVY kind=SURE_HIT' client-latest.log
grep -q '\[HI-MATRIX\] UNIVERSAL_TRIGGER .*pending=true .*ticksLeft=10' client-latest.log
grep -q '\[HI-MATRIX\] UNIVERSAL_DELIVERED dealt=7.0' client-latest.log
grep -q '\[HI-MATRIX\] SCENARIO_PASS UNIVERSAL_REPLAY .*UNIVERSAL_CAPTURED_DAMAGE_DELAYED_AND_REPLAYED' client-latest.log
! grep -q '\[HI-LEARN\]' client-latest.log
! grep -q '\[HI-MATRIX\] FAIL' client-latest.log
! grep -q 'NoSuchFieldError' client-latest.log
! grep -q 'AbstractMethodError' client-latest.log
! grep -q 'InvalidMixinException' client-latest.log
! grep -q '/FATAL]' client-latest.log
! grep -q 'cannot parse particle' client-latest.log
! grep -Eq 'NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|VerifyError' client-latest.log
! grep -Eq 'NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|VerifyError' server-latest.log
! grep -Eq '\[[^]]+/(WARN|ERROR|FATAL)\] \[com\.misanthropy\.hit_indicator' client-latest.log
! grep -Eq '\[[^]]+/(WARN|ERROR|FATAL)\] \[com\.misanthropy\.hit_indicator' server-latest.log
echo 'UNIVERSAL_REPLAY_PASS' >> runtime-summary.txt
echo 'FULL_RUNTIME_MATRIX_PASS' >> runtime-summary.txt
echo 'GROUND_PARTICLE_PARSE_PASS' >> runtime-summary.txt
echo 'EXTERNAL_MOB_HEAD_ANIMATION_FREEZE_PASS' >> runtime-summary.txt
)

echo "FULL_RUNTIME_MATRIX_PASS" >> runtime-summary.txt
echo "BETTERCOMBAT_PLAYERANIM_FREEZE_PASS" >> runtime-summary.txt
echo "IMMEDIATE_FREEZE_NO_2TICK_DELAY_PASS" >> runtime-summary.txt

python3 - <<'PY'
from pathlib import Path
import zipfile
out=Path('/tmp/hit-indicator-v20-evidence.zip')
paths=[
    Path('/tmp/hit_indicator-1.21.1-neoforge-v20.jar'),
    Path('/tmp/windup-v20.javap'),
    Path('/tmp/playeranim-freeze-v19.javap'),
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
