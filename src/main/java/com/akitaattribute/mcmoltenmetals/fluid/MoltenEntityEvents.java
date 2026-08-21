package com.akitaattribute.mcmoltenmetals.fluid;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Molten metals are injected into minecraft:lava by the generated data pack, so vanilla's
 * own lava-contact path is authoritative for entity damage, fire, and fall-distance behavior.
 * This listener only verifies that the generated tag binding is present after a tag reload.
 */
public final class MoltenEntityEvents {
    private MoltenEntityEvents() {
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
                            + "Vanilla tag-driven lava behavior may differ until the tag is restored.",
                    missing);
        }
    }
}
