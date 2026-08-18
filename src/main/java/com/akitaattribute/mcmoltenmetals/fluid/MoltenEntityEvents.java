package com.akitaattribute.mcmoltenmetals.fluid;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Vanilla's entity lava damage path is keyed off the minecraft:lava fluid tag rather than
 * the LavaFluid class itself. Molten metals are also injected into that tag by the generated
 * data pack, but this FluidType check keeps vanilla lava damage semantics intact even if a
 * server/resource reload has not rebound that generated tag yet.
 */
public final class MoltenEntityEvents {
    private MoltenEntityEvents() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();

        // If vanilla already recognizes it as lava, baseTick has already called lavaHurt().
        if (entity.isInLava()) {
            return;
        }

        boolean inMoltenMetal = entity.isInFluidType(
                (fluidType, height) -> fluidType instanceof MoltenFluidType && height > 0.0D,
                false);

        if (inMoltenMetal) {
            // Use Minecraft's own lava damage/fire implementation rather than duplicating values.
            entity.lavaHurt();
        }
    }
}
