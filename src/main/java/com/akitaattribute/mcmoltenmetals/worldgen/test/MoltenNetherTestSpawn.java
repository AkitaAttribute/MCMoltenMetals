package com.akitaattribute.mcmoltenmetals.worldgen.test;

import com.akitaattribute.mcmoltenmetals.worldgen.MoltenWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * The test preset uses Nether terrain as the logical overworld so vanilla creates the player
 * there immediately. Vanilla's normal shared-spawn search can choose the bedrock roof for such
 * a generator; this relocates only that test preset to an ordinary two-block-high Nether cavity.
 */
public final class MoltenNetherTestSpawn {
    private static final int MAX_RADIUS = 64;
    private static final int MAX_Y = 120;
    private static final int MIN_Y = 24;

    private MoltenNetherTestSpawn() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        if (!level.dimensionTypeRegistration().is(MoltenWorldgen.TEST_DIMENSION_TYPE)) {
            return;
        }

        BlockPos current = level.getSharedSpawnPos();
        if (isSafe(level, current)) {
            return;
        }

        BlockPos safe = findSafeSpawn(level, current);
        if (safe != null) {
            level.setDefaultSpawnPos(safe, 0.0F);
        }
    }

    private static BlockPos findSafeSpawn(ServerLevel level, BlockPos center) {
        for (int radius = 0; radius <= MAX_RADIUS; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    for (int y = MAX_Y; y >= MIN_Y; y--) {
                        BlockPos feet = new BlockPos(x, y, z);
                        if (isSafe(level, feet)) {
                            return feet;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos floorPos = feet.below();
        BlockState floor = level.getBlockState(floorPos);
        return level.getBlockState(feet).isAir()
                && level.getBlockState(head).isAir()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(head).isEmpty()
                && !floor.is(Blocks.BEDROCK)
                && floor.isCollisionShapeFullBlock(level, floorPos);
    }
}
