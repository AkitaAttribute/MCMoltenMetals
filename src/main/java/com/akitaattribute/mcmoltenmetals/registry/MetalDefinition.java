package com.akitaattribute.mcmoltenmetals.registry;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import net.minecraft.resources.ResourceLocation;

public enum MetalDefinition {
    IRON("iron", "Iron", minecraftTexture("textures/item/iron_ingot.png")),
    COPPER("copper", "Copper", minecraftTexture("textures/item/copper_ingot.png")),
    GOLD("gold", "Gold", minecraftTexture("textures/item/gold_ingot.png")),
    NETHERITE("netherite", "Netherite", minecraftTexture("textures/item/netherite_ingot.png"));

    private final String id;
    private final String displayName;
    private final ResourceLocation sourceTexture;

    MetalDefinition(String id, String displayName, ResourceLocation sourceTexture) {
        this.id = id;
        this.displayName = displayName;
        this.sourceTexture = sourceTexture;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String moltenName() {
        return "molten_" + id;
    }

    public ResourceLocation sourceTexture() {
        return sourceTexture;
    }

    public ResourceLocation stillTexture() {
        return MCMoltenMetals.id("fluid/" + moltenName() + "_still");
    }

    public ResourceLocation flowingTexture() {
        return MCMoltenMetals.id("fluid/" + moltenName() + "_flow");
    }

    private static ResourceLocation minecraftTexture(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }
}
