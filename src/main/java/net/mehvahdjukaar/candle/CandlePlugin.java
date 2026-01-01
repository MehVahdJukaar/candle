package net.mehvahdjukaar.candle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.objectweb.asm.*;

import java.io.*;
import java.util.*;

/**
 * Candle Gradle plugin - ASM-based bytecode transformer.
 *
 * - Adds getters for zero-arg instance methods in classes annotated with @GenerateGetters
 * - Replaces methods annotated with @Flavour (static methods in common) by INVOKESTATIC
 *   calls to flavour implementation classes in the flavour package.
 *
 * Configuration:
 *   - Set project property `candle.flavour=yourFlavour` for flavoured modules,
 *     or leave unset to default to "vanilla".
 *
 * Important constraints:
 *  - Methods annotated with @Flavour MUST be static. The scan will throw if not.
 *  - The implementation method must be a static method with the exact same descriptor.
 */
public class CandlePlugin implements Plugin<Project> {

    private static final String ANNO_GENERATE_GETTERS = "Lnet/mehvahdjukaar/candle/api/GenerateGetters;";
    private static final String ANNO_FLAVOUR = "Lnet/mehvahdjukaar/candle/api/Flavour;";

    @Override
    public void apply(Project project) {
        // flavour config: project property or gradle.properties -> candle.flavour
        String flavour = (String) project.findProperty("candle.flavour");
        if (flavour == null || flavour.isBlank()) flavour = "vanilla";
        final String flavourName = flavour;

        project.getTasks().withType(JavaCompile.class).configureEach(compileTask -> {
            compileTask.doLast(task -> {
                File classesDir = compileTask.getDestinationDirectory().get().getAsFile();
                project.getLogger().lifecycle("[Candle] transform classes in: " + classesDir);
                try {
                    transformClasses(classesDir, flavourName, project);
                } catch (IOException e) {
                    throw new RuntimeException("Candle plugin failed to transform classes", e);
                }
            });
        });
    }

    /**
     * Top-level transform: two passes per class file.
     */
    private void transformClasses(File classesDir, String flavour, Project project) throws IOException {
        if (!classesDir.exists()) return;

        walkClasses(classesDir, file -> {
            // read bytes
            byte[] original = readAllBytes(file);

            // First pass: collect metadata (class internal name, flags, methods to act on)
            ClassScanResult scan = scanClass(original, file);

            // If nothing to do (no GenerateGetters, no Flavour methods), skip
            if (!scan.hasGenerateGetters && scan.flavourMethods.isEmpty()) {
                return;
            }

            // Possibly create/locate impl class path for flavour usage:
            String implInternalName = null;
            if (!scan.flavourMethods.isEmpty()) {
                implInternalName = computeImplInternalName(scan.internalName, flavour);
                // check existence of impl class file on disk: classesDir + implInternalName + ".class"
                File implFile = new File(classesDir, implInternalName + ".class");
                if (!implFile.exists()) {
                    project.getLogger().warn("[Candle] flavour impl not found for {} -> {} (skipping flavour hooks)",
                            scan.internalName.replace('/', '.'), implInternalName.replace('/', '.'));
                    // if impl not found, clear flavourMethods to avoid trying to inject
                    scan.flavourMethods.clear();
                } else {
                    project.getLogger().lifecycle("[Candle] will wire flavour impl: " + implInternalName.replace('/', '.'));
                }
            }

            // Second pass: transform class as needed
            byte[] modified = transformClass(original, scan, implInternalName, project);

            // If class modified (different bytes), write back
            if (!Arrays.equals(original, modified)) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(modified);
                }
                project.getLogger().lifecycle("[Candle] wrote modified class: " + file.getAbsolutePath());
            }
        });
    }

    /**
     * First-pass scan: uses ASM to collect:
     * - internal class name
     * - whether class has @GenerateGetters
     * - existing method names
     * - candidate methods to create getters for (zero-arg, non-static, non-void)
     * - methods annotated with @Flavour (name+descriptor) and validate they are static
     */
    private ClassScanResult scanClass(byte[] classBytes, File fileForContext) {
        ClassReader cr = new ClassReader(classBytes);
        final ClassScanResult result = new ClassScanResult();

        ClassVisitor scanner = new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                result.internalName = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (ANNO_GENERATE_GETTERS.equals(descriptor)) {
                    result.hasGenerateGetters = true;
                }
                return super.visitAnnotation(descriptor, visible);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                // record method presence
                result.existingMethods.add(name);

                // collect getter candidates: non-static, not ctor/clinit, zero-arg, non-void
                if ((access & Opcodes.ACC_STATIC) == 0
                        && !name.equals("<init>") && !name.equals("<clinit>")
                        && descriptor.startsWith("()")
                        && Type.getReturnType(descriptor).getSort() != Type.VOID) {
                    result.getterCandidates.add(new MethodData(name, descriptor));
                }

                // detect @Flavour on method using a MethodVisitor
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    private boolean methodHasFlavour = false;

                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (ANNO_FLAVOUR.equals(desc)) {
                            methodHasFlavour = true;
                        }
                        return super.visitAnnotation(desc, visible);
                    }

                    @Override
                    public void visitEnd() {
                        if (methodHasFlavour) {
                            // validate: @Flavour MUST be static
                            if ((access & Opcodes.ACC_STATIC) == 0) {
                                throw new IllegalStateException(
                                        "[Candle] @Flavour method must be static: " +
                                                result.internalName.replace('/', '.') + "#" + name +
                                                " (file: " + fileForContext.getAbsolutePath() + ")"
                                );
                            }
                            // also validate not constructor/clinit
                            if (name.equals("<init>") || name.equals("<clinit>")) {
                                throw new IllegalStateException(
                                        "[Candle] @Flavour cannot be applied to constructors or static initializers: " +
                                                result.internalName.replace('/', '.') + "#" + name
                                );
                            }
                            // add to flavourMethods (any signature allowed)
                            result.flavourMethods.add(new MethodData(name, descriptor));
                        }
                        super.visitEnd();
                    }
                };
            }
        };

        cr.accept(scanner, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }

    /**
     * Second-pass transform: produce new class bytes:
     * - keep original methods except:
     *   * methods annotated @Flavour -> replace body with INVOKESTATIC implClass.method(...)
     * - at visitEnd, add getters for getterCandidates (if not already present)
     */
    private byte[] transformClass(byte[] originalBytes, ClassScanResult scan, String implInternalName, Project project) {
        ClassReader cr = new ClassReader(originalBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        final boolean[] modified = {false};

        ClassVisitor transformer = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                // If this method is one that needs to be replaced by flavour impl:
                boolean isFlavour = containsMethod(scan.flavourMethods, name, descriptor);
                if (isFlavour && implInternalName != null) {
                    // ensure method is static (scan validated earlier)
                    modified[0] = true;
                    // Replace method body: write method with same signature and invoke static impl
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();

                    // load method args from local vars (static => start at 0)
                    Type[] args = Type.getArgumentTypes(descriptor);
                    int varIndex = 0; // static
                    for (Type t : args) {
                        mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), varIndex);
                        varIndex += t.getSize();
                    }

                    // invoke static impl: implInternalName.name descriptor
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, implInternalName, name, descriptor, false);

                    // return
                    mv.visitInsn(getReturnOpcode(descriptor));
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    return null; // we've already written method body; no further visiting
                }

                // Otherwise copy method as-is
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                // if class has @GenerateGetters, add getters for getterCandidates if not already present
                if (scan.hasGenerateGetters) {
                    for (MethodData cand : scan.getterCandidates) {
                        String getterName = "get" + Character.toUpperCase(cand.name.charAt(0)) + cand.name.substring(1);
                        if (scan.existingMethods.contains(getterName)) continue; // already exists
                        // add getter that calls the original instance method
                        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, getterName, cand.descriptor, null, null);
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, scan.internalName, cand.name, cand.descriptor, false);
                        mv.visitInsn(getReturnOpcode(cand.descriptor));
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();
                        modified[0] = true;
                        // add to existingMethods to avoid duplicates if multiple transforms
                        scan.existingMethods.add(getterName);
                    }
                }
                super.visitEnd();
            }
        };

        cr.accept(transformer, 0);
        if (modified[0]) {
            return cw.toByteArray();
        } else {
            return originalBytes;
        }
    }

    private static boolean containsMethod(List<MethodData> list, String name, String descriptor) {
        for (MethodData m : list) {
            if (m.name.equals(name) && m.descriptor.equals(descriptor)) return true;
        }
        return false;
    }

    private static String computeImplInternalName(String internalClassName, String flavour) {
        // internalClassName example: net/mehvahdjukaar/polytone/common/Test
        int idx = internalClassName.lastIndexOf('/');
        String pkg = (idx >= 0) ? internalClassName.substring(0, idx) : "";
        String simple = (idx >= 0) ? internalClassName.substring(idx + 1) : internalClassName;
        // impl: pkg + '/' + flavour + '/' + simple + 'Impl'
        if (pkg.isEmpty()) {
            return flavour + "/" + simple + "Impl";
        } else {
            return pkg + "/" + flavour + "/" + simple + "Impl";
        }
    }

    private static int getReturnOpcode(String descriptor) {
        int sort = Type.getReturnType(descriptor).getSort();
        return switch (sort) {
            case Type.VOID -> Opcodes.RETURN;
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Opcodes.IRETURN;
            case Type.LONG -> Opcodes.LRETURN;
            case Type.FLOAT -> Opcodes.FRETURN;
            case Type.DOUBLE -> Opcodes.DRETURN;
            case Type.ARRAY, Type.OBJECT -> Opcodes.ARETURN;
            default -> throw new IllegalStateException("Unsupported return type: " + descriptor);
        };
    }

    private static void walkClasses(File dir, ClassFileConsumer consumer) throws IOException {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) walkClasses(f, consumer);
            else if (f.getName().endsWith(".class")) consumer.accept(f);
        }
    }

    private static byte[] readAllBytes(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return in.readAllBytes();
        }
    }

    /* ------------ small helper types ------------ */

    private static final class ClassScanResult {
        String internalName;
        boolean hasGenerateGetters = false;
        final List<MethodData> getterCandidates = new ArrayList<>();
        final List<MethodData> flavourMethods = new ArrayList<>();
        final Set<String> existingMethods = new HashSet<>();
    }

    private record MethodData(String name, String descriptor) {
    }

    private interface ClassFileConsumer {
        void accept(File file) throws IOException;
    }
}
