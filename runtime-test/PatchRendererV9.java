import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class PatchRendererV9 implements Opcodes {
    static final String OWNER = "com/misanthropy/hit_indicator/client/WindupIndicatorRenderer";
    static final String RL = "net/minecraft/resources/ResourceLocation";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("class path required");
        Path p = Paths.get(args[0]);
        byte[] in = Files.readAllBytes(p);
        ClassNode cn = new ClassNode();
        new ClassReader(in).accept(cn, 0);

        MethodNode render = null;
        for (MethodNode m : cn.methods) if (m.name.equals("onRenderLevel")) render = m;
        if (render == null) throw new IllegalStateException("onRenderLevel not found");

        int removedShadow = 0;
        int patchedOuter = 0;

        for (AbstractInsnNode n = render.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() == GETSTATIC && n instanceof FieldInsnNode f &&
                f.owner.equals(OWNER) && f.name.equals("SHADOW") && f.desc.equals("L"+RL+";")) {
                AbstractInsnNode end = n;
                while (end != null) {
                    if (end.getOpcode() == INVOKESTATIC && end instanceof MethodInsnNode mi &&
                        mi.owner.equals(OWNER) && mi.name.equals("draw") &&
                        mi.desc.equals("(L"+RL+";Lorg/joml/Matrix4f;FFFFF)V")) break;
                    end = end.getNext();
                }
                if (end == null) throw new IllegalStateException("shadow draw end not found");
                AbstractInsnNode after = end.getNext();
                removeRange(render.instructions, n, end);
                removedShadow++;
                n = after;
                if (n == null) break;
            }
        }

        for (AbstractInsnNode n = render.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() == FSTORE && n instanceof VarInsnNode vs && vs.var == 33) {
                AbstractInsnNode prev = previousReal(n);
                if (prev instanceof LdcInsnNode ldc && ldc.cst instanceof Float fv && Math.abs(fv - 1.0166f) < 0.0001f) {
                    AbstractInsnNode start = nextReal(n);
                    if (!(start instanceof VarInsnNode sv && sv.getOpcode() == ILOAD && sv.var == 31)) continue;
                    AbstractInsnNode end = start;
                    while (end != null) {
                        if (end.getOpcode() == INVOKESTATIC && end instanceof MethodInsnNode mi &&
                            mi.owner.equals(OWNER) && mi.name.equals("close") &&
                            mi.desc.equals("(Lcom/mojang/blaze3d/vertex/BufferBuilder;)V")) break;
                        end = end.getNext();
                    }
                    if (end == null) throw new IllegalStateException("outer timing close() not found");
                    AbstractInsnNode beforeClose = previousReal(end);
                    InsnList repl = new InsnList();
                    repl.add(new VarInsnNode(ALOAD, 32));
                    repl.add(new VarInsnNode(ALOAD, 23));
                    repl.add(new VarInsnNode(FLOAD, 33));
                    repl.add(new InsnNode(FCONST_1));
                    repl.add(new InsnNode(FCONST_1));
                    repl.add(new InsnNode(FCONST_1));
                    repl.add(new LdcInsnNode(0.90f));
                    repl.add(new MethodInsnNode(INVOKESTATIC, OWNER, "quad",
                        "(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;FFFFF)V", false));
                    render.instructions.insertBefore(start, repl);
                    removeRange(render.instructions, start, beforeClose);
                    patchedOuter++;
                    break;
                }
            }
        }

        if (removedShadow != 2) throw new IllegalStateException("Expected 2 shadow removals, got " + removedShadow);
        if (patchedOuter != 1) throw new IllegalStateException("Expected 1 outer timing patch, got " + patchedOuter);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        Files.write(p, cw.toByteArray());
        System.out.println("PATCH_V9_OK removedShadow=" + removedShadow + " patchedOuter=" + patchedOuter);
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
