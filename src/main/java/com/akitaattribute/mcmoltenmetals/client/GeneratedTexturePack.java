package com.akitaattribute.mcmoltenmetals.client;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.registry.MetalDefinition;
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

public final class GeneratedTexturePack {
    private static final String PACK_META = """
            {
              "pack": {
                "pack_format": 34,
                "description": "MC Molten Metals generated fluid textures"
              }
            }
            """;

    private static final Path PACK_ROOT = FMLPaths.CONFIGDIR.get()
            .resolve(MCMoltenMetals.MOD_ID)
            .resolve("generated_resource_pack");

    private GeneratedTexturePack() {
    }

    public static Path root() {
        return PACK_ROOT;
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        try {
            ensureSkeleton();
        } catch (IOException exception) {
            MCMoltenMetals.LOGGER.error("Could not create generated resource-pack directory", exception);
            return;
        }

        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo(
                        MCMoltenMetals.MOD_ID + "/generated",
                        Component.literal("MC Molten Metals Generated Textures"),
                        PackSource.BUILT_IN,
                        Optional.empty()),
                new PathPackResources.PathResourcesSupplier(PACK_ROOT),
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));

        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    public static Path texturePath(MetalDefinition definition, boolean flowing) {
        String suffix = flowing ? "_flow.png" : "_still.png";
        return PACK_ROOT
                .resolve("assets")
                .resolve(MCMoltenMetals.MOD_ID)
                .resolve("textures")
                .resolve("fluid")
                .resolve(definition.moltenName() + suffix);
    }

    public static Path metadataPath(MetalDefinition definition, boolean flowing) {
        return texturePath(definition, flowing).resolveSibling(
                texturePath(definition, flowing).getFileName() + ".mcmeta");
    }

    public static Path signaturePath(MetalDefinition definition) {
        return PACK_ROOT.resolve(".mcmoltenmetals").resolve(definition.id() + ".sha256");
    }

    public static void ensureSkeleton() throws IOException {
        Files.createDirectories(PACK_ROOT);
        Path packMeta = PACK_ROOT.resolve("pack.mcmeta");
        byte[] expected = PACK_META.getBytes(StandardCharsets.UTF_8);
        if (!Files.exists(packMeta) || !java.util.Arrays.equals(Files.readAllBytes(packMeta), expected)) {
            Files.write(packMeta, expected);
        }
    }
}
