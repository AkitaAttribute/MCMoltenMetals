package com.akitaattribute.mcmoltenmetals.simulation;

import com.akitaattribute.mcmoltenmetals.compat.MekanismCompat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Persistent index of chunk-independent machines. It deliberately knows nothing about Fabricator
 * recipes: it only owns dimension/position registration and invokes logical machines whose chunks
 * are not currently loaded.
 */
public final class OfflineMachineRegistry extends SavedData {
    private static final String DATA_NAME = "mcmoltenmetals_offline_machines";
    private static final String NBT_FABRICATORS = "Fabricators";
    private static final String NBT_DIMENSION = "Dimension";
    private static final String NBT_POSITION = "Position";
    private static final String NBT_STATE = "State";

    private static final SavedData.Factory<OfflineMachineRegistry> FACTORY =
            new SavedData.Factory<>(OfflineMachineRegistry::new, OfflineMachineRegistry::load);

    private final Map<String, Map<Long, MoltenFabricatorMachine>> fabricators = new HashMap<>();

    public static MoltenFabricatorMachine registerOrGet(
            ServerLevel level, BlockPos pos, MoltenFabricatorMachine initialState) {
        OfflineMachineRegistry registry = get(level.getServer());
        String dimension = level.dimension().location().toString();
        Map<Long, MoltenFabricatorMachine> machines =
                registry.fabricators.computeIfAbsent(dimension, ignored -> new HashMap<>());
        MoltenFabricatorMachine existing = machines.putIfAbsent(pos.asLong(), initialState);
        if (existing == null) {
            registry.setDirty();
            return initialState;
        }
        return existing;
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        OfflineMachineRegistry registry = get(level.getServer());
        String dimension = level.dimension().location().toString();
        Map<Long, MoltenFabricatorMachine> machines = registry.fabricators.get(dimension);
        if (machines != null && machines.remove(pos.asLong()) != null) {
            if (machines.isEmpty()) {
                registry.fabricators.remove(dimension);
            }
            registry.setDirty();
        }
    }

    public static void markDirty(ServerLevel level) {
        get(level.getServer()).setDirty();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        // The Fabricator is only registered when Mekanism exists. If Mekanism is removed from an
        // existing world, retain its persistent logical state but do not simulate ghost machines.
        if (!MekanismCompat.isLoaded()) {
            return;
        }

        MinecraftServer server = event.getServer();
        OfflineMachineRegistry registry = get(server);
        boolean changed = false;

        for (Map.Entry<String, Map<Long, MoltenFabricatorMachine>> dimensionEntry : registry.fabricators.entrySet()) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(dimensionEntry.getKey());
            if (dimensionId == null) {
                continue;
            }
            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
            ServerLevel level = server.getLevel(dimensionKey);
            if (level == null) {
                continue;
            }

            long gameTime = level.getGameTime();
            for (Map.Entry<Long, MoltenFabricatorMachine> machineEntry : dimensionEntry.getValue().entrySet()) {
                BlockPos pos = BlockPos.of(machineEntry.getKey());
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                if (machineEntry.getValue().tick(gameTime).changed()) {
                    changed = true;
                }
            }
        }

        if (changed) {
            registry.setDirty();
        }
    }

    private static OfflineMachineRegistry get(MinecraftServer server) {
        // One global file in the overworld data storage keeps dimension + position registration
        // together and remains available even while the machine's own dimension/chunk is absent.
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static OfflineMachineRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        OfflineMachineRegistry registry = new OfflineMachineRegistry();
        ListTag list = tag.getList(NBT_FABRICATORS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String dimension = entry.getString(NBT_DIMENSION);
            if (dimension.isEmpty() || ResourceLocation.tryParse(dimension) == null || !entry.contains(NBT_STATE, Tag.TAG_COMPOUND)) {
                continue;
            }
            registry.fabricators
                    .computeIfAbsent(dimension, ignored -> new HashMap<>())
                    .put(entry.getLong(NBT_POSITION), MoltenFabricatorMachine.load(entry.getCompound(NBT_STATE)));
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Map<Long, MoltenFabricatorMachine>> dimensionEntry : fabricators.entrySet()) {
            Iterator<Map.Entry<Long, MoltenFabricatorMachine>> iterator = dimensionEntry.getValue().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, MoltenFabricatorMachine> machineEntry = iterator.next();
                CompoundTag entry = new CompoundTag();
                entry.putString(NBT_DIMENSION, dimensionEntry.getKey());
                entry.putLong(NBT_POSITION, machineEntry.getKey());
                entry.put(NBT_STATE, machineEntry.getValue().save());
                list.add(entry);
            }
        }
        tag.put(NBT_FABRICATORS, list);
        return tag;
    }
}
