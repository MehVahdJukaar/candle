package net.mehvahdjukaar.candlelight.core;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;

public class CandleLightPlugin implements Plugin<Project> {

    private static final String PREFIX = "[CANDLELIGHT] ";

    public static void log(Project project, String s) {
        project.getLogger().lifecycle(PREFIX + s);
    }
    @Override
    public void apply(Project project) {
        CandleLightExtension extension = project.getExtensions()
                .create("candlelight", CandleLightExtension.class);

        extension.getPlatformPackage().convention("platform");
        extension.getLogging().convention(true);

        project.getPlugins().withId("java", plugin -> {
            JavaCompile compileTask = (JavaCompile) project.getTasks().getByName("compileJava");
            var mainSourceSet = project.getExtensions().getByType(JavaPluginExtension.class)
                    .getSourceSets().getByName("main");

            // Define the transformation task
            var transformTask = project.getTasks().register("candleLightTransform", TransformClassesTask.class, t -> {
                t.getSourceDir().set(compileTask.getDestinationDirectory());
                t.getOutputDir().set(project.getLayout().getBuildDirectory().dir("transformed/classes"));
                t.getExtensionProperty().set(extension);
            });

            // Redirect the JAR task
            project.getTasks().named("jar", Jar.class, jar -> {
                // This removes the default classes directory from the Jar's root
                jar.exclude(element -> element.getFile().getAbsolutePath()
                        .contains(compileTask.getDestinationDirectory().get().getAsFile().getAbsolutePath()));

                // This adds your transformed classes
                jar.from(transformTask);
            });

        });
    }
}