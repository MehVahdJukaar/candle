package net.mehvahdjukaar.candlelight.core.jars_processors;

import net.mehvahdjukaar.candlelight.core.CandleLightExtension;
import net.mehvahdjukaar.candlelight.core.CandleLightPlugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;
import org.objectweb.asm.*;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientOnlyTransformPlugin {

    private static final String CLIENT_ONLY = "Lnet/mehvahdjukaar/candlelight/api/ClientOnly;";

    private enum LoaderType {
        FABRIC("net.fabricmc.api.EnvType", "net.fabricmc.api.Environment",
                "Lnet/fabricmc/api/EnvType;", "CLIENT"),
        FORGE("net.minecraftforge.api.distmarker.Dist", "net.minecraftforge.api.distmarker.OnlyIn",
                "Lnet/minecraftforge/api/distmarker/Dist;", "CLIENT"),
        NEOFORGE("net.neoforged.api.distmarker.Dist", "net.neoforged.api.distmarker.OnlyIn",
                "Lnet/neoforged/api/distmarker/Dist;", "CLIENT");

        final String annotationDesc;
        final String enumValueDesc;
        final String enumConstantName;

        LoaderType(String enumClass, String annotationClass, String enumValueDesc, String enumConstantName) {
            this.annotationDesc = "L" + annotationClass.replace('.', '/') + ";";
            this.enumValueDesc = enumValueDesc;
            this.enumConstantName = enumConstantName;
        }

        static LoaderType infer(String projectName) {
            String n = projectName.toLowerCase();
            if (n.contains("fabric")) return FABRIC;
            if (n.contains("neoforge")) return NEOFORGE;
            if (n.contains("forge")) return FORGE;
            return null;
        }
    }

    public static void apply(Project project, CandleLightExtension ext) {
        project.getTasks().withType(Jar.class).configureEach(jar -> {
            if (!ext.getClientOnly().get()) return;
            if (!jar.getName().equals("jar") && !jar.getName().equals("remapJar")) return;

            jar.doFirst(task -> {
                LoaderType loader = LoaderType.infer(project.getName());
                if (loader == null) return;

                File classesDir = new File(project.getBuildDir(), "classes/java/main");
                if (!classesDir.exists()) return;

                try {
                    transformClasses(classesDir.toPath(), loader, project);
                } catch (Exception e) {
                    throw new RuntimeException("ClientOnly transform failed", e);
                }
            });
        });
    }

    private static void transformClasses(Path dir, LoaderType loader, Project project) throws Exception {
        Files.walk(dir)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(classFile -> {
                    try {
                        byte[] original = Files.readAllBytes(classFile);
                        ClassReader reader = new ClassReader(original);
                        ClassWriter writer = new ClassWriter(reader, 0);

                        AtomicBoolean modified = new AtomicBoolean(false);

                        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override
                            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                if (CLIENT_ONLY.equals(desc)) {
                                    modified.set(true);
                                    // Create the loader-specific annotation
                                    AnnotationVisitor newAv = super.visitAnnotation(loader.annotationDesc, visible);
                                    return new AnnotationVisitor(Opcodes.ASM9, newAv) {
                                        @Override
                                        public void visitEnd() {
                                            // Add the required enum value
                                            newAv.visitEnum("value", loader.enumValueDesc, loader.enumConstantName);
                                            super.visitEnd();
                                        }
                                        // Ignore any original annotation attributes (there are none)
                                        @Override public void visit(String name, Object value) {}
                                        @Override public void visitEnum(String name, String desc, String value) {}
                                        @Override public AnnotationVisitor visitAnnotation(String name, String desc) { return null; }
                                        @Override public AnnotationVisitor visitArray(String name) { return null; }
                                    };
                                }
                                // Preserve all other annotations
                                return super.visitAnnotation(desc, visible);
                            }

                            @Override
                            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                                FieldVisitor fv = super.visitField(access, name, desc, signature, value);
                                return new FieldVisitor(Opcodes.ASM9, fv) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                        if (CLIENT_ONLY.equals(desc)) {
                                            modified.set(true);
                                            AnnotationVisitor newAv = super.visitAnnotation(loader.annotationDesc, visible);
                                            return new AnnotationVisitor(Opcodes.ASM9, newAv) {
                                                @Override
                                                public void visitEnd() {
                                                    newAv.visitEnum("value", loader.enumValueDesc, loader.enumConstantName);
                                                    super.visitEnd();
                                                }
                                                @Override public void visit(String name, Object value) {}
                                                @Override public void visitEnum(String name, String desc, String value) {}
                                                @Override public AnnotationVisitor visitAnnotation(String name, String desc) { return null; }
                                                @Override public AnnotationVisitor visitArray(String name) { return null; }
                                            };
                                        }
                                        return super.visitAnnotation(desc, visible);
                                    }
                                };
                            }

                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                                return new MethodVisitor(Opcodes.ASM9, mv) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                        if (CLIENT_ONLY.equals(desc)) {
                                            modified.set(true);
                                            AnnotationVisitor newAv = super.visitAnnotation(loader.annotationDesc, visible);
                                            return new AnnotationVisitor(Opcodes.ASM9, newAv) {
                                                @Override
                                                public void visitEnd() {
                                                    newAv.visitEnum("value", loader.enumValueDesc, loader.enumConstantName);
                                                    super.visitEnd();
                                                }
                                                @Override public void visit(String name, Object value) {}
                                                @Override public void visitEnum(String name, String desc, String value) {}
                                                @Override public AnnotationVisitor visitAnnotation(String name, String desc) { return null; }
                                                @Override public AnnotationVisitor visitArray(String name) { return null; }
                                            };
                                        }
                                        return super.visitAnnotation(desc, visible);
                                    }
                                };
                            }
                        };

                        reader.accept(visitor, 0);
                        byte[] transformed = writer.toByteArray();

                        if (modified.get()) {
                            writeAtomic(classFile, transformed);
                            CandleLightPlugin.log(project,
                                    "[ClientOnly] Modified " + project.relativePath(classFile) + " (" + loader.name() + ")"
                            );
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed class: " + classFile, e);
                    }
                });
    }

    private static void writeAtomic(Path file, byte[] data) throws Exception {
        Path tmp = Files.createTempFile(file.getParent(), "cl_", ".class");
        Files.write(tmp, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}