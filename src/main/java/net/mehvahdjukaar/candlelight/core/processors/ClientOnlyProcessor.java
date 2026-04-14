package net.mehvahdjukaar.candlelight.core.processors;

import net.mehvahdjukaar.candlelight.core.CandleLightExtension;
import net.mehvahdjukaar.candlelight.core.CandleLightPlugin;
import org.gradle.api.Project;
import org.objectweb.asm.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientOnlyProcessor implements ClassProcessor {

    private enum LoaderType {
        FABRIC("net.fabricmc.api.EnvType", "net.fabricmc.api.Environment"),
        FORGE("net.minecraftforge.api.distmarker.Dist", "net.minecraftforge.api.distmarker.OnlyIn"),
        NEOFORGE("net.neoforged.api.distmarker.Dist", "net.neoforged.api.distmarker.OnlyIn");

        final String envDesc;
        final String annotationDesc;

        LoaderType(String envClass, String annotationClass) {
            this.envDesc = "L" + envClass.replace('.', '/') + ";";
            this.annotationDesc = "L" + annotationClass.replace('.', '/') + ";";
        }

        static LoaderType infer(Project project) {
            String n = project.getName().toLowerCase();
            if (n.contains("fabric")) return FABRIC;
            if (n.contains("neoforge")) return NEOFORGE;
            if (n.contains("forge")) return FORGE;
            return null;
        }
    }

    private static final String CLIENT_ONLY = "Lnet/mehvahdjukaar/candlelight/api/ClientOnly;";

    @Override
    public List<String> usedAnnotations() {
        return List.of(CLIENT_ONLY);
    }

    @Override
    public boolean transform(ClassWriter cw, ClassReader cr, Project project, CandleLightExtension ext) {
        LoaderType loader = LoaderType.infer(project);
        if (loader == null) return false;

        AtomicBoolean changed = new AtomicBoolean(false);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            private String className;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                this.className = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                return wrap(super.visitAnnotation(desc, visible), desc, visible, "class " + className);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
                return new FieldVisitor(Opcodes.ASM9, fv) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        return wrap(super.visitAnnotation(desc, visible), desc, visible, "field " + name);
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        return wrap(super.visitAnnotation(desc, visible), desc, visible, "method " + name);
                    }
                };
            }

            private AnnotationVisitor wrap(AnnotationVisitor av, String desc, boolean visible, String context) {
                if (!CLIENT_ONLY.equals(desc)) return av;

                changed.set(true);
                if (ext.getLogging().getOrElse(true)) {
                    CandleLightPlugin.log(project, "[ClientOnly] Rewriting " + context + " in " + className + " -> " + loader.name());
                }

                // Replace the original annotation with the loader-specific one
                AnnotationVisitor newAv = cv.visitAnnotation(loader.annotationDesc, visible);
                newAv.visitEnum("value", loader.envDesc, "CLIENT");
                return null; // Swallow original @ClientOnly
            }
        };

        cr.accept(cv, 0);
        return changed.get();
    }
}