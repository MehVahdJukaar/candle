package net.mehvahdjukaar.candlelight.core;

import net.mehvahdjukaar.candlelight.core.processors.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@CacheableTask
public abstract class TransformClassesTask extends DefaultTask {

    private static final List<ClassProcessor> PROCESSORS = List.of(
            new BeanConventionProcessor(),
            new OptionalInterfaceProcessor(),
            new PlatImplProcessor()
    );

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
}