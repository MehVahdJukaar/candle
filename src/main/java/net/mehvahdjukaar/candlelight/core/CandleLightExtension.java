package net.mehvahdjukaar.candlelight.core;

import org.gradle.api.file.RegularFileProperty;

import org.gradle.api.provider.Property;

public abstract class CandleLightExtension {
    public abstract Property<String> getPlatformPackage();
    public abstract Property<Boolean> getLogging();
}