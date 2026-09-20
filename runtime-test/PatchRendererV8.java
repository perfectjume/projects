import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class PatchRendererV8 implements Opcodes {
    static final String OWNER = "com/misanthropy/hit_indicator/client/WindupIndicatorRenderer";
    static final String WINDUP = "com/misanthropy/hit_indicator/client/WindupTracker$Windup";
    static final String RL = "net/minecraft/resources/ResourceLocation";
    static final String MTH = "net/minecraft/util/Mth";
    static final float VISUAL_SIZE = 1.0166f;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("class path required");
        Path p = Paths.get(args[0]);
        byte[] in = Files.readAllBytes(p);
        ClassNode cn = new ClassNode();
        new ClassReader(in).accept(cn, 0);

        MethodNode render = null;
        for (MethodNode m : cn.methods) if (m.name.equals("onRenderLevel")) render = m;
        if (render == null) throw new IllegalStateException("onRenderLevel not found");

        int progressPatched = 0, showNotchPatched = 0, openPatched = 0, sizePatched = 0, shadowPatched = 0;

        for (AbstractInsnNode n = render.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() == ISTORE && ((VarInsnNode)n).var == 13) {
                InsnList add = new InsnList();
                add.add(new InsnNode(ICONST_0));
                add.add(new VarInsnNode(ISTORE, 13));
                render.instructions.insert(n, add);
                showNotchPatched++;
                break;
            }
        }

        for (AbstractInsnNode n = render.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof FieldInsnNode f && n.getOpcode() == GETFIELD &&
                f.owner.equals(WINDUP) && f.name.equals("durationTicks") && f.desc.equals("I")) {
                AbstractInsnNode prev = previousReal(n);
                AbstractInsnNode next = nextReal(n);
                AbstractInsnNode next2 = nextReal(next);
                if (prev instanceof VarInsnNode pv && pv.getOpcode() == ALOAD && pv.var == 17 &&
                    next != null && next.getOpcode() == I2F && next2 != null && next2.getOpcode() == FDIV) {
                    AbstractInsnNode beforePrev = previousReal(prev);
                    if (beforePrev instanceof VarInsnNode fv && fv.getOpcode() == FLOAD && fv.var == 20) {
                        InsnList add = new InsnList();
                        add.add(new VarInsnNode(ALOAD, 17));
                        add.add(new FieldInsnNode(GETFIELD, WINDUP, "notchTicks", "I"));
                        add.add(new InsnNode(ISUB));
                        render.instructions.insert(n, add);
                        progressPatched++;
                        break;
                    }
                }
            }
        }

        for (AbstractInsnNode n = render.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof LdcInsnNode ldc && ldc.cst instanceof Float f && Math.abs(f - 0.782f) < 0.00001f) {
                ldc.cst = VISUAL_SIZE;
                shadowPatched++;
            }
        }

        for (AbstractInsnNode n = render.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof FieldInsnNode f && n.getOpcode() == GETSTATIC &&
                f.owner.equals(OWNER) && f.name.equals("RING") && f.desc.equals("L"+RL+";")) {
                AbstractInsnNode nx = nextReal(n);
                if (nx instanceof MethodInsnNode mi && mi.getOpcode() == INVOKESTATIC &&
                    mi.owner.equals(OWNER) && mi.name.equals("open") &&
                    mi.desc.equals("(L"+RL+";)Lcom/mojang/blaze3d/vertex/BufferBuilder;")) {
                    InsnList fixed = new InsnList();
                    fixed.add(new FieldInsnNode(GETSTATIC, OWNER, "RING", "L"+RL+";"));
                    fixed.add(new VarInsnNode(ALOAD, 23));
                    fixed.add(new LdcInsnNode(VISUAL_SIZE));
                    fixed.add(new InsnNode(FCONST_1));
                    fixed.add(new InsnNode(FCONST_1));
                    fixed.add(new InsnNode(FCONST_1));
                    fixed.add(new InsnNode(FCONST_1));
                    fixed.add(new MethodInsnNode(INVOKESTATIC, OWNER, "draw",
                        "(L"+RL+";Lorg/joml/Matrix4f;FFFFF)V", false));
                    render.instructions.insertBefore(n, fixed);

                    InsnList repl = new InsnList();
                    repl.add(new VarInsnNode(FLOAD, 21));
                    repl.add(new MethodInsnNode(INVOKESTATIC, OWNER, "timingFrame", "(F)L"+RL+";", false));
                    render.instructions.insertBefore(n, repl);
                    render.instructions.remove(n);
                    openPatched++;
                    break;
                }
            }
        }

        for (AbstractInsnNode n = render.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof VarInsnNode v && v.getOpcode() == FLOAD && v.var == 21) {
                AbstractInsnNode a = nextReal(n);
                AbstractInsnNode b = nextReal(a);
                AbstractInsnNode c = nextReal(b);
                AbstractInsnNode d = nextReal(c);
                if (a instanceof LdcInsnNode la && la.cst instanceof Float fa && Math.abs(fa - 1.35f) < 0.00001f &&
                    b instanceof LdcInsnNode lb && lb.cst instanceof Float fb && Math.abs(fb - 0.42f) < 0.00001f &&
                    c instanceof MethodInsnNode mc && mc.getOpcode() == INVOKESTATIC &&
                    mc.owner.equals(MTH) && mc.name.equals("lerp") && mc.desc.equals("(FFF)F") &&
                    d instanceof VarInsnNode sd && sd.getOpcode() == FSTORE && sd.var == 33) {
                    InsnList repl = new InsnList();
                    repl.add(new LdcInsnNode(VISUAL_SIZE));
                    repl.add(new VarInsnNode(FSTORE, 33));
                    render.instructions.insertBefore(n, repl);
                    removeRange(render.instructions, n, d);
                    sizePatched++;
                    break;
                }
            }
        }

        MethodNode helper = new MethodNode(ACC_PRIVATE | ACC_STATIC, "timingFrame", "(F)L"+RL+";", null, null);
        InsnList h = helper.instructions;
        h.add(new VarInsnNode(FLOAD, 0));
        h.add(new LdcInsnNode(31.0f));
        h.add(new InsnNode(FMUL));
        h.add(new InsnNode(F2I));
        h.add(new VarInsnNode(ISTORE, 1));
        h.add(new VarInsnNode(ILOAD, 1));
        h.add(new InsnNode(ICONST_0));
        h.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Math", "max", "(II)I", false));
        h.add(new IntInsnNode(BIPUSH, 31));
        h.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false));
        h.add(new VarInsnNode(ISTORE, 1));
        h.add(new LdcInsnNode("hit_indicator"));
        h.add(new LdcInsnNode("textures/indicator/timing/frame_"));
        h.add(new VarInsnNode(ILOAD, 1));
        h.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false));
        h.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false));
        h.add(new LdcInsnNode(".png"));
        h.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false));
        h.add(new MethodInsnNode(INVOKESTATIC, RL, "fromNamespaceAndPath",
            "(Ljava/lang/String;Ljava/lang/String;)L"+RL+";", false));
        h.add(new InsnNode(ARETURN));
        helper.maxStack = 3;
        helper.maxLocals = 2;
        cn.methods.add(helper);

        if (progressPatched != 1 || showNotchPatched != 1 || openPatched != 1 || sizePatched != 1 || shadowPatched != 2) {
            throw new IllegalStateException("patch counts progress="+progressPatched+" show="+showNotchPatched+
                " open="+openPatched+" size="+sizePatched+" shadow="+shadowPatched);
        }

        render.maxStack = Math.max(render.maxStack, 8);
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        Files.write(p, cw.toByteArray());
        System.out.println("PATCH_OK progress="+progressPatched+" show="+showNotchPatched+
            " open="+openPatched+" size="+sizePatched+" shadow="+shadowPatched);
    }

    static AbstractInsnNode nextReal(AbstractInsnNode n) {
        if (n == null) return null;
        n = n.getNext();
        while (n != null && (n.getType() == AbstractInsnNode.LABEL ||
                n.getType() == AbstractInsnNode.LINE || n.getType() == AbstractInsnNode.FRAME)) n = n.getNext();
        return n;
    }

    static AbstractInsnNode previousReal(AbstractInsnNode n) {
        if (n == null) return null;
        n = n.getPrevious();
        while (n != null && (n.getType() == AbstractInsnNode.LABEL ||
                n.getType() == AbstractInsnNode.LINE || n.getType() == AbstractInsnNode.FRAME)) n = n.getPrevious();
        return n;
    }

    static void removeRange(InsnList list, AbstractInsnNode start, AbstractInsnNode end) {
        AbstractInsnNode n = start;
        while (n != null) {
            AbstractInsnNode next = n.getNext();
            list.remove(n);
            if (n == end) break;
            n = next;
        }
    }
}
