package net.mehvahdjukaar.candlelight.core;

import net.mehvahdjukaar.candlelight.core.processors.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@CacheableTask
public abstract class TransformClassesTask extends DefaultTask {

    private static final List<ClassProcessor> PROCESSORS = List.of(
            new BeanConventionProcessor(),
            new OptionalInterfaceProcessor(),
            new PlatImplProcessor()
    );

    private static final List<String> OUR_ANNOTATIONS = PROCESSORS.stream()
            .flatMap(p -> p.usedAnnotations().stream())
            .distinct()
            .toList();


    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDir();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Input
    public abstract Property<CandleLightExtension> getExtensionProperty();

    @TaskAction
    public void execute() throws IOException {

        File inputDir = getSourceDir().get().getAsFile();
        File outputDir = getOutputDir().get().getAsFile();

        getProject().delete(outputDir);
        outputDir.mkdirs();
        CandleLightPlugin.log(getProject(), " processing annotations");
        ClassUtils.walkClasses(inputDir, file -> {
            try {
                String relative = inputDir.toPath().relativize(file.toPath()).toString();
                File outFile = new File(outputDir, relative);
                outFile.getParentFile().mkdirs();

                byte[] inputBytes = Files.readAllBytes(file.toPath());

                byte[] outputBytes = transform(inputBytes);

                if (outputBytes != null) {
                    CandleLightPlugin.log(getProject(), " processed: " + relative);
                }else outputBytes = inputBytes;
                Files.write(outFile.toPath(), outputBytes);


            } catch (IOException e) {
                throw new RuntimeException("Failed processing " + file, e);
            }
        });
    }

    private byte @Nullable [] transform(byte[] input) {

        // PASS 1: Lightweight Pre-Scan
        ClassReader scanReader = new ClassReader(input);
        PreScannerVisitor scanner = new PreScannerVisitor();

        // SKIP_CODE and SKIP_DEBUG make the scan even faster
        // because we only care about annotations
        scanReader.accept(scanner, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);

        if (!scanner.shouldTransform) {
            return null; // Exit early! No expensive ClassWriter work.
        }

        boolean changed = false;
        for (ClassProcessor processor : PROCESSORS) {
            ClassReader cr = new ClassReader(input);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

            boolean success = processor.transform(cw, cr, getProject(), getExtensionProperty().get()) ;

            if (success) {
                input = cw.toByteArray();
                changed = true;
            }
        }
        if (!changed) return null;

        return input;
    }

    private static class PreScannerVisitor extends ClassVisitor {
        private boolean shouldTransform = false;

        public PreScannerVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (OUR_ANNOTATIONS.contains(desc)) shouldTransform = true;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exc) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (OUR_ANNOTATIONS.contains(desc)) shouldTransform = true;
                    return null;
                }
            };
        }
    }
}