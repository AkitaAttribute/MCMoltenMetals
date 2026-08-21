package com.akitaattribute.mcmoltenmetals.worldgen;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.Region;
import terrablender.api.RegionType;

/**
 * Production Nether integration for Molten Grotto.
 *
 * The region mirrors the five vanilla Nether climate anchors, but only claims the Soul Sand
 * Valley anchor. Every other anchor defers to TerraBlender's vanilla Nether region. Giving this
 * region the same weight as TerraBlender's vanilla Nether region therefore gives Molten Grotto
 * parity with Soul Sand Valley inside that climate territory without replacing the Nether source.
 */
public final class MoltenNetherRegion extends Region {
    public MoltenNetherRegion(int weight) {
        super(MCMoltenMetals.id("molten_nether"), RegionType.NETHER, weight);
    }

    @Override
    public void addBiomes(
            Registry<Biome> registry,
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        addBiome(mapper,
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                0.0F, DEFERRED_PLACEHOLDER);

        addBiome(mapper,
                Climate.Parameter.point(0.0F), Climate.Parameter.point(-0.5F),
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                0.0F, MoltenWorldgen.MOLTEN_GROTTO);

        addBiome(mapper,
                Climate.Parameter.point(0.4F), Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                0.0F, DEFERRED_PLACEHOLDER);

        addBiome(mapper,
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.5F),
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                0.375F, DEFERRED_PLACEHOLDER);

        addBiome(mapper,
                Climate.Parameter.point(-0.5F), Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
                0.175F, DEFERRED_PLACEHOLDER);
    }
}
