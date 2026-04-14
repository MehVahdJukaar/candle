package net.mehvahdjukaar.candlelight.core;

import org.gradle.api.file.RegularFileProperty;

import org.gradle.api.provider.Property;

public abstract class CandleLightExtension {

    // Constructor to set default values (conventions)
    public CandleLightExtension() {
        getPlatformPackage().convention("platform");
        getLogging().convention(true);
    }

    public abstract Property<String> getPlatformPackage();

    public abstract Property<Boolean> getLogging();
}