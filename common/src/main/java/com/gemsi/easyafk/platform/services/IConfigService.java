package com.gemsi.easyafk.platform.services;

public interface IConfigService {

    /**
     * Populate the static fields on {@link com.gemsi.easyafk.Config} from the
     * platform-specific backing store. On Fabric this reads a Gson JSON file;
     * on Forge/NeoForge the values are populated by a config-event listener
     * and this call is a no-op.
     */
    void load();
}
