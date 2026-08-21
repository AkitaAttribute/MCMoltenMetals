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
 * NeoForge 1.21.1 tracks custom FluidTypes separately from its built-in lava FluidType.
 * Consequently Entity#isInLava() does not become true merely because a custom fluid is in
 * minecraft:lava. Use NeoForge's already-computed fluid-height intersection for molten damage,
 * while leaving movement and fall-distance handling to the normal FluidType path.
 */
public final class MoltenEntityEvents {
    private MoltenEntityEvents() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        boolean touchingMolten = entity.isInFluidType(
                (fluidType, height) -> fluidType instanceof MoltenFluidType && height > 0.0D,
                false);

        if (touchingMolten) {
            entity.lavaHurt();
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
                            + "Direct molten-contact damage still works, but other tag-driven lava behavior may differ.",
                    missing);
        }
    }
}
