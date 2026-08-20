package com.akitaattribute.mcmoltenmetals.simulation;

import com.akitaattribute.mcmoltenmetals.config.FabricatorRecipeConfig;
import com.akitaattribute.mcmoltenmetals.config.FabricatorRecipeConfig.Family;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Shared discovery/resolution rules used by the logical machine, loaded tile and JEI. */
public final class MoltenFabricatorRecipes {
    private static final Set<String> LAVA_SELECTOR_METALS = Set.of("copper", "iron");
    private static final Map<String, Optional<Item>> INGOT_CACHE = new ConcurrentHashMap<>();

    private MoltenFabricatorRecipes() {
    }

    public static Optional<MaterialInfo> identifyMaterial(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (String metal : MoltenMetalRegistry.metalNames()) {
            if (isRawItem(stack, metal) && rawRecipeAllowed(metal)) {
                return Optional.of(new MaterialInfo(itemId, metal, true));
            }
        }
        for (String metal : LAVA_SELECTOR_METALS) {
            if (lavaRecipeAllowed(metal) && (isIngotItem(stack, metal) || isRawItem(stack, metal))) {
                return Optional.of(new MaterialInfo(itemId, metal, isRawItem(stack, metal)));
            }
        }
        return Optional.empty();
    }

    public static Optional<MaterialInfo> identifyMaterial(String itemId) {
        return itemById(itemId).flatMap(item -> identifyMaterial(new ItemStack(item)));
    }

    public static boolean isAcceptedMaterial(ItemStack stack) {
        return identifyMaterial(stack).isPresent();
    }

    public static boolean lavaRecipeAllowed(String metal) {
        String normalized = normalize(metal);
        return LAVA_SELECTOR_METALS.contains(normalized)
                && MoltenMetalRegistry.find(normalized).isPresent()
                && FabricatorRecipeConfig.allows(Family.LAVA_TO_MOLTEN, normalized);
    }

    public static boolean rawRecipeAllowed(String metal) {
        String normalized = normalize(metal);
        return MoltenMetalRegistry.find(normalized).isPresent()
                && FabricatorRecipeConfig.allows(Family.RAW_TO_MOLTEN, normalized);
    }

    public static boolean castingRecipeAllowed(String metal) {
        String normalized = normalize(metal);
        return MoltenMetalRegistry.find(normalized).isPresent()
                && FabricatorRecipeConfig.allows(Family.MOLTEN_TO_INGOT, normalized)
                && findIngotItem(normalized).isPresent();
    }

    public static boolean steelmakingAllowed() {
        return MoltenMetalRegistry.find("iron").isPresent()
                && FabricatorRecipeConfig.allows(Family.STEELMAKING, "steel")
                && findIngotItem("steel").isPresent();
    }

    public static Optional<String> castingOutputItemId(String inputMetal, MoltenFabricatorMachine.CastMode mode) {
        String metal = normalize(inputMetal);
        if (metal.equals("iron") && mode == MoltenFabricatorMachine.CastMode.STEEL) {
            if (!steelmakingAllowed()) {
                return Optional.empty();
            }
            return findIngotItem("steel").map(item -> BuiltInRegistries.ITEM.getKey(item).toString());
        }
        if (!castingRecipeAllowed(metal)) {
            return Optional.empty();
        }
        return findIngotItem(metal).map(item -> BuiltInRegistries.ITEM.getKey(item).toString());
    }

    public static Optional<Item> findIngotItem(String metal) {
        String normalized = normalize(metal);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return INGOT_CACHE.computeIfAbsent(normalized, MoltenFabricatorRecipes::findIngotItemUncached);
    }

    private static Optional<Item> findIngotItemUncached(String normalized) {
        Optional<MoltenMetalRegistry.MoltenMetal> molten = MoltenMetalRegistry.find(normalized);
        if (molten.isPresent()) {
            for (ResourceLocation source : molten.get().definition().sourceItems()) {
                Item item = registeredItem(source);
                if (item != null && isIngotItem(new ItemStack(item), normalized)) {
                    return Optional.of(item);
                }
            }
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (isIngotItem(new ItemStack(item), normalized)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public static List<ItemStack> selectorStacks(String metal) {
        String normalized = normalize(metal);
        List<ItemStack> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (isIngotItem(stack, normalized) || isRawItem(stack, normalized)) {
                result.add(stack);
            }
        }
        return List.copyOf(result);
    }

    public static List<ItemStack> rawStacks(String metal) {
        if (!FabricatorRecipeConfig.allows(Family.RAW_TO_MOLTEN, metal)) {
            return List.of();
        }
        String normalized = normalize(metal);
        List<ItemStack> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (isRawItem(stack, normalized)) {
                result.add(new ItemStack(item, MoltenFabricatorMachine.RAW_PER_OPERATION));
            }
        }
        return List.copyOf(result);
    }

    public static boolean isAcceptedInputFluid(FluidStack stack) {
        return isAcceptedInputKey(inputFluidKey(stack));
    }

    public static boolean isAcceptedInputKey(String key) {
        String normalized = normalize(key);
        if (normalized.equals("lava")) {
            return lavaRecipeAllowed("copper") || lavaRecipeAllowed("iron");
        }
        return castingRecipeAllowed(normalized) || (normalized.equals("iron") && steelmakingAllowed());
    }

    public static String inputFluidKey(FluidStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        if (stack.getFluid() == Fluids.LAVA || stack.is(net.minecraft.tags.FluidTags.LAVA)) {
            return "lava";
        }
        for (MoltenMetalRegistry.MoltenMetal metal : MoltenMetalRegistry.metals()) {
            if (stack.getFluid() == metal.source().get() || stack.getFluid() == metal.flowing().get()) {
                return metal.definition().id();
            }
        }
        return "";
    }

    public static Optional<Fluid> fluidForKey(String key) {
        String normalized = normalize(key);
        if (normalized.equals("lava")) {
            return Optional.of(Fluids.LAVA);
        }
        return MoltenMetalRegistry.find(normalized).map(metal -> metal.source().get());
    }

    public static int maxStackSize(String itemId) {
        return itemById(itemId).map(item -> new ItemStack(item).getMaxStackSize()).orElse(64);
    }

    public static Optional<Item> itemById(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registeredItem(id));
    }

    private static Item registeredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return id.equals(BuiltInRegistries.ITEM.getKey(item)) ? item : null;
    }

    private static boolean isIngotItem(ItemStack stack, String metal) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(tag("c", "ingots/" + metal)) || stack.is(tag("forge", "ingots/" + metal))) {
            return true;
        }
        String file = fileName(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()).toLowerCase(Locale.ROOT);
        return file.equals(metal + "_ingot") || file.equals("ingot_" + metal);
    }

    private static boolean isRawItem(ItemStack stack, String metal) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(tag("c", "raw_materials/" + metal)) || stack.is(tag("forge", "raw_materials/" + metal))) {
            return true;
        }
        String file = fileName(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()).toLowerCase(Locale.ROOT);
        return file.equals("raw_" + metal) || file.equals(metal + "_raw");
    }

    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record MaterialInfo(String itemId, String metal, boolean raw) {
    }
}
