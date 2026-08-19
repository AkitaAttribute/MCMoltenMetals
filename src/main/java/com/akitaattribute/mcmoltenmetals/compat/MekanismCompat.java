package com.akitaattribute.mcmoltenmetals.compat;

import com.akitaattribute.mcmoltenmetals.MCMoltenMetals;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/**
 * Keeps all hard Mekanism class references out of the normal mod startup path.
 * The actual integration package is only class-loaded when Mekanism is present.
 */
public final class MekanismCompat {
    private static final String MEKANISM_MOD_ID = "mekanism";
    private static final String INTEGRATION_CLASS =
            "com.akitaattribute.mcmoltenmetals.compat.mekanism.MekanismIntegration";

    private MekanismCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MEKANISM_MOD_ID);
    }

    public static void bootstrap(IEventBus modBus) {
        if (!isLoaded()) {
            MCMoltenMetals.LOGGER.info("Mekanism is not loaded; skipping Molten Fabricator registration");
            return;
        }

        try {
            Class<?> integration = Class.forName(INTEGRATION_CLASS);
            Method register = integration.getMethod("register", IEventBus.class);
            register.invoke(null, modBus);
            MCMoltenMetals.LOGGER.info("Enabled optional Mekanism Molten Fabricator integration");
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Mekanism is loaded but MC Molten Metals integration could not initialize", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Mekanism integration initialization failed", cause);
        }
    }
}
