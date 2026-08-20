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
import net.neoforged.fml.loading.FMLPaths;

/** Runtime JSON exclusions for automatically discovered Molten Fabricator recipes. */
public final class FabricatorRecipeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CONFIG_VERSION = 2;
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
                    readExclusions(root, "lava_to_molten", defaults.lavaToMolten()),
                    readExclusions(root, "raw_to_molten", defaults.rawToMolten()),
                    readExclusions(root, "molten_to_ingot", defaults.moltenToIngot()),
                    readExclusions(root, "steelmaking", defaults.steelmaking()));
            if (version < CONFIG_VERSION) {
                // Version 2 adds safety defaults for materials whose normal progression does not use raw-metal smelting.
                Set<String> raw = new LinkedHashSet<>(loaded.rawToMolten());
                raw.add("refined_obsidian");
                raw.add("refined_glowstone");
                raw.add("uranium");
                loaded = new Settings(loaded.lavaToMolten(), raw, loaded.moltenToIngot(), loaded.steelmaking());
                write(loaded);
            }
            settings = loaded;
        } catch (Exception exception) {
            settings = defaults;
            MCMoltenMetals.LOGGER.error("Could not read {}; using default Fabricator recipe exclusions", PATH, exception);
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

    public static Path path() {
        return PATH;
    }

    private static Set<String> readExclusions(JsonObject root, String key, Set<String> fallback) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return fallback;
        }
        JsonArray array = root.getAsJsonObject(key).getAsJsonArray("excluded_metals");
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
            root.addProperty("description", "Metal ids listed here are excluded only from the named Molten Fabricator recipe family.");
            root.add("lava_to_molten", section(value.lavaToMolten()));
            root.add("raw_to_molten", section(value.rawToMolten()));
            root.add("molten_to_ingot", section(value.moltenToIngot()));
            root.add("steelmaking", section(value.steelmaking()));
            Files.writeString(PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            MCMoltenMetals.LOGGER.error("Could not create default Fabricator recipe config {}", PATH, exception);
        }
    }

    private static JsonObject section(Set<String> exclusions) {
        JsonObject section = new JsonObject();
        JsonArray values = new JsonArray();
        exclusions.stream().sorted().forEach(values::add);
        section.add("excluded_metals", values);
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
            Set<String> lavaToMolten,
            Set<String> rawToMolten,
            Set<String> moltenToIngot,
            Set<String> steelmaking) {
        private Settings {
            lavaToMolten = Set.copyOf(lavaToMolten);
            rawToMolten = Set.copyOf(rawToMolten);
            moltenToIngot = Set.copyOf(moltenToIngot);
            steelmaking = Set.copyOf(steelmaking);
        }

        private static Settings defaults() {
            return new Settings(
                    Set.of(),
                    Set.of("netherite", "steel", "refined_obsidian", "refined_glowstone", "uranium"),
                    Set.of(),
                    Set.of());
        }
    }
}
