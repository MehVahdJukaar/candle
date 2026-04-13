package net.mehvahdjukaar.candlelight.core;

import net.mehvahdjukaar.candlelight.core.env.ClientOnlyTransformPlugin;
import net.mehvahdjukaar.candlelight.core.processors.CLAnnotationsPlugin;
import net.mehvahdjukaar.candlelight.core.tags.ConventionalTagTransformerPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CandleLightPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Create the DSL extension
        CandleLightExtension extension = project.getExtensions()
                .create("candlelight", CandleLightExtension.class);

        CLAnnotationsPlugin.apply(project, extension);
        ClientOnlyTransformPlugin.apply(project);
        //ConventionalTagTransformerPlugin.apply(project);

    }


}
