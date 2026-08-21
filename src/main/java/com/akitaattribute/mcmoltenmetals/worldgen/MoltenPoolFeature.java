package com.akitaattribute.mcmoltenmetals.worldgen;

import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Creates a small irregular molten basin adapted to an existing Nether cave floor.
 * One discovered molten metal is selected once per successful placement, so each
 * connected pool retains a single molten-fluid identity.
 */
public final class MoltenPoolFeature extends Feature<NoneFeatureConfiguration> {
    private static final int FLOOR_SEARCH = 28;
    private static final int LOCAL_FLOOR_RANGE = 3;
    private static final int MIN_POOL_CELLS = 5;
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
        BlockPos centerFloor = findNearestFloor(level, context.origin());
        if (centerFloor == null) {
            return false;
        }

        // Small pools fit Nether cave floors much more reliably than the previous 8-14 block
        // perfect ellipse. The footprint is still broad enough to read as a distinct pool.
        int radiusX = 2 + random.nextInt(3);
        int radiusZ = 2 + random.nextInt(3);
        int poolY = centerFloor.getY();

        Set<BlockPos> candidates = new HashSet<>();
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                double normalized = (dx * dx) / (double) (radiusX * radiusX)
                        + (dz * dz) / (double) (radiusZ * radiusZ);
                double irregularEdge = 0.58D + random.nextDouble() * 0.18D;
                if (normalized > irregularEdge) {
                    continue;
                }

                BlockPos pos = new BlockPos(centerFloor.getX() + dx, poolY, centerFloor.getZ() + dz);
                if (canBuildColumn(level, pos)) {
                    candidates.add(pos);
                }
            }
        }

        Set<BlockPos> pool = largestConnectedComponent(candidates);
        if (pool.size() < MIN_POOL_CELLS) {
            return false;
        }

        // A pool cell is only retained when every exposed horizontal edge can receive a rim.
        // This lets the feature reshape uneven cave floors while still preventing lava-like
        // molten fluids from immediately escaping down an unsupported ledge.
        Set<BlockPos> unsupportedEdges = new HashSet<>();
        for (BlockPos pos : pool) {
            for (int[] offset : HORIZONTAL_NEIGHBORS) {
                BlockPos neighbor = pos.offset(offset[0], 0, offset[1]);
                if (!pool.contains(neighbor) && !canBuildColumn(level, neighbor)) {
                    unsupportedEdges.add(pos);
                    break;
                }
            }
        }
        pool.removeAll(unsupportedEdges);
        pool = largestConnectedComponent(pool);
        if (pool.size() < MIN_POOL_CELLS) {
            return false;
        }

        Set<BlockPos> rim = new HashSet<>();
        for (BlockPos pos : pool) {
            for (int[] offset : HORIZONTAL_NEIGHBORS) {
                BlockPos neighbor = pos.offset(offset[0], 0, offset[1]);
                if (!pool.contains(neighbor)) {
                    rim.add(neighbor);
                }
            }
        }

        // Only direct contact matters. The old implementation rejected an entire large area if
        // any fluid was within several blocks, which made normal Nether lava suppress almost all
        // molten-pool attempts.
        if (touchesExternalFluid(level, pool, rim)) {
            return false;
        }

        decorateGround(level, centerFloor, radiusX + 3, radiusZ + 3, random);

        BlockState soulSand = Blocks.SOUL_SAND.defaultBlockState();
        BlockState molten = metals.get(random.nextInt(metals.size()))
                .source().get().defaultFluidState().createLegacyBlock();

        for (BlockPos pos : rim) {
            clearReplaceableHeadroom(level, pos.above());
            level.setBlock(pos, soulSand, 2);
        }
        for (BlockPos pos : pool) {
            clearReplaceableHeadroom(level, pos.above());
            level.setBlock(pos.below(), soulSand, 2);
            level.setBlock(pos, molten, 2);
        }

        placeGlowstoneAccent(level, centerFloor, random);
        return true;
    }

    private static BlockPos findNearestFloor(WorldGenLevel level, BlockPos origin) {
        for (int distance = 0; distance <= FLOOR_SEARCH; distance++) {
            BlockPos down = origin.below(distance);
            if (isFloor(level, down)) {
                return down;
            }
            if (distance > 0) {
                BlockPos up = origin.above(distance);
                if (isFloor(level, up)) {
                    return up;
                }
            }
        }
        return null;
    }

    private static BlockPos findLocalFloor(WorldGenLevel level, int x, int z, int centerY) {
        for (int distance = 0; distance <= LOCAL_FLOOR_RANGE; distance++) {
            BlockPos down = new BlockPos(x, centerY - distance, z);
            if (isFloor(level, down)) {
                return down;
            }
            if (distance > 0) {
                BlockPos up = new BlockPos(x, centerY + distance, z);
                if (isFloor(level, up)) {
                    return up;
                }
            }
        }
        return null;
    }

    private static boolean isFloor(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return canReplaceTerrain(state)
                && state.isCollisionShapeFullBlock(level, pos)
                && level.getBlockState(pos.above()).isAir()
                && level.getFluidState(pos.above()).isEmpty();
    }

    private static boolean canBuildColumn(WorldGenLevel level, BlockPos pos) {
        BlockState at = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        BlockState above = level.getBlockState(pos.above());
        return (at.isAir() || canReplaceTerrain(at))
                && canReplaceTerrain(below)
                && below.isCollisionShapeFullBlock(level, pos.below())
                && (above.isAir() || canReplaceTerrain(above))
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();
    }

    private static Set<BlockPos> largestConnectedComponent(Set<BlockPos> candidates) {
        if (candidates.isEmpty()) {
            return new HashSet<>();
        }

        Set<BlockPos> remaining = new HashSet<>(candidates);
        Set<BlockPos> largest = new HashSet<>();
        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            Set<BlockPos> component = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            remaining.remove(seed);
            queue.add(seed);

            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                component.add(pos);
                for (int[] offset : HORIZONTAL_NEIGHBORS) {
                    BlockPos neighbor = pos.offset(offset[0], 0, offset[1]);
                    if (remaining.remove(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }

            if (component.size() > largest.size()) {
                largest = component;
            }
        }
        return largest;
    }

    private static boolean touchesExternalFluid(
            WorldGenLevel level, Set<BlockPos> pool, Set<BlockPos> rim) {
        for (BlockPos pos : pool) {
            if (!level.getFluidState(pos).isEmpty()) {
                return true;
            }
            for (int[] offset : HORIZONTAL_NEIGHBORS) {
                BlockPos neighbor = pos.offset(offset[0], 0, offset[1]);
                if (!pool.contains(neighbor)
                        && !rim.contains(neighbor)
                        && !level.getFluidState(neighbor).isEmpty()) {
                    return true;
                }
            }
        }
        for (BlockPos pos : rim) {
            if (!level.getFluidState(pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void decorateGround(
            WorldGenLevel level, BlockPos center, int radiusX, int radiusZ, RandomSource random) {
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                double normalized = (dx * dx) / (double) (radiusX * radiusX)
                        + (dz * dz) / (double) (radiusZ * radiusZ);
                if (normalized > 1.0D || random.nextFloat() > 0.72F) {
                    continue;
                }

                BlockPos floor = findLocalFloor(
                        level, center.getX() + dx, center.getZ() + dz, center.getY());
                if (floor != null) {
                    BlockState state = random.nextFloat() < 0.78F
                            ? Blocks.SOUL_SAND.defaultBlockState()
                            : Blocks.SOUL_SOIL.defaultBlockState();
                    level.setBlock(floor, state, 2);
                }
            }
        }
    }

    private static void clearReplaceableHeadroom(WorldGenLevel level, BlockPos pos) {
        if (canReplaceTerrain(level.getBlockState(pos))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void placeGlowstoneAccent(
            WorldGenLevel level, BlockPos centerFloor, RandomSource random) {
        if (random.nextFloat() > 0.45F) {
            return;
        }

        for (int dy = 2; dy <= 14; dy++) {
            BlockPos ceiling = centerFloor.above(dy);
            BlockState state = level.getBlockState(ceiling);
            if (state.isAir()) {
                continue;
            }
            if (canReplaceTerrain(state) && level.getBlockState(ceiling.below()).isAir()) {
                level.setBlock(ceiling.below(), Blocks.GLOWSTONE.defaultBlockState(), 2);
            }
            return;
        }
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
