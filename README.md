# Candlelight

A Gradle plugin which adds ASM operations useful in cross-platform Minecraft mods projects

Best used together with [Candlelight IDEA plugin](https://github.com/MehVahdJukaar/architectury-idea-plugin)

**Installation**

```groovy
repositories {
  maven { url = uri("https://registry.somethingcatchy.net/repository/maven-releases/") }
}
dependencies {
  compileOnly("net.mehvahdjukaar:candlelight:1.1.0")
}
```

## @PlatformImpl

Replaces annotated methods with platform-specific implementations at build time.

### How it works

- Methods annotated with `@PlatformImpl` have their body **removed** from the compiled class.
- They body is replaced with **generated delegating methods** which forward calls to a corresponding static method in a
  platform-specific implementation class.
- The target method must be placed in `<original_package>/platform/<ClassName>Impl`

### Usage example

**Common (shared) code:**

```java
public class Example {

    @PlatformImpl
    public static int getStaticValue(int x) {
        throw new AssertionError();
    }

    @PlatformImpl
    public int getInstanceValue(int x) {
        throw new AssertionError();
    }
}
```

**Platform specific implementation:**

```java
public class ExampleImpl {

    public static int getStaticValue(int x) {
        return x * 2;
    }

    public static int getInstanceValue(Example instance, int x) {
        return x + 10;
    }
}
```

## @OptionalInterface

### How it works

- Reads the interface name from `@OptionalInterface`
- If the class does not already implement it, the interface is **added to the class signature**
- No methods are generated or checked
- If the interface is not available at runtime this will actually crash. Use it together with mixins for more
  conditionality.

### Usage example

**Original code:**

```java
import net.mehvahdjukaar.candlelight.api.OptionalInterface;

@OptionalInterface("com.example.api.ExternalApi")
public class MyClass {
}
```

**Resulting bytecode:**

```java
import com.example.api.ExternalApi;

public class MyClass implements ExternalApi {
}
```

## @BeanAliases

Automatically generates JavaBean-style getter/setter aliases for methods.

### How it works

- Enabled by annotating a class with `@BeanAliases`
- The processor scans public methods and identifies:
    - **Getters** → no arguments, non-void return
    - **Setters** → one argument, void return
- For each valid method:
    - A new alias method is generated following JavaBean conventions:
        - `getX`, `isX`, or `setX`
    - The alias simply delegates to the original method
- Methods are skipped if:
    - Annotated with `@NoBeanAlias`
    - Already follow bean naming (`getX`, `isX`, `setX`, etc.)
    - An alias with the same name already exists

### Customization

- `@BeanAlias("prefix")` → overrides the default prefix
- `@NoBeanAlias` → excludes a method from alias generation

---

### Usage example

**Common code:**

```java
import net.mehvahdjukaar.candlelight.api.BeanAliases;

@BeanAliases
public class Example {

    public int value() {
        return 42;
    }

    public void value(int v) {
        // setter logic
    }

    public boolean active() {
        return true;
    }
}
```

**Generated methods:**

```java
public class Example {
    public int getValue() {
        return value();
    }

    public void setValue(int v) {
        value(v);
    }

    public boolean isActive() {
        return active();
    }

    public int value() {
        return 42;
    }

    public void value(int v) {
        // setter logic
    }

    public boolean active() {
        return true;
    }
}
```

## @VirtualOverride

Marker annotation. Does nothing on its own unless used with the Candlelight IDEA plugin.
Used to mark overrides of platform specific methods.

