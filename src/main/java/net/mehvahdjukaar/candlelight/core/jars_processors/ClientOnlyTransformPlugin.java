package net.mehvahdjukaar.candlelight.core.jars_processors;

import net.mehvahdjukaar.candlelight.core.CandleLightExtension;
import net.mehvahdjukaar.candlelight.core.CandleLightPlugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;
import org.objectweb.asm.*;

import java.io.File;
import java.nio.file.*;


public class ClientOnlyTransformPlugin {

    private static final String CLIENT_ONLY =
            "Lnet/mehvahdjukaar/candlelight/api/ClientOnly;";

    private enum LoaderType {

        FABRIC("net.fabricmc.api.EnvType",
                "net.fabricmc.api.Environment"),

        FORGE("net.minecraftforge.api.distmarker.Dist",
                "net.minecraftforge.api.distmarker.OnlyIn"),

        NEOFORGE("net.neoforged.api.distmarker.Dist",
                "net.neoforged.api.distmarker.OnlyIn");

        final String valueDesc;
        final String annotationDesc;

        LoaderType(String valueClass, String annotationClass) {
            this.valueDesc = "L" + valueClass.replace('.', '/') + ";";
            this.annotationDesc = "L" + annotationClass.replace('.', '/') + ";";
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

            CandleLightPlugin.log(project,
                    "[ClientOnly] Hooked into jar task: " + jar.getName()
            );

            jar.doFirst(task -> {

                LoaderType loader = LoaderType.infer(project.getName());
                if (loader == null) return;

                File classesDir = new File(project.getBuildDir(), "classes/java/main");

                if (!classesDir.exists()) {
                    CandleLightPlugin.log(project,
                            "[ClientOnly] No classes dir: " + classesDir
                    );
                    return;
                }

                CandleLightPlugin.log(project,
                        "[ClientOnly] Transforming classes for loader: " + loader.name()
                );

                try {
                    transformClasses(classesDir.toPath(), loader, project);
                } catch (Exception e) {
                    throw new RuntimeException("ClientOnly transform failed", e);
                }
            });
        });
    }

    private static void transformClasses(Path dir,
                                         LoaderType loader,
                                         Project project) throws Exception {

        Files.walk(dir)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(classFile -> {

                    try {
                        byte[] original = Files.readAllBytes(classFile);

                        ClassReader reader = new ClassReader(original);
                        ClassWriter writer = new ClassWriter(reader, 0);

                        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {

                            private String className;

                            @Override
                            public void visit(int version, int access, String name,
                                              String signature, String superName, String[] interfaces) {
                                className = name;
                                super.visit(version, access, name, signature, superName, interfaces);
                            }

                            @Override
                            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                return transformAnnotation(desc, visible, "class " + className);
                            }

                            @Override
                            public FieldVisitor visitField(int access, String name,
                                                           String descriptor, String signature, Object value) {
                                FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);

                                return new FieldVisitor(Opcodes.ASM9, fv) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                        return transformAnnotation(desc, visible,
                                                "field " + name + " in " + className);
                                    }
                                };
                            }

                            @Override
                            public MethodVisitor visitMethod(int access, String name,
                                                             String descriptor, String signature, String[] exceptions) {

                                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                                return new MethodVisitor(Opcodes.ASM9, mv) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                        return transformAnnotation(desc, visible,
                                                "method " + name + " in " + className);
                                    }
                                };
                            }

                            private AnnotationVisitor transformAnnotation(String desc,
                                                                          boolean visible,
                                                                          String context) {

                                if (!CLIENT_ONLY.equals(desc)) {
                                    return super.visitAnnotation(desc, visible);
                                }

                                CandleLightPlugin.log(project,
                                        "[ClientOnly] Rewriting annotation in " + context +
                                                " → " + loader.name()
                                );

                                AnnotationVisitor av =
                                        super.visitAnnotation(loader.annotationDesc, visible);

                                String env = "Lnet/minecraftforge/api/distmarker/Dist;";

                                if (loader == LoaderType.FABRIC) {
                                    env = "Lnet/fabricmc/api/EnvType;";
                                }

                                av.visitEnum("value", env, "CLIENT");

                                return av;
                            }
                        };

                        reader.accept(visitor, 0);

                        byte[] transformed = writer.toByteArray();

                        if (!java.util.Arrays.equals(original, transformed)) {

                            writeAtomic(classFile, transformed);

                            CandleLightPlugin.log(project,
                                    "[ClientOnly] patched " +
                                            project.relativePath(classFile)
                            );
                        }

                    } catch (Exception e) {
                        throw new RuntimeException("Failed class: " + classFile, e);
                    }
                });
    }

    private static void writeAtomic(Path file, byte[] data) throws Exception {

        Path tmp = Files.createTempFile(file.getParent(), "cl_", ".class");

        Files.write(tmp, data,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        try {
            Files.move(tmp, file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}