package com.akitaattribute.mcmoltenmetals.energy;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Registry for power systems that explicitly support supplying consumers across unloaded game time.
 * Empty by default; ordinary loaded-chunk energy capabilities are intentionally not treated as
 * retroactively available.
 */
public final class OfflineEnergyProviders {
    private static final CopyOnWriteArrayList<OfflineEnergyProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private OfflineEnergyProviders() {
    }

    public static void register(OfflineEnergyProvider provider) {
        PROVIDERS.addIfAbsent(Objects.requireNonNull(provider, "provider"));
    }

    /**
     * Requests energy from registered unloaded-capable providers without loading source chunks.
     */
    public static long extract(
            ServerLevel level,
            BlockPos consumerPos,
            long fromGameTimeInclusive,
            long toGameTimeExclusive,
            long requestedFe) {
        if (requestedFe <= 0 || toGameTimeExclusive <= fromGameTimeInclusive) {
            return 0L;
        }

        long supplied = 0L;
        for (OfflineEnergyProvider provider : PROVIDERS) {
            long remaining = requestedFe - supplied;
            if (remaining <= 0) {
                break;
            }

            long granted;
            try {
                granted = provider.extractOfflineEnergy(
                        level,
                        consumerPos,
                        fromGameTimeInclusive,
                        toGameTimeExclusive,
                        remaining);
            } catch (RuntimeException exception) {
                MCMoltenMetals.LOGGER.error(
                        "Offline energy provider {} failed for consumer {}",
                        provider.getClass().getName(),
                        consumerPos,
                        exception);
                continue;
            }

            if (granted < 0 || granted > remaining) {
                MCMoltenMetals.LOGGER.warn(
                        "Offline energy provider {} returned {} FE for a request of {} FE; clamping the result",
                        provider.getClass().getName(),
                        granted,
                        remaining);
                granted = Math.max(0L, Math.min(granted, remaining));
            }
            supplied += granted;
        }
        return supplied;
    }
}
