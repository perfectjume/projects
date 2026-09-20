import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class PatchRendererV12 implements Opcodes {
    static final String OWNER = "com/misanthropy/hit_indicator/client/WindupIndicatorRenderer";
    static final String MTH = "net/minecraft/util/Mth";
    static final String MATRIX4F = "org/joml/Matrix4f";

    static final float ACTIVE_OLD = 2.5415f;
    static final float ACTIVE_NEW = 2.0332f;
    static final float END_OLD = 1.05f;
    static final float END_NEW = 0.84f;
    static final float BURST_OLD = 3.0f;
    static final float BURST_NEW = 2.4f;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("class path required");
        Path p = Paths.get(args[0]);
        byte[] in = Files.readAllBytes(p);
        ClassNode cn = new ClassNode();
        new ClassReader(in).accept(cn, 0);

        for (FieldNode f : cn.fields) {
            if (f.name.equals("APPEAR_ALPHA")) throw new IllegalStateException("APPEAR_ALPHA already exists");
        }
        cn.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC, "APPEAR_ALPHA", "F", null, null));

        MethodNode render = null, vertex = null;
        for (MethodNode m : cn.methods) {
            if (m.name.equals("onRenderLevel")) render = m;
            if (m.name.equals("vertex") &&
                m.desc.equals("(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;FFFFFFFF)V")) vertex = m;
        }
        if (render == null || vertex == null) throw new IllegalStateException("renderer methods not found");

        int activePatched=0, endPatched=0, burstPatched=0;
        int alphaInitPatched=0, matrixAnimPatched=0, vertexAlphaPatched=0;

        for (AbstractInsnNode n=render.instructions.getFirst(); n!=null; n=n.getNext()) {
            if (n instanceof LdcInsnNode ldc && ldc.cst instanceof Float f) {
                if (close(f, ACTIVE_OLD)) { ldc.cst = ACTIVE_NEW; activePatched++; }
                else if (close(f, END_OLD)) { ldc.cst = END_NEW; endPatched++; }
                else if (close(f, BURST_OLD)) { ldc.cst = BURST_NEW; burstPatched++; }
            }
        }

        // 100 ms at 20 TPS = 2 ticks.
        // APPEAR_ALPHA = clamp(age / 2, 0, 1).
        for (AbstractInsnNode n=render.instructions.getFirst(); n!=null; n=n.getNext()) {
            if (n instanceof VarInsnNode v && v.getOpcode()==FSTORE && v.var==20) {
                InsnList add = new InsnList();
                add.add(new VarInsnNode(FLOAD, 20));
                add.add(new InsnNode(FCONST_2));
                add.add(new InsnNode(FDIV));
                add.add(new InsnNode(FCONST_0));
                add.add(new InsnNode(FCONST_1));
                add.add(new MethodInsnNode(INVOKESTATIC, MTH, "clamp", "(FFF)F", false));
                add.add(new FieldInsnNode(PUTSTATIC, OWNER, "APPEAR_ALPHA", "F"));
                render.instructions.insert(n, add);
                alphaInitPatched++;
                break;
            }
        }

        // Entrance size = 0.75 + 0.25 * APPEAR_ALPHA.
        for (AbstractInsnNode n=render.instructions.getFirst(); n!=null; n=n.getNext()) {
            if (n instanceof VarInsnNode v && v.getOpcode()==ASTORE && v.var==23) {
                InsnList add = new InsnList();
                add.add(new VarInsnNode(ALOAD, 23));
                add.add(new LdcInsnNode(0.75f));
                add.add(new LdcInsnNode(0.25f));
                add.add(new FieldInsnNode(GETSTATIC, OWNER, "APPEAR_ALPHA", "F"));
                add.add(new InsnNode(FMUL));
                add.add(new InsnNode(FADD));
                add.add(new InsnNode(DUP));
                add.add(new InsnNode(DUP));
                add.add(new MethodInsnNode(INVOKEVIRTUAL, MATRIX4F, "scale", "(FFF)Lorg/joml/Matrix4f;", false));
                add.add(new InsnNode(POP));
                render.instructions.insert(n, add);
                matrixAnimPatched++;
                break;
            }
        }

        // Fade opacity in over the same 2 ticks.
        for (AbstractInsnNode n=vertex.instructions.getFirst(); n!=null; n=n.getNext()) {
            if (n instanceof VarInsnNode v && v.getOpcode()==FLOAD && v.var==9) {
                InsnList add = new InsnList();
                add.add(new FieldInsnNode(GETSTATIC, OWNER, "APPEAR_ALPHA", "F"));
                add.add(new InsnNode(FMUL));
                vertex.instructions.insert(n, add);
                vertexAlphaPatched++;
                break;
            }
        }

        if (activePatched != 4) throw new IllegalStateException("active patches=" + activePatched);
        if (endPatched != 5) throw new IllegalStateException("end patches=" + endPatched);
        if (burstPatched != 2) throw new IllegalStateException("burst patches=" + burstPatched);
        if (alphaInitPatched != 1) throw new IllegalStateException("alpha init patches=" + alphaInitPatched);
        if (matrixAnimPatched != 1) throw new IllegalStateException("matrix anim patches=" + matrixAnimPatched);
        if (vertexAlphaPatched != 1) throw new IllegalStateException("vertex alpha patches=" + vertexAlphaPatched);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        Files.write(p, cw.toByteArray());
        System.out.println("PATCH_V12_OK active="+activePatched+" end="+endPatched+" burst="+burstPatched+
            " alphaInit="+alphaInitPatched+" matrixAnim="+matrixAnimPatched+" vertexAlpha="+vertexAlphaPatched);
    }

    static boolean close(float a, float b) { return Math.abs(a-b) < 0.0001f; }
}
