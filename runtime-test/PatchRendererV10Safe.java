import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public class PatchRendererV10Safe implements Opcodes {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("class path required");
        Path p = Paths.get(args[0]);
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(p)).accept(cn, 0);

        MethodNode quad = null;
        for (MethodNode m : cn.methods) {
            if (m.name.equals("quad") &&
                m.desc.equals("(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;FFFFF)V")) {
                quad = m;
                break;
            }
        }
        if (quad == null) throw new IllegalStateException("quad method not found");

        int replaced = 0;
        for (AbstractInsnNode n = quad.instructions.getFirst(); n != null; ) {
            AbstractInsnNode next = n.getNext();
            if (n instanceof VarInsnNode v && v.getOpcode() == FLOAD &&
                (v.var == 3 || v.var == 4 || v.var == 5)) {
                quad.instructions.set(n, new InsnNode(FCONST_1));
                replaced++;
            }
            n = next;
        }

        if (replaced != 12) throw new IllegalStateException("Expected 12 RGB loads, got " + replaced);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        Files.write(p, cw.toByteArray());
        System.out.println("PATCH_V10_SAFE_OK rgbLoadsReplaced=" + replaced);
    }
}
