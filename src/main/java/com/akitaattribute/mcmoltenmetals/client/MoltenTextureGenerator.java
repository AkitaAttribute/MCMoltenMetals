package com.akitaattribute.mcmoltenmetals.client;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.registry.MetalDefinition;
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
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class MoltenTextureGenerator {
    private static final String ALGORITHM_VERSION = "lava-palette-remap-v1";
    private static final int PALETTE_SIZE = 12;
    private static final AtomicBoolean RELOAD_REQUESTED = new AtomicBoolean(false);

    private static final ResourceLocation LAVA_STILL = minecraft("textures/block/lava_still.png");
    private static final ResourceLocation LAVA_FLOW = minecraft("textures/block/lava_flow.png");
    private static final ResourceLocation LAVA_STILL_META = minecraft("textures/block/lava_still.png.mcmeta");
    private static final ResourceLocation LAVA_FLOW_META = minecraft("textures/block/lava_flow.png.mcmeta");

    private MoltenTextureGenerator() {
    }

    public static boolean consumeReloadRequest() {
        return RELOAD_REQUESTED.compareAndSet(true, false);
    }

    private static boolean generate(ResourceManager resourceManager) {
        boolean changed = false;

        try {
            GeneratedTexturePack.ensureSkeleton();
            byte[] lavaStill = readRequired(resourceManager, LAVA_STILL);
            byte[] lavaFlow = readRequired(resourceManager, LAVA_FLOW);
            byte[] lavaStillMeta = readOptional(resourceManager, LAVA_STILL_META);
            byte[] lavaFlowMeta = readOptional(resourceManager, LAVA_FLOW_META);

            for (MetalDefinition definition : MetalDefinition.values()) {
                try {
                    byte[] metalTexture = readRequired(resourceManager, definition.sourceTexture());
                    String signature = signature(metalTexture, lavaStill, lavaFlow, lavaStillMeta, lavaFlowMeta);
                    Path signaturePath = GeneratedTexturePack.signaturePath(definition);
                    Path stillPath = GeneratedTexturePack.texturePath(definition, false);
                    Path flowPath = GeneratedTexturePack.texturePath(definition, true);

                    boolean upToDate = Files.exists(stillPath)
                            && Files.exists(flowPath)
                            && Files.exists(signaturePath)
                            && signature.equals(Files.readString(signaturePath, StandardCharsets.UTF_8));

                    if (upToDate) {
                        continue;
                    }

                    int[] palette = extractPalette(metalTexture);
                    remapLava(lavaStill, palette, stillPath);
                    remapLava(lavaFlow, palette, flowPath);
                    writeMetadata(GeneratedTexturePack.metadataPath(definition, false), lavaStillMeta);
                    writeMetadata(GeneratedTexturePack.metadataPath(definition, true), lavaFlowMeta);
                    Files.createDirectories(signaturePath.getParent());
                    Files.writeString(signaturePath, signature, StandardCharsets.UTF_8);
                    MCMoltenMetals.LOGGER.info("Generated molten {} textures from {}",
                            definition.id(), definition.sourceTexture());
                    changed = true;
                } catch (Exception exception) {
                    MCMoltenMetals.LOGGER.error("Failed to generate molten {} textures", definition.id(), exception);
                }
            }
        } catch (Exception exception) {
            MCMoltenMetals.LOGGER.error("Failed to prepare lava source textures", exception);
        }

        return changed;
    }

    private static int[] extractPalette(byte[] imageBytes) throws IOException {
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes))) {
            List<Integer> colors = new ArrayList<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    if (alpha(pixel) < 32) {
                        continue;
                    }
                    colors.add(rgb(pixel));
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

            double minLuminance = 1.0;
            double maxLuminance = 0.0;
            for (int y = 0; y < lava.getHeight(); y++) {
                for (int x = 0; x < lava.getWidth(); x++) {
                    int pixel = lava.getPixelRGBA(x, y);
                    if (alpha(pixel) == 0) {
                        continue;
                    }
                    double l = luminance(rgb(pixel));
                    minLuminance = Math.min(minLuminance, l);
                    maxLuminance = Math.max(maxLuminance, l);
                }
            }

            double range = Math.max(0.0001, maxLuminance - minLuminance);
            for (int y = 0; y < lava.getHeight(); y++) {
                for (int x = 0; x < lava.getWidth(); x++) {
                    int lavaPixel = lava.getPixelRGBA(x, y);
                    int a = alpha(lavaPixel);
                    if (a == 0) {
                        output.setPixelRGBA(x, y, 0);
                        continue;
                    }

                    double normalized = (luminance(rgb(lavaPixel)) - minLuminance) / range;
                    int remapped = paletteColor(palette, normalized);
                    output.setPixelRGBA(x, y, packAbgr(a, red(remapped), green(remapped), blue(remapped)));
                }
            }

            Files.createDirectories(outputPath.getParent());
            output.writeToFile(outputPath);
        }
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
        int r = lerp(red(a), red(b), amount);
        int g = lerp(green(a), green(b), amount);
        int bl = lerp(blue(a), blue(b), amount);
        return (r << 16) | (g << 8) | bl;
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
