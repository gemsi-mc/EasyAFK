package com.gemsi.easyafk.platform;

import com.gemsi.easyafk.ConfigSchema;
import com.gemsi.easyafk.EasyAFK;
import com.gemsi.easyafk.platform.services.IConfigService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the NeoForge config spec from {@link ConfigSchema}, so settings are declared
 * in exactly one place. Config paths are unchanged from previous versions, so existing
 * {@code easyafk-server.toml} files keep working.
 */
@EventBusSubscriber(modid = EasyAFK.MODID, bus = EventBusSubscriber.Bus.MOD)
public class NeoForgeConfigService implements IConfigService {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final Map<String, ModConfigSpec.ConfigValue<?>> VALUES = new LinkedHashMap<>();
    public static final ModConfigSpec SPEC = buildSpec();

    private static ModConfigSpec buildSpec() {
        for (ConfigSchema.Entry entry : ConfigSchema.entries()) {
            BUILDER.comment(entry.comment().toArray(new String[0]));
            ModConfigSpec.ConfigValue<?> value = switch (entry.type()) {
                case BOOL -> BUILDER.define(entry.path(), entry.defaultBool());
                case INT -> BUILDER.defineInRange(entry.path(), entry.defaultInt(),
                        (int) entry.min(), (int) entry.max());
                case DOUBLE -> BUILDER.defineInRange(entry.path(), entry.defaultDouble(),
                        entry.min(), entry.max());
                case STRING, COLOR -> BUILDER.define(entry.path(), entry.defaultString());
                case STRING_LIST -> BUILDER.defineList(entry.path(), entry.defaultStringList(),
                        obj -> obj instanceof String);
            };
            VALUES.put(entry.path(), value);
        }
        return BUILDER.build();
    }

    @Override
    public void load() {
        // No-op: values are populated by the ModConfigEvent listener below.
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (!(event instanceof ModConfigEvent.Loading) && !(event instanceof ModConfigEvent.Reloading)) {
            return;
        }
        for (ConfigSchema.Entry entry : ConfigSchema.entries()) {
            ModConfigSpec.ConfigValue<?> value = VALUES.get(entry.path());
            if (value == null) {
                continue;
            }
            try {
                entry.setter().accept(ConfigSchema.coerce(entry, value.get()));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Ignoring invalid EasyAFK config value: {}", e.getMessage());
            }
        }
    }
}
