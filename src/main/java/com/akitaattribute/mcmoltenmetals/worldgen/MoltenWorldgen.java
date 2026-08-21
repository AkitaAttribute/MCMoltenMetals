package com.akitaattribute.mcmoltenmetals.worldgen;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.worldgen.test.MoltenNetherTestSpawn;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import terrablender.api.Regions;
import terrablender.core.TerraBlender;

/**
 * Self-contained Nether worldgen integration.
 *
 * Keep this package dependent on the molten-metal registry rather than Fabricator/Mekanism
 * classes so it can later move into a separate addon that depends on MC Molten Metals.
 */
public final class MoltenWorldgen {
    public static final ResourceKey<Biome> MOLTEN_GROTTO =
            ResourceKey.create(Registries.BIOME, MCMoltenMetals.id("molten_grotto"));
    public static final ResourceKey<DimensionType> TEST_DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, MCMoltenMetals.id("molten_nether_test"));

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, MCMoltenMetals.MOD_ID);

    public static final Holder<Feature<?>> MOLTEN_POOL = FEATURES.register(
            "molten_pool", () -> new MoltenPoolFeature(NoneFeatureConfiguration.CODEC));

    private MoltenWorldgen() {
    }

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
        modBus.addListener(MoltenWorldgen::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(MoltenNetherTestSpawn::onServerStarted);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            int vanillaNetherWeight = TerraBlender.CONFIG.vanillaNetherRegionWeight;
            Regions.register(new MoltenNetherRegion(vanillaNetherWeight));
            MCMoltenMetals.LOGGER.info(
                    "Registered Molten Grotto TerraBlender Nether region with weight {}",
                    vanillaNetherWeight);
        });
    }
}
