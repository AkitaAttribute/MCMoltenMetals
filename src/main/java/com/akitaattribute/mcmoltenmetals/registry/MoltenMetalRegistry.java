package com.akitaattribute.mcmoltenmetals.registry;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.fluid.MoltenFluidType;
import com.akitaattribute.mcmoltenmetals.fluid.MoltenLavaFluid;
import com.akitaattribute.mcmoltenmetals.item.MoltenBucketItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
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

    private static List<MetalDefinition> definitions = List.of();
    private static List<MoltenMetal> metals = List.of();
    private static Map<String, MoltenMetal> byName = Map.of();
    private static boolean initialized;

    private MoltenMetalRegistry() {
    }

    public static synchronized void register(IEventBus modBus, List<MetalDefinition> discoveredDefinitions) {
        if (initialized) {
            throw new IllegalStateException("MoltenMetalRegistry initialized twice");
        }
        initialized = true;
        definitions = List.copyOf(discoveredDefinitions);
        metals = definitions.stream().map(MoltenMetalRegistry::registerMetal).toList();

        Map<String, MoltenMetal> lookup = new LinkedHashMap<>();
        for (MoltenMetal metal : metals) {
            lookup.put(metal.definition().id(), metal);
        }
        byName = Map.copyOf(lookup);

        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    public static List<MetalDefinition> definitions() {
        return definitions;
    }

    public static List<MoltenMetal> metals() {
        return metals;
    }

    public static List<String> metalNames() {
        return definitions.stream().map(MetalDefinition::id).toList();
    }

    public static Optional<MoltenMetal> find(String name) {
        return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
    }

    private static MoltenMetal registerMetal(MetalDefinition definition) {
        MoltenMetal metal = new MoltenMetal(definition);
        String name = definition.moltenName();

        metal.fluidType = FLUID_TYPES.register(name, () -> new MoltenFluidType(metal));
        metal.source = FLUIDS.register(name, () -> new MoltenLavaFluid.Source(metal));
        metal.flowing = FLUIDS.register(definition.flowingName(), () -> new MoltenLavaFluid.Flowing(metal));
        metal.block = BLOCKS.register(name,
                () -> new LiquidBlock(metal.source.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA)));
        metal.bucket = ITEMS.register(name + "_bucket",
                () -> new MoltenBucketItem(metal.source.get(), definition));

        return metal;
    }

    public static final class MoltenMetal {
        private final MetalDefinition definition;
        private DeferredHolder<FluidType, FluidType> fluidType;
        private DeferredHolder<Fluid, MoltenLavaFluid.Source> source;
        private DeferredHolder<Fluid, MoltenLavaFluid.Flowing> flowing;
        private DeferredHolder<Block, LiquidBlock> block;
        private DeferredHolder<Item, MoltenBucketItem> bucket;

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

        public DeferredHolder<Item, MoltenBucketItem> bucket() {
            return bucket;
        }
    }
}
