package com.gemsi.easyafk;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSchemaTest {

    /**
     * The drift guard. Every tunable on {@link Config} must be described by the schema,
     * because the schema is what each loader's config service reads and writes. A field
     * that is missing here is a field the server owner cannot actually configure.
     */
    @Test
    void everyConfigFieldHasASchemaEntry() {
        Set<String> described = ConfigSchema.entries().stream()
                .map(ConfigSchema.Entry::field)
                .collect(Collectors.toSet());

        List<String> undescribed = tunableFieldNames().stream()
                .filter(name -> !described.contains(name))
                .toList();

        assertTrue(undescribed.isEmpty(),
                "Config fields with no ConfigSchema entry (server owners cannot set these): " + undescribed);
    }

    @Test
    void everySchemaEntryMatchesARealConfigField() {
        Set<String> tunables = Set.copyOf(tunableFieldNames());

        List<String> orphans = ConfigSchema.entries().stream()
                .map(ConfigSchema.Entry::field)
                .filter(name -> !tunables.contains(name))
                .toList();

        assertTrue(orphans.isEmpty(), "ConfigSchema entries with no matching Config field: " + orphans);
    }

    /**
     * Guards against cross-wired accessor lambdas: if two entries read or write the same
     * {@link Config} field, writing every entry then reading them all back exposes it,
     * because the later write clobbers the earlier one.
     */
    @Test
    void everyEntryReadsBackItsOwnValue() {
        try {
            List<ConfigSchema.Entry> entries = ConfigSchema.entries();
            entries.forEach(entry -> entry.setter().accept(probeFor(entry)));

            for (ConfigSchema.Entry entry : entries) {
                assertEquals(probeFor(entry), entry.getter().get(),
                        "entry '" + entry.path() + "' did not read back its own value; "
                                + "its getter/setter probably point at the wrong Config field");
            }
        } finally {
            ConfigSchema.applyDefaults();
        }
    }

    /** A value distinct from the default, and distinct from every other entry's probe. */
    private static Object probeFor(ConfigSchema.Entry entry) {
        return switch (entry.type()) {
            case BOOL -> !entry.defaultBool();
            case INT -> clampedProbe(entry);
            case DOUBLE -> Math.min(entry.defaultDouble() + 0.01, entry.max());
            case STRING -> "probe:" + entry.field();
            case STRING_LIST -> List.of("probe:" + entry.field());
            case COLOR -> "#123456";
        };
    }

    private static int clampedProbe(ConfigSchema.Entry entry) {
        int candidate = entry.defaultInt() + 1;
        return candidate <= entry.max() ? candidate : entry.defaultInt() - 1;
    }

    @Test
    void configPathsAreUnique() {
        List<String> paths = ConfigSchema.entries().stream().map(ConfigSchema.Entry::path).toList();
        assertEquals(paths.size(), Set.copyOf(paths).size(), "duplicate config paths in schema: " + paths);
    }

    private static List<String> tunableFieldNames() {
        return java.util.Arrays.stream(Config.class.getDeclaredFields())
                .filter(f -> Modifier.isPublic(f.getModifiers()))
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .filter(f -> !Modifier.isFinal(f.getModifiers()))
                .map(Field::getName)
                .toList();
    }
}
