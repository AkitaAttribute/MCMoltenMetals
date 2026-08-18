package com.akitaattribute.mcmoltenmetals.registry;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

public final class MetalDiscovery {
    /*
     * An ingot convention establishes the molten-metal identity/name. Palette artwork prefers
     * a matching raw_<metal> item, then a matching <metal>_scrap item, then the ingot, then ore
     * artwork. This lets cases such as Netherite use Netherite Scrap for color without allowing
     * raw materials, scraps, or ores to create standalone molten-metal identities.
     */
    private static final String DISCOVERY_VERSION = "material-discovery-v5-raw-and-scrap-palette";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CACHE_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(MCMoltenMetals.MOD_ID)
            .resolve("metal-discovery-cache.json");

    private static final Pattern COMMON_TAG = Pattern.compile(
            "^data/(?:c|forge)/tags/(?:item|items)/(ingots|raw_materials|ores)/([^/]+)\\.json$");
    private static final Pattern ITEM_ASSET = Pattern.compile(
            "^assets/([^/]+)/(?:models|textures)/item/(.+)\\.(?:json|png)$");

    private MetalDiscovery() {
    }

    public static List<MetalDefinition> loadOrDiscover() {
        String startupSignature = startupSignature();
        List<MetalDefinition> cached = readCache(startupSignature);
        if (cached != null) {
            MCMoltenMetals.LOGGER.info("Loaded {} molten-metal definitions from discovery cache", cached.size());
            return cached;
        }

        Map<String, Candidate> candidates = new TreeMap<>();
        discoverAlreadyRegisteredItems(candidates);
        scanLoadedModFiles(candidates);

        List<MetalDefinition> definitions = candidates.values().stream()
                .filter(Candidate::isUsableMetal)
                .map(Candidate::toDefinition)
                .sorted(Comparator.comparing(MetalDefinition::id))
                .toList();

        writeCache(startupSignature, definitions);
        MCMoltenMetals.LOGGER.info("Discovered {} molten-metal definitions: {}",
                definitions.size(), definitions.stream().map(MetalDefinition::id).toList());
        return definitions;
    }

    private static void discoverAlreadyRegisteredItems(Map<String, Candidate> candidates) {
        for (ResourceLocation itemId : BuiltInRegistries.ITEM.keySet()) {
            String itemName = fileName(itemId.getPath());
            if (itemName.endsWith("_ingot") && itemName.length() > "_ingot".length()) {
                String material = normalizeMaterial(itemName.substring(0, itemName.length() - "_ingot".length()));
                candidate(candidates, material).markIngot().addSource(itemId, 20);
            } else if (itemName.startsWith("raw_") && itemName.length() > "raw_".length()) {
                String material = normalizeMaterial(itemName.substring("raw_".length()));
                candidate(candidates, material).addSource(itemId, 0);
            } else if (itemName.endsWith("_scrap") && itemName.length() > "_scrap".length()) {
                String material = normalizeMaterial(itemName.substring(0, itemName.length() - "_scrap".length()));
                candidate(candidates, material).addSource(itemId, 5);
            }
        }
    }

    private static void scanLoadedModFiles(Map<String, Candidate> candidates) {
        for (var modFileInfo : ModList.get().getModFiles()) {
            Path path = modFileInfo.getFile().getFilePath();
            try {
                if (Files.isDirectory(path)) {
                    scanDirectory(path, candidates);
                } else if (Files.isRegularFile(path)) {
                    scanZip(path, candidates);
                }
            } catch (Exception exception) {
                MCMoltenMetals.LOGGER.debug("Could not scan {} for metal conventions", path, exception);
            }
        }
    }

    private static void scanDirectory(Path root, Map<String, Candidate> candidates) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String relative = root.relativize(file).toString().replace('\\', '/');
                processResource(relative, () -> {
                    try {
                        return Files.readAllBytes(file);
                    } catch (IOException exception) {
                        return null;
                    }
                }, candidates);
            });
        }
    }

    private static void scanZip(Path jar, Map<String, Candidate> candidates) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                processResource(entry.getName(), () -> {
                    try (InputStream input = zip.getInputStream(entry)) {
                        return input.readAllBytes();
                    } catch (IOException exception) {
                        return null;
                    }
                }, candidates);
            }
        }
    }

    private static void processResource(String relative, Supplier<byte[]> bytes, Map<String, Candidate> candidates) {
        Matcher tagMatcher = COMMON_TAG.matcher(relative);
        if (tagMatcher.matches()) {
            String category = tagMatcher.group(1);
            String material = normalizeMaterial(tagMatcher.group(2));
            if (material.isEmpty()) {
                return;
            }

            Candidate candidate = candidate(candidates, material);
            int priority;
            if ("ingots".equals(category)) {
                candidate.markIngot();
                priority = 20;
            } else if ("raw_materials".equals(category)) {
                priority = 0;
            } else {
                priority = 40;
            }

            byte[] content = bytes.get();
            if (content != null) {
                addTagValues(candidate, content, priority);
            }
            return;
        }

        Matcher assetMatcher = ITEM_ASSET.matcher(relative);
        if (!assetMatcher.matches()) {
            return;
        }

        String namespace = assetMatcher.group(1);
        String itemPath = assetMatcher.group(2);
        String itemName = fileName(itemPath);
        if (itemName.endsWith("_ingot") && itemName.length() > "_ingot".length()) {
            String material = normalizeMaterial(itemName.substring(0, itemName.length() - "_ingot".length()));
            ResourceLocation itemId = safeLocation(namespace, itemPath);
            if (!material.isEmpty() && itemId != null) {
                candidate(candidates, material).markIngot().addSource(itemId, 30);
            }
        } else if (itemName.startsWith("raw_") && itemName.length() > "raw_".length()) {
            String material = normalizeMaterial(itemName.substring("raw_".length()));
            ResourceLocation itemId = safeLocation(namespace, itemPath);
            if (!material.isEmpty() && itemId != null) {
                candidate(candidates, material).addSource(itemId, 10);
            }
        } else if (itemName.endsWith("_scrap") && itemName.length() > "_scrap".length()) {
            String material = normalizeMaterial(itemName.substring(0, itemName.length() - "_scrap".length()));
            ResourceLocation itemId = safeLocation(namespace, itemPath);
            if (!material.isEmpty() && itemId != null) {
                candidate(candidates, material).addSource(itemId, 15);
            }
        }
    }

    private static void addTagValues(Candidate candidate, byte[] content, int priority) {
        try {
            JsonObject root = JsonParser.parseString(new String(content, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray values = root.getAsJsonArray("values");
            if (values == null) {
                return;
            }
            for (JsonElement value : values) {
                String id = null;
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    id = value.getAsString();
                } else if (value.isJsonObject() && value.getAsJsonObject().has("id")) {
                    id = value.getAsJsonObject().get("id").getAsString();
                }
                if (id == null || id.startsWith("#")) {
                    continue;
                }
                ResourceLocation location = safeLocation(id);
                if (location != null) {
                    candidate.addSource(location, priority);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Candidate candidate(Map<String, Candidate> candidates, String material) {
        return candidates.computeIfAbsent(normalizeMaterial(material), Candidate::new);
    }

    private static String startupSignature() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, DISCOVERY_VERSION);

            List<String> files = new ArrayList<>();
            for (var modFileInfo : ModList.get().getModFiles()) {
                Path path = modFileInfo.getFile().getFilePath();
                StringBuilder fingerprint = new StringBuilder(modFileInfo.getFile().getFileName());
                try {
                    fingerprint.append('|').append(Files.size(path));
                } catch (Exception ignored) {
                }
                try {
                    fingerprint.append('|').append(Files.getLastModifiedTime(path).toMillis());
                } catch (Exception ignored) {
                }
                files.add(fingerprint.toString());
            }
            files.stream().sorted().forEach(value -> update(digest, value));
            BuiltInRegistries.ITEM.keySet().stream()
                    .map(ResourceLocation::toString)
                    .sorted()
                    .forEach(value -> update(digest, value));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            return DISCOVERY_VERSION + "-uncached";
        }
    }

    private static List<MetalDefinition> readCache(String startupSignature) {
        if (!Files.exists(CACHE_PATH)) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(CACHE_PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!startupSignature.equals(root.get("signature").getAsString())) {
                return null;
            }
            List<MetalDefinition> definitions = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("metals")) {
                JsonObject metal = element.getAsJsonObject();
                List<ResourceLocation> sources = new ArrayList<>();
                for (JsonElement source : metal.getAsJsonArray("sourceItems")) {
                    ResourceLocation location = safeLocation(source.getAsString());
                    if (location != null) {
                        sources.add(location);
                    }
                }
                if (!sources.isEmpty()) {
                    definitions.add(new MetalDefinition(
                            metal.get("id").getAsString(),
                            metal.get("displayName").getAsString(),
                            sources));
                }
            }
            return List.copyOf(definitions);
        } catch (Exception exception) {
            MCMoltenMetals.LOGGER.debug("Ignoring unreadable metal discovery cache", exception);
            return null;
        }
    }

    private static void writeCache(String startupSignature, List<MetalDefinition> definitions) {
        try {
            Files.createDirectories(CACHE_PATH.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("signature", startupSignature);
            JsonArray metals = new JsonArray();
            for (MetalDefinition definition : definitions) {
                JsonObject metal = new JsonObject();
                metal.addProperty("id", definition.id());
                metal.addProperty("displayName", definition.displayName());
                JsonArray sources = new JsonArray();
                definition.sourceItems().forEach(source -> sources.add(source.toString()));
                metal.add("sourceItems", sources);
                metals.add(metal);
            }
            root.add("metals", metals);
            Files.writeString(CACHE_PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            MCMoltenMetals.LOGGER.debug("Could not write metal discovery cache", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String normalizeMaterial(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_")
                .replaceAll("_+", "_");
        while (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String displayName(String id) {
        String[] pieces = id.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(piece.charAt(0)));
            if (piece.length() > 1) {
                result.append(piece.substring(1));
            }
        }
        return result.toString();
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static ResourceLocation safeLocation(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            return safeLocation("minecraft", id);
        }
        return safeLocation(id.substring(0, colon), id.substring(colon + 1));
    }

    private static ResourceLocation safeLocation(String namespace, String path) {
        try {
            return ResourceLocation.fromNamespaceAndPath(namespace, path);
        } catch (Exception exception) {
            return null;
        }
    }

    private static final class Candidate {
        private final String id;
        private final Map<ResourceLocation, Integer> sourcePriorities = new LinkedHashMap<>();
        private boolean ingot;

        private Candidate(String id) {
            this.id = id;
        }

        private Candidate markIngot() {
            ingot = true;
            return this;
        }

        private Candidate addSource(ResourceLocation source, int priority) {
            sourcePriorities.merge(source, priority, Math::min);
            return this;
        }

        private boolean isUsableMetal() {
            return !id.isEmpty() && ingot && !sourcePriorities.isEmpty();
        }

        private MetalDefinition toDefinition() {
            List<ResourceLocation> sources = sourcePriorities.entrySet().stream()
                    .sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue()
                            .thenComparing(entry -> entry.getKey().toString()))
                    .map(Map.Entry::getKey)
                    .toList();
            return new MetalDefinition(id, displayName(id), sources);
        }
    }
}
