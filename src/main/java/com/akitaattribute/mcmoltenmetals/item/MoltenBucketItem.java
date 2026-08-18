package com.akitaattribute.mcmoltenmetals.item;

import com.akitaattribute.mcmoltenmetals.registry.MetalDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

public final class MoltenBucketItem extends BucketItem {
    private final MetalDefinition definition;

    public MoltenBucketItem(Fluid fluid, MetalDefinition definition) {
        super(fluid, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));
        this.definition = definition;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Molten " + definition.displayName() + " Bucket");
    }
}
