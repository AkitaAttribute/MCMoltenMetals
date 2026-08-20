package com.akitaattribute.mcmoltenmetals.compat.jei;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class MoltenFabricatorRecipeCategory implements IRecipeCategory<MoltenFabricatorJeiRecipe> {
    public static final RecipeType<MoltenFabricatorJeiRecipe> RECIPE_TYPE =
            RecipeType.create(MCMoltenMetals.MOD_ID, "molten_fabricator", MoltenFabricatorJeiRecipe.class);

    @Override public RecipeType<MoltenFabricatorJeiRecipe> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return Component.translatable("container.mcmoltenmetals.molten_fabricator"); }
    @Nullable @Override public IDrawable getIcon() { return null; }
    @Override public int getWidth() { return 132; }
    @Override public int getHeight() { return 48; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MoltenFabricatorJeiRecipe recipe, IFocusGroup focuses) {
        if (recipe.inputFluid() != null) {
            builder.addInputSlot(5, 16).setStandardSlotBackground()
                    .addFluidStack(recipe.inputFluid(), recipe.inputFluidAmount())
                    .setFluidRenderer(recipe.inputFluidAmount(), true, 16, 16);
        } else if (!recipe.primaryItems().isEmpty()) {
            builder.addInputSlot(5, 16).setStandardSlotBackground().addItemStacks(recipe.primaryItems());
        }
        if (recipe.diorite()) {
            builder.addInputSlot(34, 16).setStandardSlotBackground().addItemStack(new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.DIORITE));
        }
        if (!recipe.selectors().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 63, 16).setStandardSlotBackground().addItemStacks(recipe.selectors());
        }
        if (recipe.outputFluid() != null) {
            builder.addOutputSlot(108, 16).setOutputSlotBackground()
                    .addFluidStack(recipe.outputFluid(), recipe.outputFluidAmount())
                    .setFluidRenderer(recipe.outputFluidAmount(), true, 16, 16);
        } else if (!recipe.outputItem().isEmpty()) {
            builder.addOutputSlot(108, 16).setOutputSlotBackground().addItemStack(recipe.outputItem());
        }
    }

    @Nullable
    @Override
    public ResourceLocation getRegistryName(MoltenFabricatorJeiRecipe recipe) {
        return MCMoltenMetals.id("molten_fabricator/" + recipe.kind().name().toLowerCase(java.util.Locale.ROOT) + '/' + recipe.metal());
    }
}
