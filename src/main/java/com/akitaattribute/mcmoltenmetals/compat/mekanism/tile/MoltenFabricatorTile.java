package com.akitaattribute.mcmoltenmetals.compat.mekanism.tile;

import com.akitaattribute.mcmoltenmetals.compat.mekanism.MekanismIntegration;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import java.util.Locale;
import java.util.Set;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
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

    private static final Set<String> SUPPORTED_METALS = Set.of("copper", "iron");

    public BasicFluidTank lavaTank;
    public BasicFluidTank outputTank;
    public FluidInventorySlot lavaContainerSlot;
    public OutputInventorySlot containerOutputSlot;
    public InputInventorySlot dioriteSlot;
    public InputInventorySlot selectorSlot;

    private int operatingTicks;

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

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.FLUID);
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
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();

        // Handle lava buckets/tanks placed in the GUI input slot.
        lavaContainerSlot.fillTank(containerOutputSlot);

        MoltenMetalRegistry.MoltenMetal selected = selectedMetal(selectorSlot.getStack());
        boolean canProcess = canFunction()
                && selected != null
                && !dioriteSlot.isEmpty()
                && lavaTank.getFluidAmount() >= LAVA_PER_OPERATION
                && canAcceptOutput(selected);

        if (!canProcess) {
            if (operatingTicks != 0) {
                operatingTicks = 0;
                markForSave();
            }
            setActive(false);
            return sendUpdatePacket;
        }

        setActive(true);
        operatingTicks++;
        if (operatingTicks >= BASE_TICKS_REQUIRED) {
            finishOperation(selected);
            operatingTicks = 0;
        }
        return sendUpdatePacket;
    }

    private boolean canAcceptOutput(MoltenMetalRegistry.MoltenMetal selected) {
        FluidStack desired = new FluidStack(selected.source().get(), OUTPUT_PER_OPERATION);
        return outputTank.insert(desired, Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    private void finishOperation(MoltenMetalRegistry.MoltenMetal selected) {
        // Recheck every mutable input before committing the operation.
        if (selectedMetal(selectorSlot.getStack()) != selected
                || dioriteSlot.isEmpty()
                || lavaTank.extract(LAVA_PER_OPERATION, Action.SIMULATE, AutomationType.INTERNAL).getAmount() < LAVA_PER_OPERATION
                || !canAcceptOutput(selected)) {
            return;
        }

        lavaTank.extract(LAVA_PER_OPERATION, Action.EXECUTE, AutomationType.INTERNAL);
        dioriteSlot.shrinkStack(1, Action.EXECUTE);
        outputTank.insert(new FluidStack(selected.source().get(), OUTPUT_PER_OPERATION), Action.EXECUTE, AutomationType.INTERNAL);
        markForSave();
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
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        operatingTicks = tag.getInt(SerializationConstants.PROGRESS);
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
