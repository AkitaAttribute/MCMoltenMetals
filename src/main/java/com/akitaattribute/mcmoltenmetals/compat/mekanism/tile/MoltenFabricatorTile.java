package com.akitaattribute.mcmoltenmetals.compat.mekanism.tile;

import com.akitaattribute.mcmoltenmetals.compat.mekanism.MekanismIntegration;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.akitaattribute.mcmoltenmetals.simulation.MoltenFabricatorMachine;
import com.akitaattribute.mcmoltenmetals.simulation.OfflineMachineRegistry;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mekanism-facing wrapper for the chunk-independent Molten Fabricator simulation.
 *
 * While loaded, normal Mekanism/NeoForge capabilities remain fully usable. The capability contents
 * are synchronized into the logical machine before its one simulation tick and mirrored back after
 * it. While unloaded, the global offline registry ticks that same logical state directly.
 */
public class MoltenFabricatorTile extends TileEntityConfigurableMachine {
    public static final int TANK_CAPACITY = MoltenFabricatorMachine.TANK_CAPACITY_MB;
    public static final int LAVA_PER_OPERATION = MoltenFabricatorMachine.LAVA_PER_OPERATION_MB;
    public static final int OUTPUT_PER_OPERATION = MoltenFabricatorMachine.OUTPUT_PER_OPERATION_MB;
    public static final int BASE_TICKS_REQUIRED = MoltenFabricatorMachine.TICKS_REQUIRED;

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
    @Nullable
    private MoltenFabricatorMachine machine;
    private boolean chunkUnloading;

    public MoltenFabricatorTile(BlockPos pos, BlockState state) {
        super(MekanismIntegration.MOLTEN_FABRICATOR, pos, state);

        ConfigInfo fluidConfig = configComponent.setupIOConfig(
                TransmissionType.FLUID, lavaTank, outputTank, RelativeSide.RIGHT);
        if (fluidConfig != null) {
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.LEFT);
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.BACK);
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.TOP);
            fluidConfig.setDataType(DataType.INPUT, RelativeSide.BOTTOM);
            fluidConfig.setDataType(DataType.OUTPUT, RelativeSide.RIGHT);
            fluidConfig.setEjecting(true);
        }

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
    public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
        if (level instanceof ServerLevel serverLevel) {
            MoltenFabricatorMachine initialState = snapshotNewMachine();
            MoltenFabricatorMachine registered =
                    OfflineMachineRegistry.registerOrGet(serverLevel, worldPosition, initialState);
            machine = registered;
            if (registered != initialState) {
                // Persistent logical state is authoritative after an unloaded interval.
                applyMachineToTile(registered);
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        chunkUnloading = true;
        if (level instanceof ServerLevel serverLevel && machine != null) {
            if (syncMachineFromTile(machine)) {
                OfflineMachineRegistry.markDirty(serverLevel);
            }
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

        // Loaded-world logistics remain ordinary Mekanism behavior.
        lavaContainerSlot.fillTank(containerOutputSlot);
        energySlot.fillContainerOrConvert();

        MoltenFabricatorMachine logicalMachine = ensureMachine();
        if (logicalMachine == null) {
            setActive(false);
            return sendUpdatePacket;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        boolean changed = syncMachineFromTile(logicalMachine);
        MoltenFabricatorMachine.TickResult result = logicalMachine.tick(serverLevel.getGameTime());
        if (changed || result.changed()) {
            OfflineMachineRegistry.markDirty(serverLevel);
        }
        applyMachineToTile(logicalMachine);
        setActive(result.active());
        return sendUpdatePacket;
    }

    @Nullable
    private MoltenFabricatorMachine ensureMachine() {
        if (machine != null) {
            return machine;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        MoltenFabricatorMachine initialState = snapshotNewMachine();
        machine = OfflineMachineRegistry.registerOrGet(serverLevel, worldPosition, initialState);
        if (machine != initialState) {
            applyMachineToTile(machine);
        }
        return machine;
    }

    private MoltenFabricatorMachine snapshotNewMachine() {
        MoltenFabricatorMachine state = new MoltenFabricatorMachine();
        syncMachineFromTile(state);
        return state;
    }

    private boolean syncMachineFromTile(MoltenFabricatorMachine state) {
        long energyFe = IEnergyConversionHelper.INSTANCE.feConversion().convertTo(energyContainer.getEnergy());
        MoltenMetalRegistry.MoltenMetal selected = selectedMetal(selectorSlot.getStack());
        String selectorMetal = selected == null ? "" : selected.definition().id();
        String outputMetal = outputMetalId();
        return state.syncLoadedState(
                lavaTank.getFluidAmount(),
                dioriteSlot.getStack().getCount(),
                selectorMetal,
                outputMetal,
                outputTank.getFluidAmount(),
                energyFe,
                operatingTicks,
                canFunction());
    }

    private void applyMachineToTile(MoltenFabricatorMachine state) {
        int targetLava = state.lavaMb();
        if (lavaTank.getFluidAmount() != targetLava
                || (targetLava > 0 && lavaTank.getFluid().getFluid() != Fluids.LAVA)) {
            lavaTank.setStack(targetLava == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.LAVA, targetLava));
        }

        int targetDiorite = state.diorite();
        if (dioriteSlot.getStack().getCount() != targetDiorite
                || (targetDiorite > 0 && !dioriteSlot.getStack().is(Blocks.DIORITE.asItem()))) {
            dioriteSlot.setStack(targetDiorite == 0 ? ItemStack.EMPTY : new ItemStack(Blocks.DIORITE, targetDiorite));
        }

        int targetOutput = state.outputMb();
        if (targetOutput == 0) {
            if (!outputTank.isEmpty()) {
                outputTank.setStack(FluidStack.EMPTY);
            }
        } else {
            MoltenMetalRegistry.find(state.outputMetal()).ifPresent(metal -> {
                if (outputTank.getFluidAmount() != targetOutput
                        || outputTank.getFluid().getFluid() != metal.source().get()) {
                    outputTank.setStack(new FluidStack(metal.source().get(), targetOutput));
                }
            });
        }

        long targetJoules = IEnergyConversionHelper.INSTANCE.feConversion().convertFrom(state.energyFe());
        if (energyContainer.getEnergy() != targetJoules) {
            energyContainer.setEnergy(targetJoules);
        }

        operatingTicks = state.progress();
    }

    private String outputMetalId() {
        if (outputTank.isEmpty()) {
            return "";
        }
        for (MoltenMetalRegistry.MoltenMetal metal : MoltenMetalRegistry.metals()) {
            if (outputTank.getFluid().getFluid() == metal.source().get()) {
                return metal.definition().id();
            }
        }
        return "";
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
        if (machine != null) {
            operatingTicks = machine.progress();
        }
        super.saveAdditional(tag, provider);
        tag.putInt(SerializationConstants.PROGRESS, operatingTicks);
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        operatingTicks = Math.max(0, Math.min(tag.getInt(SerializationConstants.PROGRESS), BASE_TICKS_REQUIRED - 1));
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
