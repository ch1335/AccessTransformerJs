package com.chen1335.coreMod;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import net.neoforged.accesstransformer.FieldTarget;
import net.neoforged.accesstransformer.MethodTarget;
import net.neoforged.accesstransformer.Target;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import net.neoforged.accesstransformer.parser.AccessTransformerList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AccessTransformerJSService implements ITransformationService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Path AT_PATH;
    private static final HashSet<String> RELATED_CLASSES = new HashSet<>();

    public static ImmutableSet<String> getRelatedClasses() {
        return ImmutableSet.copyOf(RELATED_CLASSES);
    }

    @Override
    public @NotNull String name() {
        return "atjs";
    }

    @Override
    public void initialize(IEnvironment environment) {
        Path path = FMLPaths.GAMEDIR.get().resolve("kubejs").resolve("accesstransformer.cfg");
        AT_PATH = path;
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

    public static void generateRelatedClasses(ClassLoader classLoader) throws IOException {
        LOGGER.debug("start generate related Classes");
        RELATED_CLASSES.clear();
        int api = Opcodes.ASM9;
        AccessTransformerList loadedAccessTransformer = new AccessTransformerList();
        loadedAccessTransformer.loadFromPath(AT_PATH);
        loadedAccessTransformer.getAccessTransformers().forEach((className, accessTransformers) -> {
            accessTransformers.forEach(accessTransformer1 -> {
                RELATED_CLASSES.add(accessTransformer1.getTarget().getClassName());
                if ((Target<?>) accessTransformer1.getTarget() instanceof FieldTarget fieldTarget) {
                    RELATED_CLASSES.add(fieldTarget.getClassName());
                    String fieldName = fieldTarget.getFieldName();
                    ClassNode classNode = new ClassNode(api);
                    try {
                        ClassReader classReader = new ClassReader(Objects.requireNonNull(classLoader.getResourceAsStream(fieldTarget.getClassName().replace('.', '/') + ".class")));
                        classReader.accept(classNode, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                        List<FieldNode> fieldNodes = classNode.fields;
                        for (int i = 0; i < fieldNodes.size(); i++) {
                            FieldNode fieldNode = fieldNodes.get(i);
                            if (Objects.equals(fieldNode.name, fieldName)) {
                                Type type = Type.getType(fieldNode.desc);
                                RELATED_CLASSES.add(type.getClassName());
                                break;
                            }
                            if (i + 1 == fieldNodes.size()) {
                                throw new Error(String.format("[AccessTransformerJs] Field: %s not found at Class : %s", fieldName, fieldTarget.getClassName()));
                            }
                        }
                    } catch (IOException e) {
                        LOGGER.error(e.toString());
                        throw new RuntimeException(e);
                    }
                } else if ((Target<?>) accessTransformer1.getTarget() instanceof MethodTarget methodTarget) {
                    try {
                        Field field = MethodTarget.class.getDeclaredField("returnType");
                        field.setAccessible(true);
                        RELATED_CLASSES.add(((Type) field.get(methodTarget)).getClassName());
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        });
        LOGGER.debug("generate related Classes finish");
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {

    }

    @Override
    public @NotNull List<? extends ITransformer<?>> transformers() {
        return List.of();
    }


}
