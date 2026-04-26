package net.mehvahdjukaar.candlelight.core;

import net.mehvahdjukaar.candlelight.core.jars_processors.ClientOnlyTransformPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;

public class CandleLightPlugin implements Plugin<Project> {

    private static final String PREFIX = "[CANDLELIGHT] ";

    public static void log(Project project, String s) {
        project.getLogger().lifecycle(PREFIX + s);
    }

    @Override
    public void apply(Project project) {

        CandleLightExtension clExtension = project.getExtensions()
                .create("candlelight", CandleLightExtension.class);

        clExtension.getLogging().convention(true);
        clExtension.getClientOnly().convention(true);


        ClientOnlyTransformPlugin.apply(project, clExtension);


        project.getPlugins().withId("java", plugin -> {

            JavaCompile compileTask =
                    (JavaCompile) project.getTasks().getByName("compileJava");

            // =====================================
            // 1. MOVE compile output FIRST (safe)
            // =====================================
            Provider<Directory> rawDir =
                    project.getLayout().getBuildDirectory().dir("raw/classes");

            Provider<Directory> finalDir =
                    project.getLayout().getBuildDirectory().dir("classes/java/main");

            compileTask.getDestinationDirectory().set(rawDir);

            // =====================================
            // 2. TRANSFORM TASK (no cycle)
            // =====================================
            var transformTask = project.getTasks().register(
                    "candleLightTransform",
                    TransformClassesTask.class,
                    t -> {

                        // read compiled raw output
                        t.getSourceDir().set(rawDir);

                        // write FINAL runtime output
                        t.getOutputDir().set(finalDir);

                        t.getExtensionProperty().set(clExtension);

                        t.dependsOn(compileTask);
                    }
            );

            // =====================================
            // 3. ensure ordering
            // =====================================
            project.getTasks().named("classes", task -> {
                task.dependsOn(transformTask);
            });

            project.getTasks().configureEach(task -> {
                String taskName = task.getName();
                if (taskName.contains("remapSourcesJar") || taskName.contains("remapJar")) {
                    task.dependsOn(transformTask);
                    project.getRootProject().getChildProjects().values().forEach(p -> {
                        if (p.getName().equals("common")) {
                            task.dependsOn(p.getTasks().named("candleLightTransform"));
                        }
                    });
                }
                if (taskName.equals("copyAccessTransformersPublications")) {
                    task.dependsOn(project.getTasks().named("transformAccessWidener"));
                }
                if (taskName.equals("curseforge")) {
                    task.dependsOn(project.getTasks().named("jar"));
                }
            });

            if (project.getName().equals("fabric")) {

                project.getTasks().named("compileJava", t -> {
                    t.dependsOn(
                            project.project(":common")
                                    .getTasks()
                                    .named("candleLightTransform")
                    );
                });
            }

            if (project.getName().equals("neoforge")) {

                project.getTasks().named("compileJava", t -> {
                    t.dependsOn(
                            project.project(":common")
                                    .getTasks()
                                    .named("candleLightTransform")
                    );
                });
            }

        });
    }
}