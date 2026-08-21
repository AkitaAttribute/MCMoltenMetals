package com.akitaattribute.mcmoltenmetals.worldgen;

import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Creates one enclosed, Lush-Caves-sized pool. One discovered molten metal is selected once
 * per placement, so a visually distinct pool never mixes metal identities by construction.
 */
public final class MoltenPoolFeature extends Feature<NoneFeatureConfiguration> {
    private static final int FLOOR_SEARCH = 12;
    private static final int MIN_POOL_CELLS = 12;
    private static final int[][] HORIZONTAL_NEIGHBORS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    public MoltenPoolFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        List<MoltenMetalRegistry.MoltenMetal> metals = MoltenMetalRegistry.metals();
        if (metals.isEmpty()) {
            return false;
        }

        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos floor = findFloor(level, context.origin());
        if (floor == null) {
            return false;
        }

        int radiusX = 4 + random.nextInt(4);
        int radiusZ = 4 + random.nextInt(4);
        int y = floor.getY();

        // Keep pools distinct. In particular, do not let a newly selected molten metal touch
        // vanilla lava or a previously generated molten pool and merge into a mixed shoreline.
        if (hasNearbyFluid(level, floor, radiusX + 2, radiusZ + 2)) {
            return false;
        }

        List<BlockPos> pool = new ArrayList<>();
        List<BlockPos> bank = new ArrayList<>();

        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                double normalized = (dx * dx) / (double) (radiusX * radiusX)
                        + (dz * dz) / (double) (radiusZ * radiusZ);
                if (normalized > 1.0) {
                    continue;
                }

                BlockPos surface = new BlockPos(floor.getX() + dx, y, floor.getZ() + dz);
                if (normalized <= 0.58) {
                    if (!canCarvePoolCell(level, surface)) {
                        continue;
                    }
                    pool.add(surface);
                } else if (canBankCell(level, surface)) {
                    bank.add(surface);
                }
            }
        }

        if (pool.size() < MIN_POOL_CELLS) {
            return false;
        }

        // Refuse fragmented footprints. Every pool edge must have a solid bank at the fluid
        // level so the lava-like molten fluid cannot immediately escape into the cave.
        for (BlockPos pos : pool) {
            for (int[] offset : HORIZONTAL_NEIGHBORS) {
                BlockPos neighbor = pos.offset(offset[0], 0, offset[1]);
                if (!pool.contains(neighbor) && !bank.contains(neighbor)) {
                    return false;
                }
            }
        }

        BlockState soulSand = Blocks.SOUL_SAND.defaultBlockState();
        BlockState molten = metals.get(random.nextInt(metals.size()))
                .source().get().defaultFluidState().createLegacyBlock();

        for (BlockPos pos : bank) {
            level.setBlock(pos, soulSand, 2);
        }
        for (BlockPos pos : pool) {
            level.setBlock(pos.below(), soulSand, 2);
            level.setBlock(pos, molten, 2);
        }
        return true;
    }

    private static BlockPos findFloor(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        for (int i = 0; i <= FLOOR_SEARCH; i++) {
            BlockState floor = level.getBlockState(cursor);
            if (canReplaceTerrain(floor)
                    && floor.isCollisionShapeFullBlock(level, cursor)
                    && level.getBlockState(cursor.above()).isAir()) {
                return cursor.immutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }

    private static boolean hasNearbyFluid(WorldGenLevel level, BlockPos center, int radiusX, int radiusZ) {
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (!level.getFluidState(center.offset(dx, dy, dz)).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean canCarvePoolCell(WorldGenLevel level, BlockPos pos) {
        BlockState surface = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        BlockState below = level.getBlockState(pos.below());
        return canReplaceTerrain(surface)
                && canReplaceTerrain(below)
                && above.isAir()
                && surface.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean canBankCell(WorldGenLevel level, BlockPos pos) {
        BlockState surface = level.getBlockState(pos);
        return canReplaceTerrain(surface)
                && level.getBlockState(pos.above()).isAir()
                && surface.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean canReplaceTerrain(BlockState state) {
        return state.is(Blocks.NETHERRACK)
                || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.BASALT)
                || state.is(Blocks.CRIMSON_NYLIUM)
                || state.is(Blocks.WARPED_NYLIUM);
    }
}
