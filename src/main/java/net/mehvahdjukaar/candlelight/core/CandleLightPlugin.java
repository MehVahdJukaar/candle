package net.mehvahdjukaar.candlelight.core;

import net.mehvahdjukaar.candlelight.core.env.ClientOnlyTransformPlugin;
import net.mehvahdjukaar.candlelight.core.processors.CLAnnotationsPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CandleLightPlugin implements Plugin<Project> {

    private static final String LOG_NAME = "[CandleLight]";

    public static void log(Project project, String str) {
        if (extension != null && !extension.getLogging().get()) {
            return;
        }
        project.getLogger().lifecycle(LOG_NAME + str);
    }

    private static CandleLightExtension extension;

    @Override
    public void apply(Project project) {
        // Create the DSL extension
        extension = project.getExtensions()
                .create("candlelight", CandleLightExtension.class);

        CLAnnotationsPlugin.apply(project, extension);
        ClientOnlyTransformPlugin.apply(project);
        //ConventionalTagTransformerPlugin.apply(project);

    }


}
