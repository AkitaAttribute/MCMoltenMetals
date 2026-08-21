package com.akitaattribute.mcmoltenmetals.client;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.registry.MetalDefinition;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class MoltenTextureGenerator {
    private static final String ALGORITHM_VERSION = "lava-palette-remap-v3-block-atlas";
    private static final int PALETTE_SIZE = 12;
    private static final AtomicBoolean RELOAD_REQUESTED = new AtomicBoolean(false);

    private static final ResourceLocation LAVA_STILL = minecraft("textures/block/lava_still.png");
    private static final ResourceLocation LAVA_FLOW = minecraft("textures/block/lava_flow.png");
    private static final ResourceLocation LAVA_STILL_META = minecraft("textures/block/lava_still.png.mcmeta");
    private static final ResourceLocation LAVA_FLOW_META = minecraft("textures/block/lava_flow.png.mcmeta");
    private static final ResourceLocation LAVA_BUCKET = minecraft("textures/item/lava_bucket.png");
    private static final ResourceLocation EMPTY_BUCKET = minecraft("textures/item/bucket.png");

    private MoltenTextureGenerator() {
    }

    public static boolean consumeReloadRequest() {
        return RELOAD_REQUESTED.compareAndSet(true, false);
    }

    private static boolean generate(ResourceManager resourceManager) {
        boolean changed = false;

        try {
            GeneratedTexturePack.ensureSkeleton();
            GeneratedTexturePack.cleanupStaleGeneratedAssets();
            byte[] lavaStill = readRequired(resourceManager, LAVA_STILL);
            byte[] lavaFlow = readRequired(resourceManager, LAVA_FLOW);
            byte[] lavaStillMeta = readOptional(resourceManager, LAVA_STILL_META);
            byte[] lavaFlowMeta = readOptional(resourceManager, LAVA_FLOW_META);
            byte[] lavaBucket = readRequired(resourceManager, LAVA_BUCKET);
            byte[] emptyBucket = readOptional(resourceManager, EMPTY_BUCKET);

            for (MetalDefinition definition : MoltenMetalRegistry.definitions()) {
                try {
                    ResolvedTexture source = resolveMetalTexture(resourceManager, definition);
                    byte[] metalTexture = source.bytes();
                    String signature = signature(metalTexture, lavaStill, lavaFlow, lavaStillMeta, lavaFlowMeta,
                            lavaBucket, emptyBucket);
                    Path signaturePath = GeneratedTexturePack.signaturePath(definition);
                    Path stillPath = GeneratedTexturePack.texturePath(definition, false);
                    Path flowPath = GeneratedTexturePack.texturePath(definition, true);
                    Path bucketPath = GeneratedTexturePack.bucketTexturePath(definition);

                    boolean upToDate = Files.exists(stillPath)
                            && Files.exists(flowPath)
                            && Files.exists(bucketPath)
                            && Files.exists(signaturePath)
                            && signature.equals(Files.readString(signaturePath, StandardCharsets.UTF_8));

                    if (upToDate) {
                        continue;
                    }

                    int[] palette = extractPalette(metalTexture);
                    remapLava(lavaStill, palette, stillPath);
                    remapLava(lavaFlow, palette, flowPath);
                    remapBucket(lavaBucket, emptyBucket, palette, bucketPath);
                    writeMetadata(GeneratedTexturePack.metadataPath(definition, false), lavaStillMeta);
                    writeMetadata(GeneratedTexturePack.metadataPath(definition, true), lavaFlowMeta);
                    Files.createDirectories(signaturePath.getParent());
                    Files.writeString(signaturePath, signature, StandardCharsets.UTF_8);
                    MCMoltenMetals.LOGGER.info("Generated molten {} assets from {}",
                            definition.id(), source.location());
                    changed = true;
                } catch (Exception exception) {
                    MCMoltenMetals.LOGGER.error("Failed to generate molten {} assets", definition.id(), exception);
                }
            }
        } catch (Exception exception) {
            MCMoltenMetals.LOGGER.error("Failed to prepare lava template assets", exception);
        }

        return changed;
    }

    private static ResolvedTexture resolveMetalTexture(ResourceManager manager, MetalDefinition definition) throws IOException {
        for (ResourceLocation itemId : definition.sourceItems()) {
            ResourceLocation texture = resolveItemModelTexture(manager, itemId, new HashSet<>());
            if (texture != null) {
                var resource = manager.getResource(texture);
                if (resource.isPresent()) {
                    try (InputStream input = resource.get().open()) {
                        return new ResolvedTexture(texture, input.readAllBytes());
                    }
                }
            }

            ResourceLocation fallback = ResourceLocation.fromNamespaceAndPath(
                    itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
            var fallbackResource = manager.getResource(fallback);
            if (fallbackResource.isPresent()) {
                try (InputStream input = fallbackResource.get().open()) {
                    return new ResolvedTexture(fallback, input.readAllBytes());
                }
            }
        }
        throw new IOException("No item texture found for " + definition.id() + " from " + definition.sourceItems());
    }

    private static ResourceLocation resolveItemModelTexture(
            ResourceManager manager, ResourceLocation itemId, Set<ResourceLocation> visited) {
        ResourceLocation model = ResourceLocation.fromNamespaceAndPath(
                itemId.getNamespace(), "models/item/" + itemId.getPath() + ".json");
        return resolveModelTexture(manager, model, visited);
    }

    private static ResourceLocation resolveModelTexture(
            ResourceManager manager, ResourceLocation model, Set<ResourceLocation> visited) {
        if (!visited.add(model)) {
            return null;
        }
        try {
            var resource = manager.getResource(model);
            if (resource.isEmpty()) {
                return null;
            }
            JsonObject root;
            try (InputStream input = resource.get().open()) {
                root = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }

            if (root.has("textures") && root.get("textures").isJsonObject()) {
                JsonObject textures = root.getAsJsonObject("textures");
                if (textures.has("layer0") && textures.get("layer0").isJsonPrimitive()) {
                    ResourceLocation texture = textureLocation(textures.get("layer0").getAsString(), model.getNamespace());
                    if (texture != null) {
                        return texture;
                    }
                }
                for (var entry : textures.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        ResourceLocation texture = textureLocation(value.getAsString(), model.getNamespace());
                        if (texture != null) {
                            return texture;
                        }
                    }
                }
            }

            if (root.has("parent") && root.get("parent").isJsonPrimitive()) {
                ResourceLocation parent = modelReference(root.get("parent").getAsString(), model.getNamespace());
                if (parent != null) {
                    return resolveModelTexture(manager, parent, visited);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ResourceLocation textureLocation(String reference, String defaultNamespace) {
        if (reference.startsWith("#")) {
            return null;
        }
        ResourceLocation id = parseLocation(reference, defaultNamespace);
        if (id == null) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
    }

    private static ResourceLocation modelReference(String reference, String defaultNamespace) {
        ResourceLocation id = parseLocation(reference, defaultNamespace);
        if (id == null) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "models/" + id.getPath() + ".json");
    }

    private static ResourceLocation parseLocation(String value, String defaultNamespace) {
        try {
            int colon = value.indexOf(':');
            if (colon >= 0) {
                return ResourceLocation.fromNamespaceAndPath(value.substring(0, colon), value.substring(colon + 1));
            }
            return ResourceLocation.fromNamespaceAndPath(defaultNamespace, value);
        } catch (Exception exception) {
            return null;
        }
    }

    private static int[] extractPalette(byte[] imageBytes) throws IOException {
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes))) {
            List<Integer> colors = new ArrayList<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    if (alpha(pixel) >= 32) {
                        colors.add(rgb(pixel));
                    }
                }
            }
            if (colors.isEmpty()) {
                throw new IOException("Source metal texture contains no opaque pixels");
            }
            colors.sort(Comparator.comparingDouble(MoltenTextureGenerator::luminance));
            int paletteSize = Math.min(PALETTE_SIZE, colors.size());
            int[] palette = new int[paletteSize];
            if (paletteSize == 1) {
                palette[0] = colors.get(colors.size() / 2);
                return palette;
            }
            for (int i = 0; i < paletteSize; i++) {
                int index = Math.round(i * (colors.size() - 1F) / (paletteSize - 1F));
                palette[i] = colors.get(index);
            }
            return palette;
        }
    }

    private static void remapLava(byte[] lavaBytes, int[] palette, Path outputPath) throws IOException {
        try (NativeImage lava = NativeImage.read(new ByteArrayInputStream(lavaBytes));
             NativeImage output = new NativeImage(NativeImage.Format.RGBA, lava.getWidth(), lava.getHeight(), false)) {
            double[] range = luminanceRange(lava, null);
            for (int y = 0; y < lava.getHeight(); y++) {
                for (int x = 0; x < lava.getWidth(); x++) {
                    int lavaPixel = lava.getPixelRGBA(x, y);
                    int a = alpha(lavaPixel);
                    if (a == 0) {
                        output.setPixelRGBA(x, y, 0);
                        continue;
                    }
                    double normalized = normalizeLuminance(luminance(rgb(lavaPixel)), range);
                    int remapped = paletteColor(palette, normalized);
                    output.setPixelRGBA(x, y, packAbgr(a, red(remapped), green(remapped), blue(remapped)));
                }
            }
            Files.createDirectories(outputPath.getParent());
            output.writeToFile(outputPath);
        }
    }

    private static void remapBucket(byte[] lavaBucketBytes, byte[] emptyBucketBytes, int[] palette, Path outputPath)
            throws IOException {
        try (NativeImage lavaBucket = NativeImage.read(new ByteArrayInputStream(lavaBucketBytes));
             NativeImage output = new NativeImage(NativeImage.Format.RGBA, lavaBucket.getWidth(), lavaBucket.getHeight(), false)) {
            NativeImage emptyBucket = null;
            if (emptyBucketBytes.length > 0) {
                try {
                    emptyBucket = NativeImage.read(new ByteArrayInputStream(emptyBucketBytes));
                    if (emptyBucket.getWidth() != lavaBucket.getWidth() || emptyBucket.getHeight() != lavaBucket.getHeight()) {
                        emptyBucket.close();
                        emptyBucket = null;
                    }
                } catch (Exception ignored) {
                    emptyBucket = null;
                }
            }

            boolean[] mask = new boolean[lavaBucket.getWidth() * lavaBucket.getHeight()];
            for (int y = 0; y < lavaBucket.getHeight(); y++) {
                for (int x = 0; x < lavaBucket.getWidth(); x++) {
                    int lavaPixel = lavaBucket.getPixelRGBA(x, y);
                    boolean recolor;
                    if (emptyBucket != null) {
                        recolor = alpha(lavaPixel) > 0 && lavaPixel != emptyBucket.getPixelRGBA(x, y);
                    } else {
                        int color = rgb(lavaPixel);
                        recolor = alpha(lavaPixel) > 0 && red(color) > blue(color) * 1.35 && green(color) > blue(color) * 1.15;
                    }
                    mask[y * lavaBucket.getWidth() + x] = recolor;
                }
            }

            double[] range = luminanceRange(lavaBucket, mask);
            for (int y = 0; y < lavaBucket.getHeight(); y++) {
                for (int x = 0; x < lavaBucket.getWidth(); x++) {
                    int lavaPixel = lavaBucket.getPixelRGBA(x, y);
                    if (!mask[y * lavaBucket.getWidth() + x]) {
                        output.setPixelRGBA(x, y, lavaPixel);
                        continue;
                    }
                    double normalized = normalizeLuminance(luminance(rgb(lavaPixel)), range);
                    int remapped = paletteColor(palette, normalized);
                    output.setPixelRGBA(x, y,
                            packAbgr(alpha(lavaPixel), red(remapped), green(remapped), blue(remapped)));
                }
            }
            if (emptyBucket != null) {
                emptyBucket.close();
            }
            Files.createDirectories(outputPath.getParent());
            output.writeToFile(outputPath);
        }
    }

    private static double[] luminanceRange(NativeImage image, boolean[] mask) {
        double min = 1.0;
        double max = 0.0;
        boolean found = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (mask != null && !mask[y * image.getWidth() + x]) {
                    continue;
                }
                int pixel = image.getPixelRGBA(x, y);
                if (alpha(pixel) == 0) {
                    continue;
                }
                double value = luminance(rgb(pixel));
                min = Math.min(min, value);
                max = Math.max(max, value);
                found = true;
            }
        }
        return found ? new double[] {min, max} : new double[] {0.0, 1.0};
    }

    private static double normalizeLuminance(double value, double[] range) {
        return (value - range[0]) / Math.max(0.0001, range[1] - range[0]);
    }

    private static int paletteColor(int[] palette, double normalized) {
        if (palette.length == 1) {
            return palette[0];
        }
        double position = Math.max(0.0, Math.min(1.0, normalized)) * (palette.length - 1);
        int low = (int) Math.floor(position);
        int high = Math.min(palette.length - 1, low + 1);
        double amount = position - low;
        int a = palette[low];
        int b = palette[high];
        return (lerp(red(a), red(b), amount) << 16)
                | (lerp(green(a), green(b), amount) << 8)
                | lerp(blue(a), blue(b), amount);
    }

    private static int lerp(int a, int b, double amount) {
        return (int) Math.round(a + (b - a) * amount);
    }

    private static void writeMetadata(Path path, byte[] metadata) throws IOException {
        if (metadata.length == 0) {
            Files.deleteIfExists(path);
            return;
        }
        Files.createDirectories(path.getParent());
        Files.write(path, metadata);
    }

    private static byte[] readRequired(ResourceManager manager, ResourceLocation location) throws IOException {
        Resource resource = manager.getResource(location)
                .orElseThrow(() -> new IOException("Missing resource " + location));
        try (InputStream input = resource.open()) {
            return input.readAllBytes();
        }
    }

    private static byte[] readOptional(ResourceManager manager, ResourceLocation location) throws IOException {
        var resource = manager.getResource(location);
        if (resource.isEmpty()) {
            return new byte[0];
        }
        try (InputStream input = resource.get().open()) {
            return input.readAllBytes();
        }
    }

    private static String signature(byte[]... resources) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(ALGORITHM_VERSION.getBytes(StandardCharsets.UTF_8));
        for (byte[] resource : resources) {
            digest.update(resource);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static int alpha(int abgr) {
        return (abgr >>> 24) & 0xFF;
    }

    private static int rgb(int abgr) {
        return (redAbgr(abgr) << 16) | (greenAbgr(abgr) << 8) | blueAbgr(abgr);
    }

    private static int redAbgr(int abgr) {
        return abgr & 0xFF;
    }

    private static int greenAbgr(int abgr) {
        return (abgr >>> 8) & 0xFF;
    }

    private static int blueAbgr(int abgr) {
        return (abgr >>> 16) & 0xFF;
    }

    private static int red(int rgb) {
        return (rgb >>> 16) & 0xFF;
    }

    private static int green(int rgb) {
        return (rgb >>> 8) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    private static int packAbgr(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private static double luminance(int rgb) {
        return (0.2126 * red(rgb) + 0.7152 * green(rgb) + 0.0722 * blue(rgb)) / 255.0;
    }

    private static ResourceLocation minecraft(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }

    private record ResolvedTexture(ResourceLocation location, byte[] bytes) {
    }

    public static final class ReloadListener extends SimplePreparableReloadListener<Boolean> {
        @Override
        protected Boolean prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            return generate(resourceManager);
        }

        @Override
        protected void apply(Boolean changed, ResourceManager resourceManager, ProfilerFiller profiler) {
            if (changed) {
                RELOAD_REQUESTED.set(true);
            }
        }
    }
}
