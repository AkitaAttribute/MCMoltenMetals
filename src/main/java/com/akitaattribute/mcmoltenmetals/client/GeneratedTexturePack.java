package com.akitaattribute.mcmoltenmetals.client;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.registry.MetalDefinition;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PACK_META = """
            {
              "pack": {
                "pack_format": 34,
                "description": "MC Molten Metals generated client assets"
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
            writeDynamicModels();
            writeDynamicLanguage();
        } catch (IOException exception) {
            MCMoltenMetals.LOGGER.error("Could not create generated client resource pack", exception);
            return;
        }

        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo(
                        MCMoltenMetals.MOD_ID + "/generated-client",
                        Component.literal("MC Molten Metals Generated Client Assets"),
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
        return PACK_ROOT.resolve("assets").resolve(MCMoltenMetals.MOD_ID)
                .resolve("textures/block").resolve(definition.moltenName() + suffix);
    }

    public static Path bucketTexturePath(MetalDefinition definition) {
        return PACK_ROOT.resolve("assets").resolve(MCMoltenMetals.MOD_ID)
                .resolve("textures/item").resolve(definition.moltenName() + "_bucket.png");
    }

    public static Path metadataPath(MetalDefinition definition, boolean flowing) {
        Path texture = texturePath(definition, flowing);
        return texture.resolveSibling(texture.getFileName() + ".mcmeta");
    }

    public static Path signaturePath(MetalDefinition definition) {
        return PACK_ROOT.resolve(".mcmoltenmetals").resolve(definition.id() + ".sha256");
    }

    public static void ensureSkeleton() throws IOException {
        Files.createDirectories(PACK_ROOT);
        writeIfChanged(PACK_ROOT.resolve("pack.mcmeta"), PACK_META);
    }

    public static void cleanupStaleGeneratedAssets() throws IOException {
        Set<String> active = MoltenMetalRegistry.definitions().stream()
                .map(MetalDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        cleanupMatching(PACK_ROOT.resolve(".mcmoltenmetals"), active, ".sha256", "");
        cleanupMatching(PACK_ROOT.resolve("assets").resolve(MCMoltenMetals.MOD_ID).resolve("textures/block"),
                active, ".png", "molten_");
        cleanupMatching(PACK_ROOT.resolve("assets").resolve(MCMoltenMetals.MOD_ID).resolve("textures/item"),
                active, "_bucket.png", "molten_");
        deleteDirectoryContents(PACK_ROOT.resolve("assets").resolve(MCMoltenMetals.MOD_ID).resolve("textures/fluid"));
    }

    private static void writeDynamicModels() throws IOException {
        Path assets = PACK_ROOT.resolve("assets").resolve(MCMoltenMetals.MOD_ID);
        Path blockstates = assets.resolve("blockstates");
        Path blockModels = assets.resolve("models/block");
        Path itemModels = assets.resolve("models/item");
        Files.createDirectories(blockstates);
        Files.createDirectories(blockModels);
        Files.createDirectories(itemModels);

        Set<String> expectedBlockstates = new HashSet<>();
        Set<String> expectedBlockModels = new HashSet<>();
        Set<String> expectedItemModels = new HashSet<>();

        for (MetalDefinition definition : MoltenMetalRegistry.definitions()) {
            String molten = definition.moltenName();
            String blockstateName = molten + ".json";
            String itemModelName = molten + "_bucket.json";
            expectedBlockstates.add(blockstateName);
            expectedBlockModels.add(blockstateName);
            expectedItemModels.add(itemModelName);

            writeIfChanged(blockstates.resolve(blockstateName), """
                    {
                      "variants": {
                        "": { "model": "mcmoltenmetals:block/%s" }
                      }
                    }
                    """.formatted(molten));
            writeIfChanged(blockModels.resolve(blockstateName), """
                    {
                      "textures": {
                        "particle": "mcmoltenmetals:block/%s_still"
                      }
                    }
                    """.formatted(molten));
            writeIfChanged(itemModels.resolve(itemModelName), """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "mcmoltenmetals:item/%s_bucket"
                      }
                    }
                    """.formatted(molten));
        }

        deleteUnexpectedJson(blockstates, expectedBlockstates);
        deleteUnexpectedJson(blockModels, expectedBlockModels);
        deleteUnexpectedJson(itemModels, expectedItemModels);
    }

    private static void writeDynamicLanguage() throws IOException {
        JsonObject language = new JsonObject();
        for (MetalDefinition definition : MoltenMetalRegistry.definitions()) {
            String molten = definition.moltenName();
            String display = "Molten " + definition.displayName();

            // FluidType uses this description key. The block/item keys keep probes, JEI,
            // inventory screens, and other integrations on the same human-readable naming.
            language.addProperty("fluid." + MCMoltenMetals.MOD_ID + "." + molten, display);
            language.addProperty("block." + MCMoltenMetals.MOD_ID + "." + molten, display);
            language.addProperty("item." + MCMoltenMetals.MOD_ID + "." + molten + "_bucket", display + " Bucket");
        }

        Path languagePath = PACK_ROOT.resolve("assets").resolve(MCMoltenMetals.MOD_ID)
                .resolve("lang/en_us.json");
        writeIfChanged(languagePath, GSON.toJson(language) + "\n");
    }

    private static void deleteUnexpectedJson(Path directory, Set<String> expected) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.list(directory)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (name.endsWith(".json") && !expected.contains(name)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void cleanupMatching(Path directory, Set<String> active, String suffix, String prefix) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.list(directory)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith(prefix) || !name.endsWith(suffix)) {
                    continue;
                }
                String material = name.substring(prefix.length(), name.length() - suffix.length());
                if (material.endsWith("_still")) {
                    material = material.substring(0, material.length() - "_still".length());
                } else if (material.endsWith("_flow")) {
                    material = material.substring(0, material.length() - "_flow".length());
                }
                if (!active.contains(material)) {
                    Files.deleteIfExists(path);
                    Files.deleteIfExists(path.resolveSibling(path.getFileName() + ".mcmeta"));
                }
            }
        }
    }

    private static void deleteDirectoryContents(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (!path.equals(directory)) {
                    Files.deleteIfExists(path);
                }
            }
        }
        Files.deleteIfExists(directory);
    }

    private static void writeIfChanged(Path path, String content) throws IOException {
        byte[] expected = content.getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(path.getParent());
        if (!Files.exists(path) || !java.util.Arrays.equals(Files.readAllBytes(path), expected)) {
            Files.write(path, expected);
        }
    }
}
