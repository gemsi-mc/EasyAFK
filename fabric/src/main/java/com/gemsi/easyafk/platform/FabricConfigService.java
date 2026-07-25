package com.gemsi.easyafk.platform;

import com.gemsi.easyafk.ConfigSchema;
import com.gemsi.easyafk.platform.services.IConfigService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code config/easyafk.json}, driven entirely by {@link ConfigSchema}.
 *
 * <p>Keys are the {@code Config} field names, which is what previous versions wrote, so
 * existing config files keep loading unchanged. Settings absent from the file keep their
 * default and are added back when the file is rewritten on load.
 */
public class FabricConfigService implements IConfigService {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "easyafk.json");

    @Override
    public void load() {
        ConfigSchema.applyDefaults();

        if (CONFIG_FILE.exists()) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root != null && root.isJsonObject()) {
                    apply(root.getAsJsonObject());
                } else {
                    LOGGER.error("EasyAFK config at {} is not a JSON object; using defaults", CONFIG_FILE);
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Failed to read EasyAFK config from {}; using defaults", CONFIG_FILE, e);
            }
        }

        // Rewrite so settings added in a new version show up in the file with their defaults.
        save();
    }

    private void apply(JsonObject root) {
        for (ConfigSchema.Entry entry : ConfigSchema.entries()) {
            JsonElement element = root.get(entry.field());
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                entry.setter().accept(ConfigSchema.coerce(entry, toJavaValue(element)));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Ignoring invalid EasyAFK config value: {}", e.getMessage());
            }
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        for (ConfigSchema.Entry entry : ConfigSchema.entries()) {
            root.add(entry.field(), toJsonValue(entry.getter().get()));
        }

        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write EasyAFK config to {}", CONFIG_FILE, e);
        }
    }

    private static Object toJavaValue(JsonElement element) {
        if (element.isJsonArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                values.add(toJavaValue(child));
            }
            return values;
        }
        if (!element.isJsonPrimitive()) {
            throw new IllegalArgumentException("unsupported config value: " + element);
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsNumber();
        }
        return primitive.getAsString();
    }

    private static JsonElement toJsonValue(Object value) {
        if (value instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (value instanceof Number n) {
            return new JsonPrimitive(n);
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            list.forEach(item -> array.add(String.valueOf(item)));
            return array;
        }
        return new JsonPrimitive(String.valueOf(value));
    }
}
