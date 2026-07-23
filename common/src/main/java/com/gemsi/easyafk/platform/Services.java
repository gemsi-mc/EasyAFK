package com.gemsi.easyafk.platform;

import com.gemsi.easyafk.platform.services.IConfigService;
import com.gemsi.easyafk.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

// Common code accesses platform-specific implementations through Java's ServiceLoader. Each loader module
// (fabric/, forge/, neoforge/) ships its implementation and registers it in
// META-INF/services/<fully-qualified-interface-name>.
public class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    public static final IConfigService CONFIG = load(IConfigService.class);

    public static <T> T load(Class<T> clazz) {

        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        return loadedService;
    }
}