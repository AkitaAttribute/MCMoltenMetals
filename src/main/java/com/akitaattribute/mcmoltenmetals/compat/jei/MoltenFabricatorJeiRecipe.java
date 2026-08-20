package com.akitaattribute.mcmoltenmetals.compat.jei;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/** JEI-only presentation of one Molten Fabricator operation. */
public record MoltenFabricatorJeiRecipe(
        Kind kind,
        String metal,
        List<ItemStack> primaryItems,
        List<ItemStack> selectors,
        @Nullable Fluid inputFluid,
        long inputFluidAmount,
        boolean diorite,
        @Nullable Fluid outputFluid,
        long outputFluidAmount,
        ItemStack outputItem) {
    public MoltenFabricatorJeiRecipe {
        primaryItems = List.copyOf(primaryItems);
        selectors = List.copyOf(selectors);
        outputItem = outputItem.copy();
    }

    public enum Kind { LAVA_TO_MOLTEN, RAW_TO_MOLTEN, MOLTEN_TO_INGOT, STEELMAKING }
}
