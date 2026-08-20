package com.akitaattribute.mcmoltenmetals.compat.mekanism.tile;

import com.akitaattribute.mcmoltenmetals.compat.mekanism.MekanismIntegration;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.akitaattribute.mcmoltenmetals.simulation.MoltenFabricatorMachine;
import com.akitaattribute.mcmoltenmetals.simulation.MoltenFabricatorRecipes;
import com.akitaattribute.mcmoltenmetals.simulation.MoltenFabricatorRecipes.MaterialInfo;
import com.akitaattribute.mcmoltenmetals.simulation.OfflineMachineRegistry;
import java.util.List;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.api.energy.IEnergyConversionHelper;
import mekanism.api.inventory.IInventorySlot;
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
import mekanism.common.tile.interfaces.IHasMode;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Mekanism-facing wrapper for the chunk-independent Fabricator simulation. */
public class MoltenFabricatorTile extends TileEntityConfigurableMachine implements IHasMode {
    public static final int TANK_CAPACITY = MoltenFabricatorMachine.TANK_CAPACITY_MB;
    public static final int BASE_TICKS_REQUIRED = MoltenFabricatorMachine.TICKS_REQUIRED;
    private static final String NBT_CAST_MODE = "MoltenFabricatorCastMode";

    public BasicFluidTank inputTank;
    public BasicFluidTank outputTank;
    public FluidInventorySlot inputContainerSlot;
    public OutputInventorySlot containerOutputSlot;
    public InputInventorySlot dioriteSlot;
    public InputInventorySlot materialSlot;
    public OutputInventorySlot itemOutputSlot;
    public EnergyInventorySlot energySlot;

    private MachineEnergyContainer<MoltenFabricatorTile> energyContainer;
    private int operatingTicks;
    private MoltenFabricatorMachine.CastMode castMode = MoltenFabricatorMachine.CastMode.INGOT;
    @Nullable private MoltenFabricatorMachine machine;
    private boolean chunkUnloading;

    public MoltenFabricatorTile(BlockPos pos, BlockState state) {
        super(MekanismIntegration.MOLTEN_FABRICATOR, pos, state);

        ConfigInfo fluidConfig = configComponent.setupIOConfig(
                TransmissionType.FLUID, inputTank, outputTank, RelativeSide.RIGHT);
        if (fluidConfig != null) {
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.LEFT);
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.BACK);
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.TOP);
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.BOTTOM);
            fluidConfig.setDataType(DataType.OUTPUT, RelativeSide.RIGHT);
            fluidConfig.setEjecting(true);
        }

        ConfigInfo itemConfig = configComponent.setupItemIOConfig(
                List.of(dioriteSlot, materialSlot), List.of(itemOutputSlot), energySlot, false);
        if (itemConfig != null) {
            itemConfig.setDataType(DataType.INPUT, RelativeSide.LEFT);
            itemConfig.setDataType(DataType.INPUT, RelativeSide.BACK);
            itemConfig.setDataType(DataType.INPUT, RelativeSide.TOP);
            itemConfig.setDataType(DataType.INPUT, RelativeSide.BOTTOM);
            itemConfig.setDataType(DataType.OUTPUT, RelativeSide.RIGHT);
            itemConfig.setEjecting(true);
        }

        configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);
        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM, TransmissionType.FLUID);
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
        builder.addTank(inputTank = BasicFluidTank.input(
                TANK_CAPACITY, MoltenFabricatorRecipes::isAcceptedInputFluid, listener));
        builder.addTank(outputTank = BasicFluidTank.output(TANK_CAPACITY, listener));
        return builder.build();
    }

    @NotNull
    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        // Keep the original first five slot indexes stable for existing worlds; item output is appended.
        builder.addSlot(inputContainerSlot = FluidInventorySlot.fill(inputTank, listener, 28, 20));
        builder.addSlot(containerOutputSlot = OutputInventorySlot.at(listener, 28, 51));
        builder.addSlot(dioriteSlot = InputInventorySlot.at(
                stack -> stack.is(Blocks.DIORITE.asItem()), listener, 64, 17));
        builder.addSlot(materialSlot = InputInventorySlot.at(
                MoltenFabricatorRecipes::isAcceptedMaterial, listener, 64, 53));
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(
                energyContainer, this::getLevel, listener, 105, 53));
        builder.addSlot(itemOutputSlot = OutputInventorySlot.at(listener, 105, 17));
        return builder.build();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
        if (level instanceof ServerLevel serverLevel) {
            MoltenFabricatorMachine initialState = snapshotNewMachine();
            MoltenFabricatorMachine registered = OfflineMachineRegistry.registerOrGet(serverLevel, worldPosition, initialState);
            machine = registered;
            if (registered != initialState) applyMachineToTile(registered);
        }
    }

    @Override
    public void onChunkUnloaded() {
        chunkUnloading = true;
        if (level instanceof ServerLevel serverLevel && machine != null && syncMachineFromTile(machine)) {
            OfflineMachineRegistry.markDirty(serverLevel);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (!chunkUnloading && level instanceof ServerLevel serverLevel) {
            OfflineMachineRegistry.unregister(serverLevel, worldPosition);
        }
        super.setRemoved();
        machine = null;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        inputContainerSlot.fillTank(containerOutputSlot);
        energySlot.fillContainerOrConvert();

        MoltenFabricatorMachine logicalMachine = ensureMachine();
        if (logicalMachine == null) {
            setActive(false);
            return sendUpdatePacket;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        boolean changed = syncMachineFromTile(logicalMachine);
        MoltenFabricatorMachine.TickResult result = logicalMachine.tick(serverLevel.getGameTime());
        if (changed || result.changed()) OfflineMachineRegistry.markDirty(serverLevel);
        applyMachineToTile(logicalMachine);
        setActive(result.active());
        return sendUpdatePacket;
    }

    @Nullable
    private MoltenFabricatorMachine ensureMachine() {
        if (machine != null) return machine;
        if (!(level instanceof ServerLevel serverLevel)) return null;
        MoltenFabricatorMachine initial = snapshotNewMachine();
        machine = OfflineMachineRegistry.registerOrGet(serverLevel, worldPosition, initial);
        if (machine != initial) applyMachineToTile(machine);
        return machine;
    }

    private MoltenFabricatorMachine snapshotNewMachine() {
        MoltenFabricatorMachine state = new MoltenFabricatorMachine();
        syncMachineFromTile(state);
        return state;
    }

    private boolean syncMachineFromTile(MoltenFabricatorMachine state) {
        String inputFluid = MoltenFabricatorRecipes.inputFluidKey(inputTank.getFluid());
        MaterialInfo material = MoltenFabricatorRecipes.identifyMaterial(materialSlot.getStack()).orElse(null);
        String materialItem = material == null ? "" : material.itemId();
        String materialMetal = material == null ? "" : material.metal();
        boolean materialRaw = material != null && material.raw();
        String outputMetal = outputMetalId();
        String itemOutput = itemOutputSlot.getStack().isEmpty()
                ? "" : BuiltInRegistries.ITEM.getKey(itemOutputSlot.getStack().getItem()).toString();
        long energyFe = IEnergyConversionHelper.INSTANCE.feConversion().convertTo(energyContainer.getEnergy());
        return state.syncLoadedState(
                inputFluid, inputTank.getFluidAmount(), dioriteSlot.getStack().getCount(),
                materialItem, materialMetal, materialRaw, materialSlot.getStack().getCount(),
                outputMetal, outputTank.getFluidAmount(), itemOutput, itemOutputSlot.getStack().getCount(),
                energyFe, operatingTicks, castMode, canFunction());
    }

    private void applyMachineToTile(MoltenFabricatorMachine state) {
        FluidStack targetInput = MoltenFabricatorRecipes.fluidForKey(state.inputFluid())
                .map(fluid -> new FluidStack(fluid, state.inputMb())).orElse(FluidStack.EMPTY);
        if (inputTank.getFluid().getFluid() != targetInput.getFluid()
                || inputTank.getFluidAmount() != targetInput.getAmount()) {
            inputTank.setStack(targetInput);
        }

        setItemStack(materialSlot, state.materialItem(), state.materialCount());
        setItemStack(itemOutputSlot, state.itemOutput(), state.itemOutputCount());

        if (state.outputMb() == 0) {
            if (!outputTank.isEmpty()) outputTank.setStack(FluidStack.EMPTY);
        } else {
            MoltenMetalRegistry.find(state.outputMetal()).ifPresent(metal -> {
                if (outputTank.getFluid().getFluid() != metal.source().get()
                        || outputTank.getFluidAmount() != state.outputMb()) {
                    outputTank.setStack(new FluidStack(metal.source().get(), state.outputMb()));
                }
            });
        }

        int targetDiorite = state.diorite();
        if (dioriteSlot.getStack().getCount() != targetDiorite
                || (targetDiorite > 0 && !dioriteSlot.getStack().is(Blocks.DIORITE.asItem()))) {
            dioriteSlot.setStack(targetDiorite == 0 ? ItemStack.EMPTY : new ItemStack(Blocks.DIORITE, targetDiorite));
        }
        long targetJoules = IEnergyConversionHelper.INSTANCE.feConversion().convertFrom(state.energyFe());
        if (energyContainer.getEnergy() != targetJoules) energyContainer.setEnergy(targetJoules);
        operatingTicks = state.progress();
        castMode = state.castMode();
    }

    private static void setItemStack(IInventorySlot slot, String itemId, int count) {
        if (count <= 0 || itemId.isEmpty()) {
            if (!slot.getStack().isEmpty()) slot.setStack(ItemStack.EMPTY);
            return;
        }
        MoltenFabricatorRecipes.itemById(itemId).ifPresent(item -> {
            if (!slot.getStack().is(item) || slot.getStack().getCount() != count) {
                slot.setStack(new ItemStack(item, count));
            }
        });
    }

    private String outputMetalId() {
        if (outputTank.isEmpty()) return "";
        for (MoltenMetalRegistry.MoltenMetal metal : MoltenMetalRegistry.metals()) {
            if (outputTank.getFluid().getFluid() == metal.source().get()) return metal.definition().id();
        }
        return "";
    }

    public MachineEnergyContainer<MoltenFabricatorTile> getEnergyContainer() { return energyContainer; }
    public int getOperatingTicks() { return operatingTicks; }
    public double getScaledProgress() { return operatingTicks / (double) BASE_TICKS_REQUIRED; }
    public MoltenFabricatorMachine.CastMode getCastMode() { return castMode; }
    public int getCastModeOrdinal() { return castMode.ordinal(); }

    @Override
    public void nextMode() { setCastMode(castMode.next()); }

    @Override
    public void previousMode() { setCastMode(castMode.previous()); }

    private void setCastMode(MoltenFabricatorMachine.CastMode mode) {
        if (castMode == mode) return;
        castMode = mode;
        if (machine != null) machine.setCastMode(mode);
        if (level instanceof ServerLevel serverLevel) OfflineMachineRegistry.markDirty(serverLevel);
        markForSave();
    }

    private void setCastModeFromSync(int ordinal) {
        castMode = MoltenFabricatorMachine.CastMode.fromOrdinal(ordinal);
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        if (machine != null) {
            operatingTicks = machine.progress();
            castMode = machine.castMode();
        }
        super.saveAdditional(tag, provider);
        tag.putInt(SerializationConstants.PROGRESS, operatingTicks);
        tag.putInt(NBT_CAST_MODE, castMode.ordinal());
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        operatingTicks = Math.max(0, Math.min(tag.getInt(SerializationConstants.PROGRESS), BASE_TICKS_REQUIRED - 1));
        castMode = MoltenFabricatorMachine.CastMode.fromOrdinal(tag.getInt(NBT_CAST_MODE));
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(this::getOperatingTicks, value -> operatingTicks = value));
        container.track(SyncableInt.create(this::getCastModeOrdinal, this::setCastModeFromSync));
    }

    @Override
    public int getRedstoneLevel() {
        int fluidLevel = MekanismUtils.redstoneLevelFromContents(outputTank.getFluidAmount(), outputTank.getCapacity());
        if (itemOutputSlot.isEmpty()) return fluidLevel;
        int itemLevel = MekanismUtils.redstoneLevelFromContents(
                itemOutputSlot.getStack().getCount(), itemOutputSlot.getStack().getMaxStackSize());
        return Math.max(fluidLevel, itemLevel);
    }
}
