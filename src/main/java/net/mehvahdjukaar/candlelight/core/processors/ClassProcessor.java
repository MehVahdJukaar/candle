package net.mehvahdjukaar.candlelight.core.processors;


import net.mehvahdjukaar.candlelight.core.CandleLightExtension;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.util.List;

public interface ClassProcessor {

    boolean transform(ClassWriter classWriter, ClassReader reader, Project project, CandleLightExtension ext);

    List<String> usedAnnotations();
}
