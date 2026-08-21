package com.akitaattribute.mcmoltenmetals.compat.mekanism.client;

import com.akitaattribute.mcmoltenmetals.compat.mekanism.MekanismIntegration;
import mekanism.client.ClientRegistrationUtil;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only Mekanism hooks. Loaded reflectively only on a physical client. */
public final class MekanismClientIntegration {
    private MekanismClientIntegration() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(MekanismClientIntegration::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        ClientRegistrationUtil.registerScreen(
                event,
                MekanismIntegration.MOLTEN_FABRICATOR_CONTAINER,
                GuiMoltenFabricator::new);
    }
}
