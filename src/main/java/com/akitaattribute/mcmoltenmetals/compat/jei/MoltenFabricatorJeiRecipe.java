package com.akitaattribute.mcmoltenmetals.compat.jei;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * JEI-only presentation of one Molten Fabricator operation.
 *
 * The selector list is a crafting-station/non-consumed ingredient; the machine's real processing
 * rules continue to live in MoltenFabricatorMachine.
 */
public record MoltenFabricatorJeiRecipe(
        String metal,
        List<ItemStack> selectors,
        Fluid outputFluid) {

    public MoltenFabricatorJeiRecipe {
        selectors = List.copyOf(selectors);
    }
}
