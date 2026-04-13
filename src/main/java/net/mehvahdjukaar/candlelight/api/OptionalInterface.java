package net.mehvahdjukaar.candlelight.api;

import java.lang.annotation.*;

@Retention(RetentionPolicy.CLASS) // important: visible in .class, not runtime required
@Target(ElementType.TYPE)
public @interface OptionalInterface {
    String value(); // fully-qualified interface name
}