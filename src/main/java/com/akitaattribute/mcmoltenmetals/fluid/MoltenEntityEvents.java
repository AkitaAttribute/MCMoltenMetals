package com.akitaattribute.mcmoltenmetals.fluid;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Vanilla's entity lava contact path is keyed off the minecraft:lava fluid tag rather than
 * the LavaFluid class itself. Molten metals are also injected into that tag by the generated
 * data pack, but this FluidType check keeps vanilla lava contact semantics intact even if a
 * server/resource reload has not rebound that generated tag yet.
 */
public final class MoltenEntityEvents {
    private MoltenEntityEvents() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();

        // If vanilla already recognizes it as lava, baseTick already handled lava contact.
        if (entity.isInLava()) {
            return;
        }

        boolean inMoltenMetal = entity.isInFluidType(
                (fluidType, height) -> fluidType instanceof MoltenFluidType && height > 0.0D,
                false);

        if (inMoltenMetal) {
            // Use Minecraft's own lava damage/fire implementation rather than duplicating values.
            entity.lavaHurt();
            // Match vanilla baseTick's reduction while an entity is in lava.
            entity.fallDistance *= 0.5F;
        }
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!event.shouldUpdateStaticData()) {
            return;
        }

        List<String> missing = new ArrayList<>();
        for (MoltenMetalRegistry.MoltenMetal metal : MoltenMetalRegistry.metals()) {
            boolean sourceTagged = metal.source().get().defaultFluidState().is(FluidTags.LAVA);
            boolean flowingTagged = metal.flowing().get().defaultFluidState().is(FluidTags.LAVA);
            if (!sourceTagged || !flowingTagged) {
                missing.add(metal.definition().id());
            }
        }

        if (!missing.isEmpty()) {
            MCMoltenMetals.LOGGER.warn(
                    "Molten fluids missing from minecraft:lava after tag reload: {}. "
                            + "Direct lava-contact fallback remains active, but other tag-driven lava behavior may differ.",
                    missing);
        }
    }
}
