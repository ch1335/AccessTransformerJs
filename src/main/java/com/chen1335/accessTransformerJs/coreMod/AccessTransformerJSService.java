package com.chen1335.accessTransformerJs.coreMod;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class AccessTransformerJSService implements ITransformationService {
    @Override
    public @NotNull String name() {
        return "access_transformer_js";
    }

    @Override
    public void initialize(IEnvironment environment) {
        Path path = FMLPaths.GAMEDIR.get().resolve("kubejs").resolve("accesstransformer.cfg");
        File accessTransformerFile = path.toFile();
        if (!accessTransformerFile.exists()) {
            try {
                if (!FMLPaths.GAMEDIR.get().resolve("kubejs").toFile().exists()) {
                    FMLPaths.GAMEDIR.get().resolve("kubejs").toFile().mkdir();
                }
                if (accessTransformerFile.createNewFile()) {
                    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(accessTransformerFile));
                    bufferedWriter.write("# see https://docs.neoforged.net/docs/advanced/accesstransformers/ Examples\t\n");
                    bufferedWriter.write("public net.minecraft.world.entity.boss.wither.WitherBoss bossEvent");
                    bufferedWriter.flush();
                    bufferedWriter.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            Field field = FMLLoader.class.getDeclaredField("accessTransformer");
            field.setAccessible(true);
            AccessTransformerEngine accessTransformer = (AccessTransformerEngine) field.get(FMLLoader.class);
            accessTransformer.loadATFromPath(path);
        } catch (NoSuchFieldException | IllegalAccessException | IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {

    }

    @Override
    public @NotNull List<? extends ITransformer<?>> transformers() {
        return List.of();
    }
}
