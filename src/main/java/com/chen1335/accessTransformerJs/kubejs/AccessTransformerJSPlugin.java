package com.chen1335.accessTransformerJs.kubejs;

import com.chen1335.coreMod.AccessTransformerJSService;
import com.mojang.logging.LogUtils;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.ScriptManager;
import org.slf4j.Logger;

import java.io.IOException;

public class AccessTransformerJSPlugin implements KubeJSPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void init() {
        try {
            AccessTransformerJSService.generateRelatedClasses(this.getClass().getClassLoader());
        } catch (IOException e) {
            LOGGER.error(e.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        LOGGER.info("start check ClassFilter");
        AccessTransformerJSService.getRelatedClasses().forEach(className -> {
            if (!manager.isClassAllowed(className)) {
                throw new Error(String.format("[AccessTransformerJs] Class: %s is not allowed to be access by Script %s", className, manager.scriptType.name));
            }
        });
        LOGGER.info("check ClassFilter finish");
    }
}
