package com.akitaattribute.mcmoltenmetals.fluid;

import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import net.neoforged.neoforge.fluids.FluidType;

public abstract class MoltenLavaFluid extends LavaFluid {
    protected final MoltenMetalRegistry.MoltenMetal metal;

    protected MoltenLavaFluid(MoltenMetalRegistry.MoltenMetal metal) {
        this.metal = metal;
    }

    @Override
    public Fluid getFlowing() {
        return metal.flowing().get();
    }

    @Override
    public Fluid getSource() {
        return metal.source().get();
    }

    @Override
    public Item getBucket() {
        return metal.bucket().get();
    }

    @Override
    public FluidType getFluidType() {
        return metal.fluidType().get();
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return metal.block().get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, FlowingFluid.getLegacyLevel(state));
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == getSource() || fluid == getFlowing();
    }

    public static final class Source extends MoltenLavaFluid {
        public Source(MoltenMetalRegistry.MoltenMetal metal) {
            super(metal);
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }
    }

    public static final class Flowing extends MoltenLavaFluid {
        public Flowing(MoltenMetalRegistry.MoltenMetal metal) {
            super(metal);
            registerDefaultState(getStateDefinition().any()
                    .setValue(LEVEL, 7)
                    .setValue(FALLING, false));
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }
    }
}
