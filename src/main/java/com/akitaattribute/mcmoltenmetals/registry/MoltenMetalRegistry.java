package com.akitaattribute.mcmoltenmetals.registry;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.fluid.MoltenFluidType;
import com.akitaattribute.mcmoltenmetals.fluid.MoltenLavaFluid;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class MoltenMetalRegistry {
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MCMoltenMetals.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(BuiltInRegistries.FLUID, MCMoltenMetals.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, MCMoltenMetals.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, MCMoltenMetals.MOD_ID);

    private static final List<MoltenMetal> METALS = Arrays.stream(MetalDefinition.values())
            .map(MoltenMetalRegistry::registerMetal)
            .toList();
    private static final Map<String, MoltenMetal> BY_NAME = buildLookup();

    private MoltenMetalRegistry() {
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    public static List<MoltenMetal> metals() {
        return METALS;
    }

    public static List<String> metalNames() {
        return METALS.stream().map(metal -> metal.definition().id()).toList();
    }

    public static Optional<MoltenMetal> find(String name) {
        return Optional.ofNullable(BY_NAME.get(name.toLowerCase(Locale.ROOT)));
    }

    private static Map<String, MoltenMetal> buildLookup() {
        Map<String, MoltenMetal> lookup = new LinkedHashMap<>();
        for (MoltenMetal metal : METALS) {
            lookup.put(metal.definition().id(), metal);
        }
        return Map.copyOf(lookup);
    }

    private static MoltenMetal registerMetal(MetalDefinition definition) {
        MoltenMetal metal = new MoltenMetal(definition);
        String name = definition.moltenName();

        metal.fluidType = FLUID_TYPES.register(name, () -> new MoltenFluidType(metal));
        metal.source = FLUIDS.register(name, () -> new MoltenLavaFluid.Source(metal));
        metal.flowing = FLUIDS.register("flowing_" + name, () -> new MoltenLavaFluid.Flowing(metal));
        metal.block = BLOCKS.register(name,
                () -> new LiquidBlock(metal.source, BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA)));
        metal.bucket = ITEMS.register(name + "_bucket",
                () -> new BucketItem(metal.source,
                        new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

        return metal;
    }

    public static final class MoltenMetal {
        private final MetalDefinition definition;
        private DeferredHolder<FluidType, FluidType> fluidType;
        private DeferredHolder<Fluid, MoltenLavaFluid.Source> source;
        private DeferredHolder<Fluid, MoltenLavaFluid.Flowing> flowing;
        private DeferredHolder<Block, LiquidBlock> block;
        private DeferredHolder<Item, BucketItem> bucket;

        private MoltenMetal(MetalDefinition definition) {
            this.definition = definition;
        }

        public MetalDefinition definition() {
            return definition;
        }

        public DeferredHolder<FluidType, FluidType> fluidType() {
            return fluidType;
        }

        public DeferredHolder<Fluid, MoltenLavaFluid.Source> source() {
            return source;
        }

        public DeferredHolder<Fluid, MoltenLavaFluid.Flowing> flowing() {
            return flowing;
        }

        public DeferredHolder<Block, LiquidBlock> block() {
            return block;
        }

        public DeferredHolder<Item, BucketItem> bucket() {
            return bucket;
        }
    }
}
