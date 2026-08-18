package com.akitaattribute.mcmoltenmetals.registry;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record MetalDefinition(String id, String displayName, List<ResourceLocation> sourceItems) {
    public MetalDefinition {
        sourceItems = List.copyOf(sourceItems);
    }

    public String moltenName() {
        return "molten_" + id;
    }

    public String flowingName() {
        return "flowing_" + moltenName();
    }

    public ResourceLocation stillTexture() {
        return MCMoltenMetals.id("fluid/" + moltenName() + "_still");
    }

    public ResourceLocation flowingTexture() {
        return MCMoltenMetals.id("fluid/" + moltenName() + "_flow");
    }

    public ResourceLocation bucketTexture() {
        return MCMoltenMetals.id("item/" + moltenName() + "_bucket");
    }
}
