package com.akitaattribute.mcmoltenmetals.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Supplies Forge Energy for a consumer across an interval of elapsed server game time while
 * the consumer's chunk was unloaded.
 *
 * Implementations must not force-load chunks. Providers that can remain available while their
 * source/network is unloaded should resolve connectivity and available generation from persistent
 * or otherwise chunk-independent state.
 */
@FunctionalInterface
public interface OfflineEnergyProvider {
    /**
     * Attempts to transfer energy that was available during the supplied unloaded game-time window.
     *
     * @param level server level containing the consumer
     * @param consumerPos consumer block position
     * @param fromGameTimeInclusive first unloaded server game tick
     * @param toGameTimeExclusive first game tick after the unloaded interval
     * @param requestedFe maximum Forge Energy requested
     * @return Forge Energy actually supplied, between zero and {@code requestedFe}
     */
    long extractOfflineEnergy(
            ServerLevel level,
            BlockPos consumerPos,
            long fromGameTimeInclusive,
            long toGameTimeExclusive,
            long requestedFe);
}
