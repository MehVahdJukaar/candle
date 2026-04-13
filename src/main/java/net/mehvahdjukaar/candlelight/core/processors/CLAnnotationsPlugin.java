package net.mehvahdjukaar.candlelight.core.processors;

import net.mehvahdjukaar.candlelight.core.CandleLightExtension;
import net.mehvahdjukaar.candlelight.core.CandleLightPlugin;
import net.mehvahdjukaar.candlelight.core.ClassUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

@ApiStatus.Internal
public class CLAnnotationsPlugin {

    private static final List<ClassProcessor> PROCESSORS = List.of(
            new BeanConventionProcessor(),
            new OptionalInterfaceProcessor(),
            new PlatImplProcessor()
    );

    public static void apply(Project project, CandleLightExtension extension) {

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {

            task.doLast(t -> {

                File classesDir = task.getDestinationDirectory().get().getAsFile();
                String relativePath = project.relativePath(classesDir);
                CandleLightPlugin.log(project," Scanning classes in: " + relativePath
                );

                if (!classesDir.exists()) {
                    CandleLightPlugin.log(project," Classes directory does not exist: " + relativePath);
                    return;
                }

                try {
                    transformAll(classesDir, project, extension);
                } catch (Exception e) {
                    throw new GradleException("Candlelight bytecode transformation failed", e);
                }
            });
        });
    }

    private static void transformAll(File classesDir, Project project, CandleLightExtension ext) throws IOException {

        ClassUtils.walkClasses(classesDir, file -> {

            byte[] original;

            try {
                original = ClassUtils.readAllBytes(file);
            } catch (IOException e) {
                throw new RuntimeException("Failed reading class: " + file, e);
            }


            ClassReader cr = new ClassReader(original);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

            boolean changed = false;
            for (ClassProcessor processor : PROCESSORS) {
                boolean success = processor.transform(cw, cr, project, ext) ;
                changed = changed || success;
            }
            if (!changed) return;

            try {
                writeAtomic(file.toPath(), cw.toByteArray());

                CandleLightPlugin.log(project," Patched class: " + file.getName()
                );

            } catch (IOException e) {
                throw new RuntimeException("Failed writing class: " + file, e);
            }
        });
    }

    private static void writeAtomic(Path file, byte[] data) throws IOException {

        Path tmp = Files.createTempFile(file.getParent(), "cl_", ".class");

        Files.write(tmp, data, StandardOpenOption.WRITE);

        Files.move(tmp, file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }
}