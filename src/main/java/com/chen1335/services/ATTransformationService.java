package com.chen1335.services;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FormatDetector;
import com.electronwill.nightconfig.core.io.ConfigParser;
import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import net.minecraftforge.accesstransformer.*;
import net.minecraftforge.accesstransformer.parser.AccessTransformerList;
import net.minecraftforge.accesstransformer.service.AccessTransformerService;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.ModsFolderLocator;
import net.minecraftforge.fml.loading.moddiscovery.NightConfigWrapper;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class ATTransformationService implements ITransformationService {
    private static Path AT_PATH;
    private static Path MAPPED_AT_PATH;
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<String, Object> MAP = Map.of();

    @Override
    public @NotNull String name() {
        return "atjs";
    }

    @Override
    public void initialize(IEnvironment iEnvironment) {
        LOGGER.info("accessTransformerJS initialize start");
        Path path = FMLPaths.GAMEDIR.get().resolve("kubejs");
        AT_PATH = path.resolve("accesstransformer.cfg");
        MAPPED_AT_PATH = path.resolve("mapped_accesstransformer.cfg");
        File accessTransformerFile = AT_PATH.toFile();
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
                LOGGER.info(e.toString());
                throw new RuntimeException(e);
            }
        }
        try {
            generateMappedAccessTransformerConfigFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void generateMappedAccessTransformerConfigFile() {
        try {
            File mappedAccessTransformerFile = MAPPED_AT_PATH.toFile();
            if (mappedAccessTransformerFile.createNewFile()) {
                LOGGER.info("create new mapped accessTransformerFile");
            }

            LOGGER.info("start generate mappedAccessTransformerFile");
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(mappedAccessTransformerFile));
            bufferedWriter.write("# This file is used to store accessTransformer config that have been obfuscated.You shouldn't modify this file!\t\n");

            LOGGER.info("try load rhino map");
            RhinoMapLoader.load();
            LOGGER.info("load rhino map finished");

            AccessTransformerList loadedAccessTransformer = new AccessTransformerList();
            loadedAccessTransformer.loadFromPath(AT_PATH, "accessTransformerJSAddition");
            loadedAccessTransformer.getAccessTransformers().forEach((className, accessTransformers) -> {
                accessTransformers.forEach(accessTransformer -> {
                    if ((Target<?>) accessTransformer.getTarget() instanceof FieldTarget fieldTarget) {
                        try {
                            bufferedWriter.write(getTargetAccess(accessTransformer) + " " + fieldTarget.getClassName() + " " + RhinoMapLoader.getMappedField(fieldTarget.getClassName(), fieldTarget.getFieldName()) + " #" + fieldTarget.getFieldName() + "\t\n");
                        } catch (Exception e) {
                            LOGGER.error(String.valueOf(e.getCause()));
                        }
                    } else if ((Target<?>) accessTransformer.getTarget() instanceof MethodTarget methodTarget) {
                        try {
                            Field field = MethodTarget.class.getDeclaredField("arguments");
                            field.setAccessible(true);
                            List<Type> types = (List<Type>) field.get(methodTarget);
                            bufferedWriter.write(getTargetAccess(accessTransformer) + " " + methodTarget.getClassName() + " " + RhinoMapLoader.getMappedMethod(methodTarget.getClassName(), methodTarget.targetName(), types) + " #" + methodTarget.targetName().split("\\(")[0] + "\t\n");
                        } catch (Exception e) {
                            LOGGER.error(String.valueOf(e.getCause()));
                        }
                    } else if ((Target<?>) accessTransformer.getTarget() instanceof ClassTarget classTarget) {
                        try {
                            bufferedWriter.write(getTargetAccess(accessTransformer) + " " + classTarget.getClassName() + "\t\n");
                        } catch (Exception e) {
                            LOGGER.error(String.valueOf(e.getCause()));
                        }
                    }
                });

            });

            bufferedWriter.flush();
            bufferedWriter.close();

            try {
                LOGGER.info("AccessTransformerJS: trying add custom accessTransformer config");
                Field field = FMLLoader.class.getDeclaredField("accessTransformer");
                field.setAccessible(true);
                AccessTransformerService accessTransformer = (AccessTransformerService) field.get(FMLLoader.class);
                accessTransformer.offerResource(MAPPED_AT_PATH, "accessTransformerJSAddition");
                LOGGER.info("AccessTransformerJS: try add custom accessTransformer config success");
            } catch (NoSuchFieldException | IllegalAccessException e) {
                LOGGER.info(e.getMessage());
                throw new RuntimeException(e);
            }
            LOGGER.info("accessTransformerJS initialize finished");
            MAP = null;
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            throw new RuntimeException(e);
        }

    }

    private static String getTargetAccess(AccessTransformer accessTransformer) throws NoSuchFieldException, IllegalAccessException {
        Field f = AccessTransformer.class.getDeclaredField("targetAccess");
        f.setAccessible(true);
        AccessTransformer.Modifier modifier = (AccessTransformer.Modifier) f.get(accessTransformer);
        String s = modifier.name().toLowerCase();

        Field f2 = AccessTransformer.class.getDeclaredField("targetFinalState");
        f2.setAccessible(true);
        AccessTransformer.FinalState finalState = (AccessTransformer.FinalState) f2.get(accessTransformer);

        if (finalState == AccessTransformer.FinalState.REMOVEFINAL) {
            s = s + "-f";
        } else if (finalState == AccessTransformer.FinalState.MAKEFINAL) {
            s = s + "+f";
        }

        return s;
    }


    private static class RhinoMapLoader {
        public static void load() {
            try {
                ModsFolderLocator m = new ModsFolderLocator();
                Stream<Path> pathStream = m.scanCandidates();
                LOGGER.info("finding mods");
                List<IModLocator.ModFileOrException> r = m.scanMods();
                LOGGER.info("finding mods finished");
                Iterator<Path> iterator = pathStream.iterator();
                boolean rhinoFounded = false;
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    URL jarUrl = path.toUri().toURL();

                    try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, Thread.currentThread().getContextClassLoader())) {
                        try (InputStream is = classLoader.getResourceAsStream("META-INF/mods.toml")) {
                            if (is != null) {
                                ConfigParser<?> parser = FormatDetector.detectByName("mods.toml").createParser();
                                Config config = parser.parse(is);
                                final NightConfigWrapper configWrapper = new NightConfigWrapper(config);
                                for (IConfigurable mods : configWrapper.getConfigList("mods")) {
                                    Optional<String> optional = mods.getConfigElement("modId");
                                    if (optional.isPresent() && optional.get().equals("rhino")) {
                                        LOGGER.info("Rhino is founded! Now I'm going to secretly use his mm.jsmappings!");
                                        rhinoFounded = true;
                                        InputStream jsmappingsIs = classLoader.getResourceAsStream("mm.jsmappings");
                                        if (jsmappingsIs != null) {

                                            try {
                                                LOGGER.info("trying load minecraftRemapper");
                                                BufferedInputStream bufferedInputStream = new BufferedInputStream(new GZIPInputStream(jsmappingsIs));
                                                Class<?> c = classLoader.loadClass("dev.latvian.mods.rhino.mod.util.MinecraftRemapper");
                                                Method method = c.getMethod("load", InputStream.class, boolean.class);
                                                Object minecraftRemapper = method.invoke(c, bufferedInputStream, false);
                                                Field classMapField = c.getDeclaredField("classMap");
                                                classMapField.setAccessible(true);
                                                MAP = (Map<String, Object>) classMapField.get(minecraftRemapper);
                                                bufferedInputStream.close();
                                                LOGGER.info("load minecraftRemapper success");
                                            } catch (Throwable t) {
                                                LOGGER.info(String.valueOf(t.getCause()));
                                            }


                                        } else {
                                            throw new Exception("Rhino is founded,but i can't find his mm.jsmappings,This may be my fault,But now I'm going to crash your game! :)");
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (!rhinoFounded) {
                    throw new Exception("Rhino is not found,WHAT ARE YOU DOING!");
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
                throw new RuntimeException();
            }
        }

        public static String getMappedField(String className, String fieldName) throws IllegalAccessException, NoSuchFieldException {
            LOGGER.info("finding field mappedName: {} at class {}", fieldName, className);
            Object remappedClass = MAP.get(className);
            if (remappedClass == null) {
                return fieldName;
            }
            Field field = remappedClass.getClass().getDeclaredField("fields");
            field.setAccessible(true);
            Map<String, String> fields = (Map<String, String>) field.get(remappedClass);

            if (fields == null) {
                return fieldName;
            }

            for (String key : fields.keySet()) {
                if (fields.get(key).equals(fieldName)) {
                    return key;
                }
            }
            return fieldName;
        }

        public static String getMappedMethod(String className, String methodName, List<Type> types) throws IllegalAccessException, NoSuchFieldException {
            LOGGER.info("finding method mappedName: {} at class {}", methodName, className);
            Object remappedClass = MAP.get(className);
            if (remappedClass == null) {
                return methodName;
            }

            if (types.isEmpty()) {
                Field field = remappedClass.getClass().getDeclaredField("emptyMethods");
                field.setAccessible(true);
                Map<String, String> emptyMethods = (Map<String, String>) field.get(remappedClass);

                for (String mappedName : emptyMethods.keySet()) {
                    if (emptyMethods.get(mappedName).equals(methodName.split("\\(")[0])) {
                        return mappedName + "()" + methodName.split("\\)")[1];
                    }
                }
            }

            Field field = remappedClass.getClass().getDeclaredField("methods");
            field.setAccessible(true);
            Map<String, String> methods = (Map<String, String>) field.get(remappedClass);

            if (methods == null) {
                return methodName;
            }

            for (String mappedNameAndDescriptor : methods.keySet()) {
                String[] strings = mappedNameAndDescriptor.split("\\(");
                String mappedName = strings[0];
                String descriptor = strings[1];

                String rhinoRefMappedNameAndDescriptor = methodName.split("\\)")[0];
                String[] strings1 = rhinoRefMappedNameAndDescriptor.split("\\(");
                String mappedName1 = strings1[0];
                String descriptor1 = strings1[1];

                if (methods.get(mappedNameAndDescriptor).equals(mappedName1) && descriptor.equals(descriptor1)) {
                    return mappedName + "(" + descriptor1 + ")" + methodName.split("\\)")[1];
                }

            }
            return methodName;
        }


    }

    @Override
    public void onLoad(IEnvironment iEnvironment, Set<String> set) {

    }

    @Override
    public @NotNull List<ITransformer> transformers() {
        return List.of();
    }
}
