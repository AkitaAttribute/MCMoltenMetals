package com.akitaattribute.mcmoltenmetals.compat.mekanism;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import com.akitaattribute.mcmoltenmetals.compat.mekanism.client.MekanismClientIntegration;
import com.akitaattribute.mcmoltenmetals.compat.mekanism.tile.MoltenFabricatorTile;
import java.lang.reflect.InvocationTargetException;
import mekanism.api.text.ILangEntry;
import mekanism.common.block.attribute.AttributeUpgradeSupport;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.content.blocktype.Machine.MachineBuilder;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Mekanism-native machine registration. This class is never loaded unless Mekanism is present.
 */
public final class MekanismIntegration {
    public static final String MACHINE_NAME = "molten_fabricator";

    public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(MCMoltenMetals.MOD_ID);
    public static final TileEntityTypeDeferredRegister TILE_TYPES = new TileEntityTypeDeferredRegister(MCMoltenMetals.MOD_ID);
    public static final ContainerTypeDeferredRegister CONTAINER_TYPES = new ContainerTypeDeferredRegister(MCMoltenMetals.MOD_ID);

    public static final Machine<MoltenFabricatorTile> MOLTEN_FABRICATOR_TYPE = MachineBuilder
            .createMachine(MekanismIntegration::tileType, Lang.MOLTEN_FABRICATOR)
            .withGui(MekanismIntegration::containerType)
            .withSideConfig(TransmissionType.FLUID)
            .without(AttributeUpgradeSupport.class)
            .build();

    public static final BlockRegistryObject<BlockTile<MoltenFabricatorTile, Machine<MoltenFabricatorTile>>, BlockItem> MOLTEN_FABRICATOR =
            BLOCKS.register(MACHINE_NAME,
                    () -> new BlockTile<>(MOLTEN_FABRICATOR_TYPE, properties -> properties.mapColor(MapColor.METAL)));

    public static final TileEntityTypeRegistryObject<MoltenFabricatorTile> MOLTEN_FABRICATOR_TILE = TILE_TYPES
            .mekBuilder(MOLTEN_FABRICATOR, MoltenFabricatorTile::new)
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .withSimple(Capabilities.CONFIG_CARD)
            .build();

    public static final ContainerTypeRegistryObject<MekanismTileContainer<MoltenFabricatorTile>> MOLTEN_FABRICATOR_CONTAINER =
            CONTAINER_TYPES.register(MACHINE_NAME, MoltenFabricatorTile.class);

    private MekanismIntegration() {
    }

    private static TileEntityTypeRegistryObject<MoltenFabricatorTile> tileType() {
        return MOLTEN_FABRICATOR_TILE;
    }

    private static ContainerTypeRegistryObject<MekanismTileContainer<MoltenFabricatorTile>> containerType() {
        return MOLTEN_FABRICATOR_CONTAINER;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        TILE_TYPES.register(modBus);
        CONTAINER_TYPES.register(modBus);
        modBus.addListener(MekanismIntegration::addCreativeTabItems);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClient(modBus);
        }
    }

    private static void registerClient(IEventBus modBus) {
        try {
            MekanismClientIntegration.register(modBus);
        } catch (NoClassDefFoundError error) {
            throw new IllegalStateException("Mekanism client integration could not load", error);
        }
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MOLTEN_FABRICATOR.getItemHolder());
        }
    }

    private enum Lang implements ILangEntry {
        MOLTEN_FABRICATOR("block." + MCMoltenMetals.MOD_ID + "." + MACHINE_NAME);

        private final String translationKey;

        Lang(String translationKey) {
            this.translationKey = translationKey;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }
}
