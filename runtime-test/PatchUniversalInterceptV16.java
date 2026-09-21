import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class PatchUniversalInterceptV16 implements Opcodes {
    private static final String OWNER = "com/misanthropy/hit_indicator/server/AttackInterceptor";
    private static final String PRED = "com/misanthropy/hit_indicator/server/LearnedAttackPredictor";
    private static final String NET = "com/misanthropy/hit_indicator/network/HitIndicatorNetwork";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("class path required");
        Path p = Paths.get(args[0]);
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(p)).accept(cn, 0);

        MethodNode incoming = find(cn, "onLivingAttack");
        MethodNode serverTick = find(cn, "onServerTick");
        MethodNode stopped = find(cn, "onServerStopped");
        MethodNode deliver = find(cn, "deliverTo");
        if (incoming == null || serverTick == null || stopped == null || deliver == null)
            throw new IllegalStateException("required methods not found");

        boolean prepareRemoved = removeCallWithPreviousReal(incoming, PRED, "prepareIncoming", 2);
        boolean durationRemoved = removeCallWithNeighbors(incoming, PRED, "adjustDuration");

        boolean sendRestored = false;
        for (AbstractInsnNode n = incoming.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode mi && mi.getOpcode() == INVOKESTATIC
                    && mi.owner.equals(PRED) && mi.name.equals("sendWindupMaybe")
                    && mi.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;IIII)V")) {
                mi.owner = NET;
                mi.name = "sendWindup";
                sendRestored = true;
                break;
            }
        }

        boolean replayRedirected = false;
        for (AbstractInsnNode n = deliver.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode mi && mi.getOpcode() == INVOKESTATIC
                    && mi.owner.equals(PRED) && mi.name.equals("deliverMobOrCaptured")
                    && mi.desc.equals("(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/damagesource/DamageSource;F)Z")) {
                mi.owner = OWNER;
                mi.name = "deliverCaptured";
                replayRedirected = true;
                break;
            }
        }

        boolean tickRemoved = removeCallWithPreviousReal(serverTick, PRED, "tick", 2);
        boolean shutdownRemoved = removeCallWithPreviousReal(stopped, PRED, "shutdown", 2);

        if (find(cn, "deliverCaptured") != null)
            throw new IllegalStateException("deliverCaptured already exists");

        MethodNode helper = new MethodNode(ACC_PRIVATE | ACC_STATIC,
                "deliverCaptured",
                "(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                null, null);
        helper.instructions.add(new VarInsnNode(ALOAD, 1));
        helper.instructions.add(new VarInsnNode(ALOAD, 2));
        helper.instructions.add(new VarInsnNode(FLOAD, 3));
        helper.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,
                "net/minecraft/server/level/ServerPlayer",
                "hurt",
                "(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                false));
        helper.instructions.add(new InsnNode(IRETURN));
        helper.maxStack = 3;
        helper.maxLocals = 4;
        cn.methods.add(helper);

        if (!prepareRemoved || !durationRemoved || !sendRestored || !replayRedirected || !tickRemoved || !shutdownRemoved) {
            throw new IllegalStateException("patch incomplete prepare=" + prepareRemoved
                    + " duration=" + durationRemoved + " send=" + sendRestored
                    + " replay=" + replayRedirected + " tick=" + tickRemoved
                    + " shutdown=" + shutdownRemoved);
        }

        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode mi && mi.owner.equals(PRED)) {
                    throw new IllegalStateException("remaining predictor call " + m.name + " -> " + mi.name + mi.desc);
                }
            }
        }

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        Files.write(p, cw.toByteArray());
        System.out.println("PATCH_V16_UNIVERSAL_INTERCEPT_OK");
    }

    private static MethodNode find(ClassNode cn, String name) {
        for (MethodNode m : cn.methods) if (m.name.equals(name)) return m;
        return null;
    }

    private static boolean removeCallWithPreviousReal(MethodNode m, String owner, String name, int prevCount) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode mi && mi.owner.equals(owner) && mi.name.equals(name)) {
                List<AbstractInsnNode> remove = new ArrayList<>();
                AbstractInsnNode p = n;
                for (int i = 0; i < prevCount; i++) {
                    p = prevReal(p);
                    if (p == null) return false;
                    remove.add(p);
                }
                remove.add(n);
                for (AbstractInsnNode x : remove) m.instructions.remove(x);
                return true;
            }
        }
        return false;
    }

    private static boolean removeCallWithNeighbors(MethodNode m, String owner, String name) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode mi && mi.owner.equals(owner) && mi.name.equals(name)
                    && mi.desc.equals("(I)I")) {
                AbstractInsnNode prev = prevReal(n);
                AbstractInsnNode next = nextReal(n);
                if (!(prev instanceof VarInsnNode pv) || pv.getOpcode() != ILOAD
                        || !(next instanceof VarInsnNode nv) || nv.getOpcode() != ISTORE
                        || pv.var != nv.var) {
                    throw new IllegalStateException("unexpected adjustDuration shape");
                }
                m.instructions.remove(prev);
                m.instructions.remove(n);
                m.instructions.remove(next);
                return true;
            }
        }
        return false;
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
