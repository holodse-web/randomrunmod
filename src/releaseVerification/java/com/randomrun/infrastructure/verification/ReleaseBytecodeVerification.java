package com.randomrun.infrastructure.verification;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.jar.JarFile;

/** Verifies the packaged release without initializing Minecraft or the mod. */
public final class ReleaseBytecodeVerification {
    public static void main(String[] args) throws Exception {
        Path release = Path.of(args[0]).toRealPath();
        ClassLoader loader = ReleaseBytecodeVerification.class.getClassLoader();
        ClassFile verifier = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.ofResourceParsing(loader).cached()));
        var failures = new ArrayList<String>();
        int classes = 0;
        try (JarFile jar = new JarFile(release.toFile())) {
            for (var entry : jar.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                classes++;
                try (var in = jar.getInputStream(entry)) {
                    var errors = verifier.verify(in.readAllBytes());
                    if (!errors.isEmpty()) {
                        failures.add(entry.getName() + ": " + errors.size() + " verification error(s); "
                            + errors.getFirst().getMessage());
                    }
                }
            }
        }
        if (classes == 0) throw new AssertionError("Release contains no classes: " + release);
        if (!failures.isEmpty()) {
            failures.stream().limit(10).forEach(System.err::println);
            throw new AssertionError("Invalid release bytecode in " + failures.size() + "/" + classes + " classes");
        }

        // Force the JVM to verify the same entrypoint Fabric loads at startup.
        Class<?> entrypoint = Class.forName("com.randomrun.infrastructure.RandomRunMod", false, loader);
        Path loadedFrom = Path.of(entrypoint.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if (!loadedFrom.equals(release)) {
            throw new AssertionError("Entrypoint was loaded from " + loadedFrom + " instead of " + release);
        }
        entrypoint.getDeclaredMethods();
        entrypoint.getDeclaredConstructors();
        System.out.println("RELEASE_BYTECODE_VERIFIED: " + classes + " classes; JVM entrypoint verified; " + release);
    }
}
