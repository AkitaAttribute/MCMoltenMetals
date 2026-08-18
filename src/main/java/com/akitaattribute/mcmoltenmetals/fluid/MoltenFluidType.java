package com.akitaattribute.mcmoltenmetals.fluid;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.registry.MetalDefinition;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;

public final class MoltenFluidType extends FluidType {
    private final MetalDefinition definition;

    public MoltenFluidType(MoltenMetalRegistry.MoltenMetal metal) {
        super(Properties.create()
                .descriptionId("fluid." + MCMoltenMetals.MOD_ID + "." + metal.definition().moltenName())
                .density(3000)
                .viscosity(6000)
                .temperature(1300)
                .lightLevel(15)
                .canExtinguish(false)
                .canHydrate(false)
                .canSwim(false)
                .canDrown(false)
                .pathType(PathType.LAVA)
                .adjacentPathType(null)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA));
        this.definition = metal.definition();
    }

    @Override
    public Component getDescription() {
        return Component.literal("Molten " + definition.displayName());
    }

    /*
     * Keep the NeoForge-side mechanics synchronized with vanilla lava rather than maintaining
     * approximate copies of its movement/source-conversion behavior here.
     */
    @Override
    public boolean canConvertToSource(FluidState state, LevelReader reader, BlockPos pos) {
        return NeoForgeMod.LAVA_TYPE.value().canConvertToSource(state, reader, pos);
    }

    @Override
    public double motionScale(Entity entity) {
        return NeoForgeMod.LAVA_TYPE.value().motionScale(entity);
    }

    @Override
    public void setItemMovement(ItemEntity entity) {
        NeoForgeMod.LAVA_TYPE.value().setItemMovement(entity);
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return definition.stillTexture();
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return definition.flowingTexture();
            }
        });
    }
}
