package com.gemsi.easyafk.platform;

import com.gemsi.easyafk.ConfigSchema;
import com.gemsi.easyafk.EasyAFK;
import com.gemsi.easyafk.platform.services.IConfigService;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the Forge config spec from {@link ConfigSchema}, so settings are declared
 * in exactly one place. Config paths are unchanged from previous versions, so existing
 * {@code easyafk-server.toml} files keep working.
 */
@Mod.EventBusSubscriber(modid = EasyAFK.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeConfigService implements IConfigService {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final Map<String, ForgeConfigSpec.ConfigValue<?>> VALUES = new LinkedHashMap<>();
    public static final ForgeConfigSpec SPEC = buildSpec();

    private static ForgeConfigSpec buildSpec() {
        for (ConfigSchema.Entry entry : ConfigSchema.entries()) {
            BUILDER.comment(entry.comment().toArray(new String[0]));
            ForgeConfigSpec.ConfigValue<?> value = switch (entry.type()) {
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
            ForgeConfigSpec.ConfigValue<?> value = VALUES.get(entry.path());
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
