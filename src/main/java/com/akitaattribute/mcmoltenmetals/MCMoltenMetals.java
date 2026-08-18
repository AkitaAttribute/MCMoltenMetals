package com.akitaattribute.mcmoltenmetals;

import com.akitaattribute.mcmoltenmetals.command.MoltenMetalCommand;
import com.akitaattribute.mcmoltenmetals.fluid.MoltenEntityEvents;
import com.akitaattribute.mcmoltenmetals.registry.MetalDiscovery;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.akitaattribute.mcmoltenmetals.resource.GeneratedDataPack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MCMoltenMetals.MOD_ID)
public final class MCMoltenMetals {
    public static final String MOD_ID = "mcmoltenmetals";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public MCMoltenMetals(IEventBus modBus, ModContainer modContainer) {
        var definitions = MetalDiscovery.loadOrDiscover();
        MoltenMetalRegistry.register(modBus, definitions);

        modBus.addListener(GeneratedDataPack::register);
        modBus.addListener(this::addCreativeTabItems);
        NeoForge.EVENT_BUS.addListener(MoltenMetalCommand::register);
        NeoForge.EVENT_BUS.addListener(MoltenEntityEvents::onEntityTick);
        NeoForge.EVENT_BUS.addListener(MoltenEntityEvents::onTagsUpdated);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (MoltenMetalRegistry.MoltenMetal metal : MoltenMetalRegistry.metals()) {
                event.accept(metal.bucket().get());
            }
        }
    }
}
