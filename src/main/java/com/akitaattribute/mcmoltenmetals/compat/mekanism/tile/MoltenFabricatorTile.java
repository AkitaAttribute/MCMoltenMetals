package com.akitaattribute.mcmoltenmetals.compat.mekanism.tile;

import com.akitaattribute.mcmoltenmetals.compat.mekanism.MekanismIntegration;
import com.akitaattribute.mcmoltenmetals.energy.OfflineEnergyProviders;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import java.util.Locale;
import java.util.Set;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.api.energy.IEnergyConversionHelper;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.util.MekanismUtils;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mekanism-backed processor used by the optional integration.
 *
 * One operation consumes one bucket of lava and one diorite, and produces one bucket of
 * molten copper or molten iron. The metal item is a selector/catalyst and is never consumed.
 */
public class MoltenFabricatorTile extends TileEntityConfigurableMachine {
    public static final int TANK_CAPACITY = 8 * FluidType.BUCKET_VOLUME;
    public static final int LAVA_PER_OPERATION = FluidType.BUCKET_VOLUME;
    public static final int OUTPUT_PER_OPERATION = FluidType.BUCKET_VOLUME;
    public static final int BASE_TICKS_REQUIRED = 5 * SharedConstants.TICKS_PER_SECOND;

    private static final String NBT_LAST_GAME_TIME = "MoltenFabricatorLastGameTime";
    private static final String NBT_OFFLINE_ENERGY = "MoltenFabricatorOfflineEnergy";
    private static final String NBT_OFFLINE_PROCESS = "MoltenFabricatorOfflineProcess";
    private static final Set<String> SUPPORTED_METALS = Set.of("copper", "iron");

    public BasicFluidTank lavaTank;
    public BasicFluidTank outputTank;
    public FluidInventorySlot lavaContainerSlot;
    public OutputInventorySlot containerOutputSlot;
    public InputInventorySlot dioriteSlot;
    public InputInventorySlot selectorSlot;
    public EnergyInventorySlot energySlot;

    private MachineEnergyContainer<MoltenFabricatorTile> energyContainer;
    private int operatingTicks;

    // Offline processing is catch-up based. These values are snapshots from the last serialized
    // loaded state, so energy injected after the chunk reloads cannot be spent retroactively.
    private long lastProcessedGameTime = Long.MIN_VALUE;
    private long offlineSavedEnergyJoules;
    private boolean offlineProcessAllowed;
    private boolean offlineCatchupPending;

    public MoltenFabricatorTile(BlockPos pos, BlockState state) {
        super(MekanismIntegration.MOLTEN_FABRICATOR, pos, state);

        // One configurable fluid transmission exposes the lava tank as input and the molten
        // buffer as output. This is the same side-config/capability path Mekanism Mechanical
        // Pipes use for native machines.
        ConfigInfo fluidConfig = configComponent.setupIOConfig(
                TransmissionType.FLUID, lavaTank, outputTank, RelativeSide.RIGHT);
        if (fluidConfig != null) {
            // Useful first-placement defaults. All sides remain configurable in the Mekanism UI.
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.LEFT);
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.BACK);
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.TOP);
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.BOTTOM);
            fluidConfig.setDataType(DataType.OUTPUT, RelativeSide.RIGHT);
            fluidConfig.setEjecting(true);
        }

        // Energy uses Mekanism's normal configurable input capability, so Universal Cables and
        // compatible NeoForge energy logistics can power the machine from configured sides.
        configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.FLUID);
    }

    @NotNull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener));
        return builder.build();
    }

    @NotNull
    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this);
        builder.addTank(lavaTank = BasicFluidTank.input(
                TANK_CAPACITY,
                stack -> stack.is(FluidTags.LAVA),
                listener));
        builder.addTank(outputTank = BasicFluidTank.output(TANK_CAPACITY, listener));
        return builder.build();
    }

    @NotNull
    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSide(facingSupplier);

        // Filled lava containers are drained into the same internal tank that is exposed to
        // pipes. Empty containers are moved to the companion output slot.
        builder.addSlot(lavaContainerSlot = FluidInventorySlot.fill(lavaTank, listener, 28, 20));
        builder.addSlot(containerOutputSlot = OutputInventorySlot.at(listener, 28, 51));

        builder.addSlot(dioriteSlot = InputInventorySlot.at(
                stack -> stack.is(Blocks.DIORITE.asItem()), listener, 64, 17));
        builder.addSlot(selectorSlot = InputInventorySlot.at(
                MoltenFabricatorTile::isValidSelector, listener, 64, 53));
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(
                energyContainer, this::getLevel, listener, 105, 53));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        long currentGameTime = level.getGameTime();

        // Catch up before accepting newly loaded cable/item power. Normal Mekanism cables do not
        // receive retroactive credit; only explicitly registered unloaded-capable providers do.
        if (offlineCatchupPending && level instanceof ServerLevel serverLevel) {
            applyOfflineCatchup(serverLevel, currentGameTime);
        }
        lastProcessedGameTime = currentGameTime;

        // Handle lava buckets/tanks and energy items placed in the GUI input slots.
        lavaContainerSlot.fillTank(containerOutputSlot);
        energySlot.fillContainerOrConvert();

        MoltenMetalRegistry.MoltenMetal selected = selectedMetal(selectorSlot.getStack());
        boolean canProcess = canProcessWithoutEnergy(selected);

        if (!canProcess) {
            if (operatingTicks != 0) {
                operatingTicks = 0;
                markForSave();
            }
            setActive(false);
            return sendUpdatePacket;
        }

        long energyPerTick = energyContainer.getEnergyPerTick();
        if (energyContainer.extract(energyPerTick, Action.SIMULATE, AutomationType.INTERNAL) < energyPerTick) {
            // Power loss pauses progress rather than destroying work already completed.
            setActive(false);
            return sendUpdatePacket;
        }

        energyContainer.extract(energyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
        setActive(true);
        operatingTicks++;
        if (operatingTicks >= BASE_TICKS_REQUIRED) {
            finishOperation(selected);
            operatingTicks = 0;
        }
        return sendUpdatePacket;
    }

    private void applyOfflineCatchup(ServerLevel serverLevel, long currentGameTime) {
        offlineCatchupPending = false;

        if (!offlineProcessAllowed || lastProcessedGameTime == Long.MIN_VALUE) {
            clearOfflineSnapshot();
            return;
        }

        // The current loaded tick is processed normally below, so only replay ticks strictly
        // between the saved tick and this one. Server downtime does not advance gameTime.
        long elapsedTicks = currentGameTime - lastProcessedGameTime - 1L;
        if (elapsedTicks <= 0) {
            clearOfflineSnapshot();
            return;
        }

        MoltenMetalRegistry.MoltenMetal selected = selectedMetal(selectorSlot.getStack());
        long usefulTicks = maxUsefulOfflineTicks(selected, elapsedTicks);
        if (usefulTicks <= 0) {
            clearOfflineSnapshot();
            return;
        }

        long requestedFe = usefulTicks * MekanismIntegration.FE_PER_TICK;

        // Ask unloaded-capable external sources first. With no provider registered this returns
        // zero, and the saved internal buffer becomes the sole offline power budget.
        long externalFe = OfflineEnergyProviders.extract(
                serverLevel,
                worldPosition,
                lastProcessedGameTime + 1L,
                currentGameTime,
                requestedFe);
        long externalJoules = IEnergyConversionHelper.INSTANCE.feConversion().convertFrom(externalFe);

        long currentLoadedEnergy = energyContainer.getEnergy();
        long savedEnergy = Math.min(offlineSavedEnergyJoules, energyContainer.getMaxEnergy());
        // Preserve power that may have arrived after the chunk was loaded; it cannot fund the
        // elapsed interval retroactively.
        long newlyLoadedEnergy = Math.max(0L, currentLoadedEnergy - savedEnergy);

        long offlineEnergyBudget = savedEnergy + externalJoules;
        long energyPerTick = energyContainer.getEnergyPerTick();
        long fundedTicks = Math.min(usefulTicks, offlineEnergyBudget / energyPerTick);
        long consumedJoules = fundedTicks * energyPerTick;
        long remainingOfflineEnergy = offlineEnergyBudget - consumedJoules;

        // Any unused offline energy remains in the machine, while post-load energy is preserved.
        energyContainer.setEnergy(Math.min(
                energyContainer.getMaxEnergy(),
                newlyLoadedEnergy + remainingOfflineEnergy));

        if (fundedTicks > 0) {
            advanceOfflineProcessing(selected, fundedTicks);
            markForSave();
        }
        clearOfflineSnapshot();
    }

    private long maxUsefulOfflineTicks(@Nullable MoltenMetalRegistry.MoltenMetal selected, long elapsedTicks) {
        if (selected == null || !canAcceptOutput(selected) || dioriteSlot.isEmpty()
                || lavaTank.getFluidAmount() < LAVA_PER_OPERATION) {
            return 0L;
        }

        int operationsFromLava = lavaTank.getFluidAmount() / LAVA_PER_OPERATION;
        int operationsFromDiorite = dioriteSlot.getStack().getCount();
        int operationsFromOutput = (outputTank.getCapacity() - outputTank.getFluidAmount()) / OUTPUT_PER_OPERATION;
        int availableOperations = Math.min(operationsFromLava, Math.min(operationsFromDiorite, operationsFromOutput));
        if (availableOperations <= 0) {
            return 0L;
        }

        long firstOperationTicks = BASE_TICKS_REQUIRED - operatingTicks;
        long usefulTicks = firstOperationTicks + (long) (availableOperations - 1) * BASE_TICKS_REQUIRED;
        return Math.min(elapsedTicks, usefulTicks);
    }

    private void advanceOfflineProcessing(MoltenMetalRegistry.MoltenMetal selected, long fundedTicks) {
        long remainingTicks = fundedTicks;
        while (remainingTicks > 0 && selectedMetal(selectorSlot.getStack()) == selected
                && !dioriteSlot.isEmpty()
                && lavaTank.getFluidAmount() >= LAVA_PER_OPERATION
                && canAcceptOutput(selected)) {
            int ticksToCompletion = BASE_TICKS_REQUIRED - operatingTicks;
            int step = (int) Math.min(remainingTicks, ticksToCompletion);
            operatingTicks += step;
            remainingTicks -= step;

            if (operatingTicks >= BASE_TICKS_REQUIRED) {
                if (!finishOperation(selected)) {
                    operatingTicks = 0;
                    break;
                }
                operatingTicks = 0;
            }
        }
    }

    private boolean canProcessWithoutEnergy(@Nullable MoltenMetalRegistry.MoltenMetal selected) {
        return canFunction()
                && selected != null
                && !dioriteSlot.isEmpty()
                && lavaTank.getFluidAmount() >= LAVA_PER_OPERATION
                && canAcceptOutput(selected);
    }

    private boolean canAcceptOutput(MoltenMetalRegistry.MoltenMetal selected) {
        FluidStack desired = new FluidStack(selected.source().get(), OUTPUT_PER_OPERATION);
        return outputTank.insert(desired, Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    private boolean finishOperation(MoltenMetalRegistry.MoltenMetal selected) {
        // Recheck every mutable input before committing the operation.
        if (selectedMetal(selectorSlot.getStack()) != selected
                || dioriteSlot.isEmpty()
                || lavaTank.extract(LAVA_PER_OPERATION, Action.SIMULATE, AutomationType.INTERNAL).getAmount() < LAVA_PER_OPERATION
                || !canAcceptOutput(selected)) {
            return false;
        }

        lavaTank.extract(LAVA_PER_OPERATION, Action.EXECUTE, AutomationType.INTERNAL);
        dioriteSlot.shrinkStack(1, Action.EXECUTE);
        outputTank.insert(new FluidStack(selected.source().get(), OUTPUT_PER_OPERATION), Action.EXECUTE, AutomationType.INTERNAL);
        markForSave();
        return true;
    }

    private void clearOfflineSnapshot() {
        offlineSavedEnergyJoules = 0L;
        offlineProcessAllowed = false;
    }

    public MachineEnergyContainer<MoltenFabricatorTile> getEnergyContainer() {
        return energyContainer;
    }

    public int getOperatingTicks() {
        return operatingTicks;
    }

    public double getScaledProgress() {
        return operatingTicks / (double) BASE_TICKS_REQUIRED;
    }

    public static boolean isValidSelector(ItemStack stack) {
        return selectedMetal(stack) != null;
    }

    @Nullable
    public static MoltenMetalRegistry.MoltenMetal selectedMetal(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (String metal : SUPPORTED_METALS) {
            if (matchesMetalSelector(stack, metal)) {
                return MoltenMetalRegistry.find(metal).orElse(null);
            }
        }
        return null;
    }

    private static boolean matchesMetalSelector(ItemStack stack, String metal) {
        if (stack.is(commonTag("c", "ingots/" + metal))
                || stack.is(commonTag("c", "raw_materials/" + metal))
                || stack.is(commonTag("forge", "ingots/" + metal))
                || stack.is(commonTag("forge", "raw_materials/" + metal))) {
            return true;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        int slash = path.lastIndexOf('/');
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        return fileName.equals(metal + "_ingot") || fileName.equals("raw_" + metal);
    }

    private static TagKey<Item> commonTag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt(SerializationConstants.PROGRESS, operatingTicks);

        // If a freshly loaded tile is serialized before its first server tick, preserve the
        // original offline snapshot rather than erasing the pending interval.
        if (offlineCatchupPending) {
            tag.putLong(NBT_LAST_GAME_TIME, lastProcessedGameTime);
            tag.putLong(NBT_OFFLINE_ENERGY, offlineSavedEnergyJoules);
            tag.putBoolean(NBT_OFFLINE_PROCESS, offlineProcessAllowed);
        } else {
            long saveGameTime = level == null ? lastProcessedGameTime : level.getGameTime();
            tag.putLong(NBT_LAST_GAME_TIME, saveGameTime);
            tag.putLong(NBT_OFFLINE_ENERGY, energyContainer.getEnergy());
            tag.putBoolean(NBT_OFFLINE_PROCESS, canProcessWithoutEnergy(selectedMetal(selectorSlot.getStack())));
        }
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        operatingTicks = tag.getInt(SerializationConstants.PROGRESS);

        if (tag.contains(NBT_LAST_GAME_TIME)) {
            lastProcessedGameTime = tag.getLong(NBT_LAST_GAME_TIME);
            offlineSavedEnergyJoules = tag.getLong(NBT_OFFLINE_ENERGY);
            offlineProcessAllowed = tag.getBoolean(NBT_OFFLINE_PROCESS);
            offlineCatchupPending = true;
        } else {
            lastProcessedGameTime = Long.MIN_VALUE;
            clearOfflineSnapshot();
            offlineCatchupPending = false;
        }
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(this::getOperatingTicks, value -> operatingTicks = value));
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(outputTank.getFluidAmount(), outputTank.getCapacity());
    }
}
