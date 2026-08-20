package com.akitaattribute.mcmoltenmetals.compat.jei;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.compat.MekanismCompat;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.akitaattribute.mcmoltenmetals.simulation.MoltenFabricatorMachine;
import com.akitaattribute.mcmoltenmetals.simulation.MoltenFabricatorRecipes;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

@JeiPlugin
public final class MoltenMetalsJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID = MCMoltenMetals.id("jei");

    @Override public ResourceLocation getPluginUid() { return PLUGIN_UID; }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (fabricatorAvailable()) registration.addRecipeCategories(new MoltenFabricatorRecipeCategory());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!fabricatorAvailable()) return;
        List<MoltenFabricatorJeiRecipe> recipes = new ArrayList<>();

        for (String metal : List.of("copper", "iron")) {
            MoltenMetalRegistry.find(metal).ifPresent(molten -> {
                List<ItemStack> selectors = MoltenFabricatorRecipes.selectorStacks(metal);
                if (MoltenFabricatorRecipes.lavaRecipeAllowed(metal) && !selectors.isEmpty()) {
                    recipes.add(new MoltenFabricatorJeiRecipe(
                            MoltenFabricatorJeiRecipe.Kind.LAVA_TO_MOLTEN, metal, List.of(), selectors,
                            Fluids.LAVA, MoltenFabricatorMachine.LAVA_PER_OPERATION_MB, true,
                            molten.source().get(), MoltenFabricatorMachine.OUTPUT_PER_OPERATION_MB, ItemStack.EMPTY));
                }
            });
        }

        for (MoltenMetalRegistry.MoltenMetal molten : MoltenMetalRegistry.metals()) {
            String metal = molten.definition().id();
            List<ItemStack> raws = MoltenFabricatorRecipes.rawStacks(metal);
            if (MoltenFabricatorRecipes.rawRecipeAllowed(metal) && !raws.isEmpty()) {
                recipes.add(new MoltenFabricatorJeiRecipe(
                        MoltenFabricatorJeiRecipe.Kind.RAW_TO_MOLTEN, metal, raws, List.of(),
                        null, 0, true, molten.source().get(), MoltenFabricatorMachine.RAW_OUTPUT_MB, ItemStack.EMPTY));
            }
            if (MoltenFabricatorRecipes.castingRecipeAllowed(metal)) {
                MoltenFabricatorRecipes.findIngotItem(metal).ifPresent(item -> recipes.add(new MoltenFabricatorJeiRecipe(
                        MoltenFabricatorJeiRecipe.Kind.MOLTEN_TO_INGOT, metal, List.of(), List.of(),
                        molten.source().get(), MoltenFabricatorMachine.INGOT_MOLTEN_MB, false,
                        null, 0, new ItemStack(item))));
            }
        }

        if (MoltenFabricatorRecipes.steelmakingAllowed()) {
            MoltenMetalRegistry.find("iron").ifPresent(iron -> MoltenFabricatorRecipes.findIngotItem("steel").ifPresent(steel ->
                    recipes.add(new MoltenFabricatorJeiRecipe(
                            MoltenFabricatorJeiRecipe.Kind.STEELMAKING, "iron_to_steel", List.of(), List.of(),
                            iron.source().get(), MoltenFabricatorMachine.INGOT_MOLTEN_MB, false,
                            null, 0, new ItemStack(steel)))));
        }

        registration.addRecipes(MoltenFabricatorRecipeCategory.RECIPE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        MekanismCompat.fabricatorItem().ifPresent(item ->
                registration.addRecipeCatalysts(MoltenFabricatorRecipeCategory.RECIPE_TYPE, item));
    }

    private static boolean fabricatorAvailable() { return MekanismCompat.fabricatorItem().isPresent(); }
}
