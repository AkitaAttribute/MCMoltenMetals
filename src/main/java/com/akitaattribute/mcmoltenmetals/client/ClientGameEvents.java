package com.akitaattribute.mcmoltenmetals.client;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = MCMoltenMetals.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (MoltenTextureGenerator.consumeReloadRequest()) {
            Minecraft.getInstance().reloadResourcePacks();
        }
    }
}
