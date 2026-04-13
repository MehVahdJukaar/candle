package net.mehvahdjukaar.candlelight.core.processors;

import net.mehvahdjukaar.candlelight.core.ClassUtils;
import org.gradle.api.Project;
import org.objectweb.asm.*;

import java.util.Arrays;

public class OptionalInterfaceProcessor implements ClassProcessor {
    private static final String ANNOTATION_DESC =
            ClassUtils.toDescriptor("net.mehvahdjukaar.candlelight.api.OptionalInterface");

    @Override
    public byte[] transform(byte[] input, Project project) {

        ClassReader reader = new ClassReader(input);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {

            private String iface;

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {

                if (desc.equals(ANNOTATION_DESC)) {

                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            iface = (String) value;
                        }
                    };
                }

                return super.visitAnnotation(desc, visible);
            }

            @Override
            public void visit(int version,
                              int access,
                              String name,
                              String signature,
                              String superName,
                              String[] interfaces) {

                if (iface != null) {
                    if (interfaces == null) {
                        interfaces = new String[0];
                    }
                    String[] newInterfaces =
                            Arrays.copyOf(interfaces, interfaces.length + 1);

                    newInterfaces[interfaces.length] =
                            iface.replace('.', '/');

                    interfaces = newInterfaces;
                }

                super.visit(version, access, name, signature, superName, interfaces);
            }
        };

        reader.accept(visitor, 0);
        return writer.toByteArray();
    }
}
