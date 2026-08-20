package com.akitaattribute.mcmoltenmetals.config;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

/** Runtime JSON exclusions for molten discovery and Molten Fabricator recipes. */
public final class FabricatorRecipeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CONFIG_VERSION = 3;
    private static final Path PATH = FMLPaths.CONFIGDIR.get()
            .resolve(MCMoltenMetals.MOD_ID)
            .resolve("fabricator-recipes.json");

    private static volatile Settings settings = Settings.defaults();

    private FabricatorRecipeConfig() {
    }

    public static void initialize() {
        Settings defaults = Settings.defaults();
        if (!Files.exists(PATH)) {
            settings = defaults;
            write(defaults);
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            Settings loaded = new Settings(
                    readExclusions(root, "molten_discovery", "excluded_items", defaults.moltenSourceItems()),
                    readExclusions(root, "lava_to_molten", "excluded_metals", defaults.lavaToMolten()),
                    readExclusions(root, "raw_to_molten", "excluded_metals", defaults.rawToMolten()),
                    readExclusions(root, "molten_to_ingot", "excluded_metals", defaults.moltenToIngot()),
                    readExclusions(root, "steelmaking", "excluded_metals", defaults.steelmaking()));

            boolean migrated = false;
            Set<String> moltenSources = new LinkedHashSet<>(loaded.moltenSourceItems());
            Set<String> raw = new LinkedHashSet<>(loaded.rawToMolten());
            if (version < 2) {
                // Version 2 added safety defaults for materials whose normal progression does not use raw-metal smelting.
                raw.add("refined_obsidian");
                raw.add("refined_glowstone");
                raw.add("uranium");
                migrated = true;
            }
            if (version < 3) {
                // Version 3 moves source-level molten exclusions into visible configuration.
                moltenSources.add("pixelmon:crystal");
                migrated = true;
            }
            if (migrated) {
                loaded = new Settings(moltenSources, loaded.lavaToMolten(), raw, loaded.moltenToIngot(), loaded.steelmaking());
                write(loaded);
            }
            settings = loaded;
        } catch (Exception exception) {
            settings = defaults;
            MCMoltenMetals.LOGGER.error("Could not read {}; using default molten/Fabricator exclusions", PATH, exception);
        }
    }

    public static boolean allows(Family family, String metal) {
        String normalized = normalize(metal);
        Set<String> exclusions = switch (family) {
            case LAVA_TO_MOLTEN -> settings.lavaToMolten();
            case RAW_TO_MOLTEN -> settings.rawToMolten();
            case MOLTEN_TO_INGOT -> settings.moltenToIngot();
            case STEELMAKING -> settings.steelmaking();
        };
        return !normalized.isEmpty() && !exclusions.contains(normalized);
    }

    public static boolean isMoltenSourceExcluded(ResourceLocation itemId) {
        return itemId != null && settings.moltenSourceItems().contains(normalize(itemId.toString()));
    }

    public static Set<String> moltenSourceExclusions() {
        return settings.moltenSourceItems();
    }

    public static Path path() {
        return PATH;
    }

    private static Set<String> readExclusions(JsonObject root, String sectionKey, String listKey, Set<String> fallback) {
        if (!root.has(sectionKey) || !root.get(sectionKey).isJsonObject()) {
            return fallback;
        }
        JsonArray array = root.getAsJsonObject(sectionKey).getAsJsonArray(listKey);
        if (array == null) {
            return fallback;
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = normalize(element.getAsString());
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return Set.copyOf(values);
    }

    private static void write(Settings value) {
        try {
            Files.createDirectories(PATH.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CONFIG_VERSION);
            root.addProperty("description", "Discovery exclusions use full item ids; Fabricator recipe exclusions use metal ids.");
            root.add("molten_discovery", section("excluded_items", value.moltenSourceItems()));
            root.add("lava_to_molten", section("excluded_metals", value.lavaToMolten()));
            root.add("raw_to_molten", section("excluded_metals", value.rawToMolten()));
            root.add("molten_to_ingot", section("excluded_metals", value.moltenToIngot()));
            root.add("steelmaking", section("excluded_metals", value.steelmaking()));
            Files.writeString(PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            MCMoltenMetals.LOGGER.error("Could not create default Molten Metals config {}", PATH, exception);
        }
    }

    private static JsonObject section(String listKey, Set<String> exclusions) {
        JsonObject section = new JsonObject();
        JsonArray values = new JsonArray();
        exclusions.stream().sorted().forEach(values::add);
        section.add(listKey, values);
        return section;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public enum Family {
        LAVA_TO_MOLTEN,
        RAW_TO_MOLTEN,
        MOLTEN_TO_INGOT,
        STEELMAKING
    }

    private record Settings(
            Set<String> moltenSourceItems,
            Set<String> lavaToMolten,
            Set<String> rawToMolten,
            Set<String> moltenToIngot,
            Set<String> steelmaking) {
        private Settings {
            moltenSourceItems = Set.copyOf(moltenSourceItems);
            lavaToMolten = Set.copyOf(lavaToMolten);
            rawToMolten = Set.copyOf(rawToMolten);
            moltenToIngot = Set.copyOf(moltenToIngot);
            steelmaking = Set.copyOf(steelmaking);
        }

        private static Settings defaults() {
            return new Settings(
                    Set.of("pixelmon:crystal"),
                    Set.of(),
                    Set.of("netherite", "steel", "refined_obsidian", "refined_glowstone", "uranium"),
                    Set.of(),
                    Set.of());
        }
    }
}
