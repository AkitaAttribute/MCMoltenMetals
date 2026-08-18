package com.akitaattribute.mcmoltenmetals.resource;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.registry.MetalDefinition;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

public final class GeneratedDataPack {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PACK_META = """
            {
              "pack": {
                "pack_format": 48,
                "description": "MC Molten Metals generated fluid tags"
              }
            }
            """;
    private static final Path ROOT = FMLPaths.CONFIGDIR.get()
            .resolve(MCMoltenMetals.MOD_ID)
            .resolve("generated_data_pack");

    private GeneratedDataPack() {
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        try {
            prepare();
        } catch (IOException exception) {
            MCMoltenMetals.LOGGER.error("Could not create generated molten-metal data pack", exception);
            return;
        }

        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo(
                        MCMoltenMetals.MOD_ID + "/generated-data",
                        Component.literal("MC Molten Metals Generated Data"),
                        PackSource.BUILT_IN,
                        Optional.empty()),
                new PathPackResources.PathResourcesSupplier(ROOT),
                PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    private static void prepare() throws IOException {
        Files.createDirectories(ROOT);
        write(ROOT.resolve("pack.mcmeta"), PACK_META);

        JsonArray values = new JsonArray();
        for (MetalDefinition definition : MoltenMetalRegistry.definitions()) {
            values.add(MCMoltenMetals.id(definition.moltenName()).toString());
            values.add(MCMoltenMetals.id(definition.flowingName()).toString());
        }

        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", values);
        String json = GSON.toJson(tag);

        write(ROOT.resolve("data/minecraft/tags/fluid/lava.json"), json);
        write(ROOT.resolve("data/c/tags/fluid/molten_metals.json"), json);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        byte[] expected = content.getBytes(StandardCharsets.UTF_8);
        if (!Files.exists(path) || !java.util.Arrays.equals(Files.readAllBytes(path), expected)) {
            Files.write(path, expected);
        }
    }
}
