package net.mehvahdjukaar.candlelight.core.processors;

import net.mehvahdjukaar.candlelight.core.CandleLightExtension;
import net.mehvahdjukaar.candlelight.core.CandleLightPlugin;
import net.mehvahdjukaar.candlelight.core.ClassUtils;
import org.gradle.api.Project;
import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;
public class PlatImplProcessor implements ClassProcessor {

    private static final String FLAVOUR_ANNOTATION_DESC = ClassUtils.toDescriptor("net.mehvahdjukaar.candlelight.api.PlatformImpl");

    @Override
    public boolean transform(ClassWriter writer, ClassReader reader, Project project, CandleLightExtension ext) {

        final boolean[] modified = {false};

        reader.accept(new ClassVisitor(ASM9, writer) {
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
                            CandleLightPlugin.log(project," Found @PlatformImpl method: " +
                                    className.replace('/', '.') + "#" + name + desc);
                        }
                        return super.visitAnnotation(annotationDesc, visible);
                    }

                    @Override
                    public void visitCode() {
                        if (isFlavour) {
                            modified[0] = true;

                            String platPackage = ext.getPlatformPackage().get();
                            String implInternalName = computeImplInternalName(className,
                                    platPackage);
                            CandleLightPlugin.log(project,"  Rewriting method to delegate to: " +
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

        return modified[0];
    }

    private static String computeImplInternalName(String originalInternalName, String platFolder) {
        int lastSlash = originalInternalName.lastIndexOf('/');
        String pkg = lastSlash >= 0 ? originalInternalName.substring(0, lastSlash) : "";
        String simple = lastSlash >= 0 ? originalInternalName.substring(lastSlash + 1) : originalInternalName;

        return pkg.isEmpty()
                ? platFolder+"/" + simple + "Impl"
                : pkg + "/"+platFolder+"/" + simple + "Impl";
    }
}

