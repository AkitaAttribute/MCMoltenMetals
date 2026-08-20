package com.akitaattribute.mcmoltenmetals.compat.jei;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.compat.MekanismCompat;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Optional JEI integration. This class contains no hard Mekanism references; the Fabricator item is
 * resolved through MekanismCompat only when the optional Mekanism integration is active.
 */
@JeiPlugin
public final class MoltenMetalsJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID = MCMoltenMetals.id("jei");
    private static final List<String> FABRICATOR_METALS = List.of("copper", "iron");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (fabricatorAvailable()) {
            registration.addRecipeCategories(new MoltenFabricatorRecipeCategory());
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!fabricatorAvailable()) {
            return;
        }

        List<MoltenFabricatorJeiRecipe> recipes = new ArrayList<>();
        for (String metalId : FABRICATOR_METALS) {
            MoltenMetalRegistry.find(metalId).ifPresent(metal -> {
                List<ItemStack> selectors = selectorStacks(metalId);
                if (!selectors.isEmpty()) {
                    recipes.add(new MoltenFabricatorJeiRecipe(
                            metalId,
                            selectors,
                            metal.source().get()));
                }
            });
        }
        registration.addRecipes(MoltenFabricatorRecipeCategory.RECIPE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        MekanismCompat.fabricatorItem().ifPresent(item ->
                registration.addCraftingStation(MoltenFabricatorRecipeCategory.RECIPE_TYPE, item));
    }

    private static boolean fabricatorAvailable() {
        return MekanismCompat.fabricatorItem().isPresent();
    }

    /**
     * Mirrors the selector acceptance rules used by MoltenFabricatorTile so JEI rotates through
     * every currently registered ingot/raw item that can act as the non-consumed selector.
     */
    private static List<ItemStack> selectorStacks(String metal) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty() && matchesMetalSelector(stack, metal)) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    private static boolean matchesMetalSelector(ItemStack stack, String metal) {
        String normalizedMetal = metal.toLowerCase(Locale.ROOT);
        if (stack.is(commonTag("c", "ingots/" + normalizedMetal))
                || stack.is(commonTag("c", "raw_materials/" + normalizedMetal))
                || stack.is(commonTag("forge", "ingots/" + normalizedMetal))
                || stack.is(commonTag("forge", "raw_materials/" + normalizedMetal))) {
            return true;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        int slash = path.lastIndexOf('/');
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        return fileName.equals(normalizedMetal + "_ingot") || fileName.equals("raw_" + normalizedMetal);
    }

    private static TagKey<Item> commonTag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
