import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class PatchAttackPredictorV14 implements Opcodes {
    private static final String OWNER = "com/misanthropy/hit_indicator/server/AttackInterceptor";
    private static final String PRED = "com/misanthropy/hit_indicator/server/LearnedAttackPredictor";
    private static final String NET = "com/misanthropy/hit_indicator/network/HitIndicatorNetwork";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("class path required");
        Path p = Paths.get(args[0]);
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(p)).accept(cn, 0);

        MethodNode incoming = null, serverTick = null, stopped = null;
        for (MethodNode m : cn.methods) {
            if (m.name.equals("onLivingAttack")) incoming = m;
            else if (m.name.equals("onServerTick")) serverTick = m;
            else if (m.name.equals("onServerStopped")) stopped = m;
        }
        if (incoming == null || serverTick == null || stopped == null)
            throw new IllegalStateException("required methods not found");

        // Insert LearnedAttackPredictor.onIncoming(attacker, victim) before the
        // normal setCanceled path (the second setCanceled call in the method).
        int cancelCount = 0;
        boolean incomingHook = false;
        for (AbstractInsnNode n = incoming.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode mi
                    && mi.owner.equals("net/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent")
                    && mi.name.equals("setCanceled")
                    && mi.desc.equals("(Z)V")) {
                cancelCount++;
                if (cancelCount == 2) {
                    AbstractInsnNode insertBefore = prevReal(prevReal(n)); // ALOAD event, ICONST_1
                    InsnList add = new InsnList();
                    add.add(new VarInsnNode(ALOAD, 3)); // attacker
                    add.add(new VarInsnNode(ALOAD, 1)); // victim
                    add.add(new MethodInsnNode(INVOKESTATIC, PRED, "prepareIncoming",
                            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerPlayer;)V", false));
                    incoming.instructions.insertBefore(insertBefore, add);
                    incomingHook = true;
                    break;
                }
            }
        }

        // At the common join after heavy/normal windup duration selection,
        // replace durationTicks through a helper call without adding control flow.
        boolean durationHook = false;
        for (AbstractInsnNode n = incoming.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof FieldInsnNode fi
                    && fi.getOpcode() == GETSTATIC
                    && fi.owner.equals(OWNER)
                    && fi.name.equals("PENDING")) {
                AbstractInsnNode next = nextReal(n);
                if (next instanceof VarInsnNode vi && vi.getOpcode() == ILOAD && vi.var == 4) {
                    AbstractInsnNode after = nextReal(next);
                    if (after instanceof TypeInsnNode ti
                            && ti.getOpcode() == NEW
                            && ti.desc.equals(OWNER + "$PendingHit")) {
                        InsnList add = new InsnList();
                        add.add(new VarInsnNode(ILOAD, 7));
                        add.add(new MethodInsnNode(INVOKESTATIC, PRED, "adjustDuration", "(I)I", false));
                        add.add(new VarInsnNode(ISTORE, 7));
                        incoming.instructions.insertBefore(n, add);
                        durationHook = true;
                        break;
                    }
                }
            }
        }

        // Replace the normal packet call with a helper that suppresses it only
        // when a learned ring is already visible.  No new branch in this method.
        boolean sendHook = false;
        for (AbstractInsnNode n = incoming.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode mi
                    && mi.getOpcode() == INVOKESTATIC
                    && mi.owner.equals(NET)
                    && mi.name.equals("sendWindup")
                    && mi.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;IIII)V")) {
                mi.owner = PRED;
                mi.name = "sendWindupMaybe";
                mi.desc = "(Lnet/minecraft/server/level/ServerPlayer;IIII)V";
                sendHook = true;
                break;
            }
        }

        // Run observation/prediction every server tick before normal pending processing.
        InsnList tickAdd = new InsnList();
        tickAdd.add(new VarInsnNode(ALOAD, 0));
        tickAdd.add(new MethodInsnNode(INVOKEVIRTUAL,
                "net/neoforged/neoforge/event/tick/ServerTickEvent$Post",
                "getServer", "()Lnet/minecraft/server/MinecraftServer;", false));
        tickAdd.add(new MethodInsnNode(INVOKESTATIC, PRED, "tick",
                "(Lnet/minecraft/server/MinecraftServer;)V", false));
        serverTick.instructions.insert(tickAdd);

        // Clear learned runtime state when the server stops.
        InsnList stopAdd = new InsnList();
        stopAdd.add(new MethodInsnNode(INVOKESTATIC, PRED, "reset", "()V", false));
        stopped.instructions.insert(stopAdd);

        if (!incomingHook || !durationHook || !sendHook)
            throw new IllegalStateException("patch incomplete incoming=" + incomingHook +
                    " duration=" + durationHook + " send=" + sendHook);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        Files.write(p, cw.toByteArray());
        System.out.println("PATCH_V14_PREDICTOR_OK");
    }

    private static AbstractInsnNode prevReal(AbstractInsnNode n) {
        n = n.getPrevious();
        while (n != null && (n.getType() == AbstractInsnNode.LABEL
                || n.getType() == AbstractInsnNode.LINE
                || n.getType() == AbstractInsnNode.FRAME)) n = n.getPrevious();
        return n;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode n) {
        n = n.getNext();
        while (n != null && (n.getType() == AbstractInsnNode.LABEL
                || n.getType() == AbstractInsnNode.LINE
                || n.getType() == AbstractInsnNode.FRAME)) n = n.getNext();
        return n;
    }
}
