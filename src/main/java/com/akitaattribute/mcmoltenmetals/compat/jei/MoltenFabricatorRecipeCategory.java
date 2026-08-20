package com.akitaattribute.mcmoltenmetals.compat.jei;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.simulation.MoltenFabricatorMachine;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * JEI category for the Molten Fabricator's real copper/iron production operations.
 */
public final class MoltenFabricatorRecipeCategory implements IRecipeCategory<MoltenFabricatorJeiRecipe> {
    public static final IRecipeType<MoltenFabricatorJeiRecipe> RECIPE_TYPE =
            IRecipeType.create(MCMoltenMetals.MOD_ID, "molten_fabricator", MoltenFabricatorJeiRecipe.class);

    @Override
    public IRecipeType<MoltenFabricatorJeiRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("container.mcmoltenmetals.molten_fabricator");
    }

    @Nullable
    @Override
    public IDrawable getIcon() {
        // JEI uses the registered Molten Fabricator crafting station as the category icon.
        return null;
    }

    @Override
    public int getWidth() {
        return 132;
    }

    @Override
    public int getHeight() {
        return 48;
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            MoltenFabricatorJeiRecipe recipe,
            IFocusGroup focuses) {
        builder.addInputSlot(5, 16)
                .setStandardSlotBackground()
                .addFluidStack(Fluids.LAVA, MoltenFabricatorMachine.LAVA_PER_OPERATION_MB)
                .setFluidRenderer(MoltenFabricatorMachine.LAVA_PER_OPERATION_MB, true, 16, 16);

        builder.addInputSlot(34, 16)
                .setStandardSlotBackground()
                .addItemStack(new ItemStack(Blocks.DIORITE));

        // The selector is required but is never consumed by the Fabricator.
        builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 63, 16)
                .setStandardSlotBackground()
                .addItemStacks(recipe.selectors());

        builder.addOutputSlot(108, 16)
                .setOutputSlotBackground()
                .addFluidStack(recipe.outputFluid(), MoltenFabricatorMachine.OUTPUT_PER_OPERATION_MB)
                .setFluidRenderer(MoltenFabricatorMachine.OUTPUT_PER_OPERATION_MB, true, 16, 16);
    }

    @Nullable
    @Override
    public ResourceLocation getRegistryName(MoltenFabricatorJeiRecipe recipe) {
        return MCMoltenMetals.id("molten_fabricator/" + recipe.metal());
    }
}
