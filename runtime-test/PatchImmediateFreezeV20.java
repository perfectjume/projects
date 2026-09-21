import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class PatchImmediateFreezeV20 implements Opcodes {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("class path required");
        Path p = Paths.get(args[0]);

        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(p)).accept(cn, 0);

        MethodNode target = null;
        for (MethodNode m : cn.methods) {
            if (m.name.equals("shouldFreeze")
                    && m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;)Z")) {
                target = m;
                break;
            }
        }
        if (target == null) throw new IllegalStateException("shouldFreeze not found");

        int changed = 0;
        for (AbstractInsnNode n = target.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof LdcInsnNode ldc
                    && ldc.cst instanceof Long
                    && ((Long) ldc.cst).longValue() == 2L) {
                AbstractInsnNode next = nextReal(n);
                if (next != null && next.getOpcode() == LCMP) {
                    ldc.cst = Long.valueOf(0L);
                    changed++;
                }
            }
        }

        if (changed != 1) {
            throw new IllegalStateException("expected exactly one 2L freeze threshold, changed=" + changed);
        }

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        Files.write(p, cw.toByteArray());
        System.out.println("PATCH_V20_IMMEDIATE_FREEZE_OK");
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode n) {
        n = n.getNext();
        while (n != null && (n.getType() == AbstractInsnNode.LABEL
                || n.getType() == AbstractInsnNode.LINE
                || n.getType() == AbstractInsnNode.FRAME)) {
            n = n.getNext();
        }
        return n;
    }
}
