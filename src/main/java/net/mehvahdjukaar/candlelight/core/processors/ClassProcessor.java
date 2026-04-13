package net.mehvahdjukaar.candlelight.core.processors;


import org.gradle.api.Project;

public interface ClassProcessor {

    byte[] transform(byte[] classBytes, Project project);
}
