import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class PatchRendererV11 implements Opcodes {
    private static final float ACTIVE_SCALE_OLD = 1.0166f;
    private static final float ACTIVE_SCALE_NEW = 2.5415f;
    private static final float END_SCALE_OLD = 0.42f;
    private static final float END_SCALE_NEW = 1.05f;
    private static final float BURST_OLD = 1.2f;
    private static final float BURST_NEW = 3.0f;
    private static final float BODY_Y_OLD = 0.8f;
    private static final float BODY_Y_NEW = 0.5f;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("class path required");
        Path p = Paths.get(args[0]);
        byte[] in = Files.readAllBytes(p);
        ClassNode cn = new ClassNode();
        new ClassReader(in).accept(cn, 0);

        MethodNode render = null;
        for (MethodNode m : cn.methods) if (m.name.equals("onRenderLevel")) render = m;
        if (render == null) throw new IllegalStateException("onRenderLevel not found");

        int bodyYPatched = 0;
        int activeScalePatched = 0;
        int endScalePatched = 0;
        int burstPatched = 0;

        for (AbstractInsnNode n = render.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode mi && mi.getOpcode() == INVOKEVIRTUAL
                    && mi.owner.equals("net/minecraft/world/entity/LivingEntity")
                    && mi.name.equals("getBbHeight") && mi.desc.equals("()F")) {
                AbstractInsnNode next = nextReal(n);
                if (next instanceof LdcInsnNode ldc && ldc.cst instanceof Float f && close(f, BODY_Y_OLD)) {
                    ldc.cst = BODY_Y_NEW;
                    bodyYPatched++;
                    break;
                }
            }
        }

        for (AbstractInsnNode n = render.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof LdcInsnNode ldc && ldc.cst instanceof Float f) {
                if (close(f, ACTIVE_SCALE_OLD)) {
                    ldc.cst = ACTIVE_SCALE_NEW;
                    activeScalePatched++;
                } else if (close(f, END_SCALE_OLD)) {
                    ldc.cst = END_SCALE_NEW;
                    endScalePatched++;
                } else if (close(f, BURST_OLD)) {
                    ldc.cst = BURST_NEW;
                    burstPatched++;
                }
            }
        }

        if (bodyYPatched != 1) throw new IllegalStateException("Expected 1 body-Y patch, got " + bodyYPatched);
        if (activeScalePatched != 4) throw new IllegalStateException("Expected 4 active-scale patches, got " + activeScalePatched);
        if (endScalePatched != 5) throw new IllegalStateException("Expected 5 end-scale patches, got " + endScalePatched);
        if (burstPatched != 2) throw new IllegalStateException("Expected 2 burst-scale patches, got " + burstPatched);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        Files.write(p, cw.toByteArray());
        System.out.println("PATCH_V11_OK bodyY=" + bodyYPatched + " activeScale=" + activeScalePatched +
            " endScale=" + endScalePatched + " burst=" + burstPatched);
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < 0.0001f;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode n) {
        n = n.getNext();
        while (n != null && (n.getType() == AbstractInsnNode.LABEL ||
                n.getType() == AbstractInsnNode.LINE || n.getType() == AbstractInsnNode.FRAME)) n = n.getNext();
        return n;
    }
}
