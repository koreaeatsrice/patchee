package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Proves the guard reflects on names the real GregTech jar actually has.
 *
 * <p>
 * Every expected name comes from {@link PollutionVisuals#RENDER_FIELDS},
 * {@link PollutionVisuals#PROXY_FIELDS} and {@link PollutionVisuals#MATCHER_FIELDS},
 * and the matcher type comes from {@link PollutionVisualsGuard#MATCHER_CLASS}, so
 * a rename on either side fails here instead of failing silently in a client log.
 * GregTech is not on the test classpath, so this reads the jar's class files with
 * ASM instead of loading them.
 *
 * <p>
 * Set {@code PATCHEE_GREGTECH_JAR} to a real GregTech jar to run it; unset, the
 * test is skipped with an assumption, so a plain build never needs the jar.
 */
class PollutionVisualsRealJarTest {

    /** Set to a real GregTech jar to run these checks; skipped otherwise. */
    private static final String REAL_JAR_ENV = "PATCHEE_GREGTECH_JAR";

    private static final String CLIENT_RENDER_CLASS = "gregtech/common/config/Client$Render";
    private static final String GT_PROXY_CLASS = "gregtech/common/GTProxy";
    private static final String POLLUTION_CLASS = "gregtech/common/pollution/Pollution";

    /** The field type the five tint tables must have, taken from the guard's own constant. */
    private static final String MATCHER_DESCRIPTOR = "L" + PollutionVisualsGuard.MATCHER_CLASS.replace('.', '/') + ";";

    @Test
    void theRealJarCarriesEveryFieldTheGuardWrites() throws Exception {
        String jar = System.getenv(REAL_JAR_ENV);
        Assumptions.assumeTrue(
            jar != null && Files.isRegularFile(Paths.get(jar)),
            "set " + REAL_JAR_ENV + " to a real GregTech jar to run the real-jar field checks");

        try (ZipFile zip = new ZipFile(jar)) {
            for (String name : PollutionVisuals.RENDER_FIELDS) {
                assertBooleanField(zip, jar, CLIENT_RENDER_CLASS, name);
            }
            // The proxy mirrors live on GTProxy itself on the shipped jar, but the
            // guard walks superclasses, so this accepts an inherited field too.
            for (String name : PollutionVisuals.PROXY_FIELDS) {
                assertBooleanField(zip, jar, GT_PROXY_CLASS, name);
            }
            for (String name : PollutionVisuals.MATCHER_FIELDS) {
                String declaredIn = declaringClass(zip, jar, POLLUTION_CLASS, name);
                if (declaredIn == null) {
                    fail(
                        "real GregTech jar " + jar
                            + " has no field "
                            + name
                            + " on "
                            + POLLUTION_CLASS
                            + " (or its superclasses) - PollutionVisuals.MATCHER_FIELDS no longer matches GregTech");
                }
                assertEquals(
                    MATCHER_DESCRIPTOR,
                    classInfo(zip, jar, declaredIn).fields.get(name),
                    "real GregTech jar " + jar
                        + " declares "
                        + name
                        + " on "
                        + declaredIn
                        + " with the wrong type - expected "
                        + MATCHER_DESCRIPTOR);
            }
        }
    }

    /** Asserts the field exists as a boolean, on the class or a superclass. */
    private static void assertBooleanField(ZipFile zip, String jar, String className, String fieldName)
        throws Exception {
        String declaredIn = declaringClass(zip, jar, className, fieldName);
        if (declaredIn == null) {
            fail(
                "real GregTech jar " + jar
                    + " has no field "
                    + fieldName
                    + " on "
                    + className
                    + " (or its superclasses) - the guard would silently skip it");
        }
        assertEquals(
            "Z",
            classInfo(zip, jar, declaredIn).fields.get(fieldName),
            "real GregTech jar " + jar
                + " declares "
                + fieldName
                + " on "
                + declaredIn
                + " but not as a boolean - the guard writes a boolean");
    }

    /** The class in this hierarchy that really declares the field, or null. */
    private static String declaringClass(ZipFile zip, String jar, String start, String fieldName) throws Exception {
        for (String current = start; current != null && !"java/lang/Object".equals(current);) {
            ClassInfo info = classInfo(zip, jar, current);
            if (info.fields.containsKey(fieldName)) {
                return current;
            }
            current = info.superName;
        }
        return null;
    }

    private static ClassInfo classInfo(ZipFile zip, String jar, String internalName) throws Exception {
        ZipEntry entry = zip.getEntry(internalName + ".class");
        if (entry == null) {
            fail("real GregTech jar " + jar + " has no " + internalName + ".class - the guard's class name is stale");
        }
        try (InputStream in = zip.getInputStream(entry)) {
            ClassInfo info = new ClassInfo();
            new ClassReader(in).accept(new FieldCollector(info), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            return info;
        }
    }

    /** Field name to descriptor, plus the superclass name. */
    private static final class ClassInfo {

        final Map<String, String> fields = new LinkedHashMap<String, String>();
        String superName;
    }

    private static final class FieldCollector extends ClassVisitor {

        private final ClassInfo info;

        FieldCollector(ClassInfo info) {
            super(Opcodes.ASM5);
            this.info = info;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
            info.superName = superName;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            info.fields.put(name, descriptor);
            return null;
        }
    }
}
