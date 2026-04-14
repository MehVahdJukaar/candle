package net.mehvahdjukaar.candlelight.core;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;

public class CandleLightPlugin implements Plugin<Project> {

    private static final String CANDLE_LIGHT_PLUGIN_NAME = "[CANDLELIGHT]";

    public static void log(Project project, String s) {
        project.getLogger().lifecycle(CANDLE_LIGHT_PLUGIN_NAME + s);
    }

    @Override
    public void apply(Project project) {

        // Extension (non-static, per-project)
        CandleLightExtension extension =
                project.getExtensions().create("candlelight", CandleLightExtension.class);

        project.getPlugins().withId("java", plugin -> {

            project.getTasks().withType(JavaCompile.class).all(compileTask -> {
                if (!compileTask.getName().equals("compileJava")) return;

                String taskName = "transform" + capitalize(compileTask.getName());

                var transformTask = project.getTasks().register(taskName, TransformClassesTask.class,
                        task -> {

                            task.getSourceDir().set(compileTask.getDestinationDirectory());

                            task.getOutputDir().set(
                                    project.getLayout().getBuildDirectory()
                                            .dir("candlelight/" + compileTask.getName())
                            );

                            task.getExtensionProperty().set(extension);


                            // Ensure correct execution order
                            task.dependsOn(compileTask);
                        });

                // Replace compiled classes with transformed ones
                project.getExtensions()
                        .getByType(JavaPluginExtension.class)
                        .getSourceSets()
                        .named("main", sourceSet -> {

                            project.getTasks().withType(org.gradle.api.tasks.bundling.Jar.class).configureEach(jar -> {

                                jar.from(transformTask.flatMap(TransformClassesTask::getOutputDir));

                            });
                        });
            });
        });
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}