package net.mehvahdjukaar.candlelight.core.processors;

import net.mehvahdjukaar.candlelight.core.ClassUtils;
import org.gradle.api.Project;
import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;
public class PlatImplProcessor implements ClassProcessor {

    private static final String FLAVOUR_ANNOTATION_DESC = ClassUtils.toDescriptor("net.mehvahdjukaar.candlelight.api.PlatformImpl");

    @Override
    public byte[] transform(byte[] classBytes, Project project) {

        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        final boolean[] modified = {false};

        cr.accept(new ClassVisitor(ASM9, cw) {
            private String className;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                className = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor originalMv = super.visitMethod(access, name, desc, sig, ex);

                return new MethodVisitor(ASM9, originalMv) {
                    boolean isFlavour;

                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationDesc, boolean visible) {
                        if (FLAVOUR_ANNOTATION_DESC.equals(annotationDesc)) {
                            isFlavour = true;
                            project.getLogger().lifecycle("[Candlelight]  Found @Flavour method: " +
                                    className.replace('/', '.') + "#" + name + desc);
                        }
                        return super.visitAnnotation(annotationDesc, visible);
                    }

                    @Override
                    public void visitCode() {
                        if (isFlavour) {
                            modified[0] = true;

                            String implInternalName = computeImplInternalName(className);
                            project.getLogger().lifecycle("[Candlelight]  Rewriting method to delegate to: " +
                                    implInternalName.replace('/', '.') + "#" + name + desc);

                            Type[] args = Type.getArgumentTypes(desc);
                            int idx = 0;
                            for (Type t : args) {
                                super.visitVarInsn(t.getOpcode(ILOAD), idx);
                                idx += t.getSize();
                            }

                            super.visitMethodInsn(INVOKESTATIC, implInternalName, name, desc, false);
                            super.visitInsn(ClassUtils.getReturnOpcode(desc));
                            super.visitMaxs(0, 0);
                            super.visitEnd();
                        } else {
                            super.visitCode();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return modified[0] ? cw.toByteArray() : classBytes;
    }

    private static String computeImplInternalName(String originalInternalName) {
        int lastSlash = originalInternalName.lastIndexOf('/');
        String pkg = lastSlash >= 0 ? originalInternalName.substring(0, lastSlash) : "";
        String simple = lastSlash >= 0 ? originalInternalName.substring(lastSlash + 1) : originalInternalName;

        return pkg.isEmpty()
                ? "platform/" + simple + "Impl"
                : pkg + "/platform/" + simple + "Impl";
    }
}

