package com.akitaattribute.mcmoltenmetals.simulation;

import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;

/** Chunk-independent logical state and processing rules for a Molten Fabricator. */
public final class MoltenFabricatorMachine {
    public static final int TANK_CAPACITY_MB = 1_000_000_000;
    public static final long ENERGY_CAPACITY_FE = 1_000_000_000L;
    public static final long ENERGY_PER_TICK_FE = 100L;
    public static final int LAVA_PER_OPERATION_MB = 1_000;
    public static final int OUTPUT_PER_OPERATION_MB = 1_000;
    public static final int RAW_PER_OPERATION = 50;
    public static final int RAW_OUTPUT_MB = 7_500;
    public static final int INGOT_MOLTEN_MB = 150;
    public static final int TICKS_REQUIRED = 5 * SharedConstants.TICKS_PER_SECOND;
    public static final int DIORITE_CAPACITY = 64;

    private static final String NBT_INPUT_FLUID = "InputFluid";
    private static final String NBT_INPUT = "InputAmount";
    private static final String NBT_DIORITE = "Diorite";
    private static final String NBT_MATERIAL_ITEM = "MaterialItem";
    private static final String NBT_MATERIAL_METAL = "MaterialMetal";
    private static final String NBT_MATERIAL_RAW = "MaterialRaw";
    private static final String NBT_MATERIAL_COUNT = "MaterialCount";
    private static final String NBT_OUTPUT_METAL = "OutputMetal";
    private static final String NBT_OUTPUT = "OutputAmount";
    private static final String NBT_ITEM_OUTPUT = "ItemOutput";
    private static final String NBT_ITEM_OUTPUT_COUNT = "ItemOutputCount";
    private static final String NBT_ENERGY = "EnergyFE";
    private static final String NBT_PROGRESS = "Progress";
    private static final String NBT_PROCESSING_RECIPE = "ProcessingRecipe";
    private static final String NBT_CAST_MODE = "CastMode";
    private static final String NBT_ENABLED = "Enabled";

    // Legacy keys from the first continuous-offline implementation.
    private static final String LEGACY_LAVA = "Lava";
    private static final String LEGACY_SELECTOR = "SelectorMetal";
    private static final String LEGACY_OUTPUT = "Output";

    private String inputFluid = "";
    private int inputMb;
    private int diorite;
    private String materialItem = "";
    private String materialMetal = "";
    private boolean materialRaw;
    private int materialCount;
    private String outputMetal = "";
    private int outputMb;
    private String itemOutput = "";
    private int itemOutputCount;
    private long energyFe;
    private int progress;
    private String processingRecipe = "";
    private CastMode castMode = CastMode.INGOT;
    private boolean enabled = true;

    private transient long lastTick = Long.MIN_VALUE;
    private transient boolean lastActive;

    public TickResult tick(long gameTime) {
        if (lastTick == gameTime) {
            return new TickResult(false, lastActive);
        }
        lastTick = gameTime;

        Operation operation = resolveOperation();
        boolean changed = normalizeProcessingRecipe(operation);
        if (operation == null || !canWork(operation)) {
            lastActive = false;
            return new TickResult(changed, false);
        }

        energyFe -= ENERGY_PER_TICK_FE;
        progress++;
        changed = true;
        lastActive = true;

        if (progress >= TICKS_REQUIRED) {
            finish(operation);
            progress = 0;
        }
        return new TickResult(changed, true);
    }

    private Operation resolveOperation() {
        if (!inputFluid.isEmpty() && !inputFluid.equals("lava")) {
            return MoltenFabricatorRecipes.castingOutputItemId(inputFluid, castMode)
                    .map(output -> new Operation(Kind.CAST, inputFluid, output, "cast:" + inputFluid + ':' + castMode.name()))
                    .orElse(null);
        }
        if (inputFluid.equals("lava") && !materialMetal.isEmpty()
                && MoltenFabricatorRecipes.lavaRecipeAllowed(materialMetal)) {
            return new Operation(Kind.LAVA, materialMetal, "", "lava:" + materialMetal);
        }
        if (inputFluid.isEmpty() && materialRaw && !materialMetal.isEmpty()
                && MoltenFabricatorRecipes.rawRecipeAllowed(materialMetal)) {
            return new Operation(Kind.RAW, materialMetal, "", "raw:" + materialMetal);
        }
        return null;
    }

    private boolean normalizeProcessingRecipe(Operation operation) {
        String next = operation == null ? "" : operation.key();
        if (processingRecipe.equals(next)) {
            return false;
        }
        processingRecipe = next;
        if (progress != 0) {
            progress = 0;
        }
        return true;
    }

    private boolean canWork(Operation operation) {
        if (!enabled || energyFe < ENERGY_PER_TICK_FE) {
            return false;
        }
        return switch (operation.kind()) {
            case LAVA -> inputMb >= LAVA_PER_OPERATION_MB
                    && materialCount > 0
                    && diorite > 0
                    && canAcceptFluid(operation.metal(), OUTPUT_PER_OPERATION_MB);
            case RAW -> materialCount >= RAW_PER_OPERATION
                    && diorite > 0
                    && canAcceptFluid(operation.metal(), RAW_OUTPUT_MB);
            case CAST -> inputMb >= INGOT_MOLTEN_MB && canAcceptItem(operation.outputItem(), 1);
        };
    }

    private void finish(Operation operation) {
        switch (operation.kind()) {
            case LAVA -> {
                inputMb -= LAVA_PER_OPERATION_MB;
                diorite--;
                addFluidOutput(operation.metal(), OUTPUT_PER_OPERATION_MB);
            }
            case RAW -> {
                materialCount -= RAW_PER_OPERATION;
                diorite--;
                if (materialCount == 0) {
                    materialItem = "";
                    materialMetal = "";
                    materialRaw = false;
                }
                addFluidOutput(operation.metal(), RAW_OUTPUT_MB);
            }
            case CAST -> {
                inputMb -= INGOT_MOLTEN_MB;
                if (inputMb == 0) {
                    inputFluid = "";
                }
                if (itemOutputCount == 0) {
                    itemOutput = operation.outputItem();
                }
                itemOutputCount++;
            }
        }
    }

    private void addFluidOutput(String metal, int amount) {
        if (outputMb == 0) {
            outputMetal = metal;
        }
        outputMb += amount;
    }

    private boolean canAcceptFluid(String metal, int amount) {
        return amount > 0 && outputMb <= TANK_CAPACITY_MB - amount
                && (outputMb == 0 || outputMetal.equals(metal));
    }

    private boolean canAcceptItem(String itemId, int amount) {
        return amount > 0
                && itemOutputCount <= MoltenFabricatorRecipes.maxStackSize(itemId) - amount
                && (itemOutputCount == 0 || itemOutput.equals(itemId));
    }

    public boolean syncLoadedState(
            String inputFluid,
            int inputMb,
            int diorite,
            String materialItem,
            String materialMetal,
            boolean materialRaw,
            int materialCount,
            String outputMetal,
            int outputMb,
            String itemOutput,
            int itemOutputCount,
            long energyFe,
            int progress,
            CastMode castMode,
            boolean enabled) {
        String safeInputFluid = normalize(inputFluid);
        int safeInput = clamp(inputMb, 0, TANK_CAPACITY_MB);
        if (safeInput == 0) safeInputFluid = "";
        int safeDiorite = clamp(diorite, 0, DIORITE_CAPACITY);
        String safeMaterialItem = normalizeId(materialItem);
        int materialLimit = safeMaterialItem.isEmpty() ? 64 : MoltenFabricatorRecipes.maxStackSize(safeMaterialItem);
        int safeMaterialCount = clamp(materialCount, 0, materialLimit);
        if (safeMaterialCount == 0) safeMaterialItem = "";
        String safeMaterialMetal = safeMaterialCount == 0 ? "" : normalize(materialMetal);
        boolean safeMaterialRaw = materialRaw && safeMaterialCount > 0;
        int safeOutput = clamp(outputMb, 0, TANK_CAPACITY_MB);
        String safeOutputMetal = safeOutput == 0 ? "" : normalize(outputMetal);
        String safeItemOutput = normalizeId(itemOutput);
        int itemLimit = safeItemOutput.isEmpty() ? 64 : MoltenFabricatorRecipes.maxStackSize(safeItemOutput);
        int safeItemOutputCount = clamp(itemOutputCount, 0, itemLimit);
        if (safeItemOutputCount == 0) safeItemOutput = "";
        long safeEnergy = Math.max(0L, Math.min(energyFe, ENERGY_CAPACITY_FE));
        int safeProgress = clamp(progress, 0, TICKS_REQUIRED - 1);
        CastMode safeMode = castMode == null ? CastMode.INGOT : castMode;

        boolean changed = !this.inputFluid.equals(safeInputFluid) || this.inputMb != safeInput
                || this.diorite != safeDiorite || !this.materialItem.equals(safeMaterialItem)
                || !this.materialMetal.equals(safeMaterialMetal) || this.materialRaw != safeMaterialRaw
                || this.materialCount != safeMaterialCount || !this.outputMetal.equals(safeOutputMetal)
                || this.outputMb != safeOutput || !this.itemOutput.equals(safeItemOutput)
                || this.itemOutputCount != safeItemOutputCount || this.energyFe != safeEnergy
                || this.progress != safeProgress || this.castMode != safeMode || this.enabled != enabled;

        this.inputFluid = safeInputFluid;
        this.inputMb = safeInput;
        this.diorite = safeDiorite;
        this.materialItem = safeMaterialItem;
        this.materialMetal = safeMaterialMetal;
        this.materialRaw = safeMaterialRaw;
        this.materialCount = safeMaterialCount;
        this.outputMetal = safeOutputMetal;
        this.outputMb = safeOutput;
        this.itemOutput = safeItemOutput;
        this.itemOutputCount = safeItemOutputCount;
        this.energyFe = safeEnergy;
        this.progress = safeProgress;
        this.castMode = safeMode;
        this.enabled = enabled;
        return changed;
    }

    public boolean setCastMode(CastMode mode) {
        CastMode safe = mode == null ? CastMode.INGOT : mode;
        if (castMode == safe) return false;
        castMode = safe;
        return true;
    }

    // Chunk-independent resource API for the offline logistics layer.
    public long insertEnergyFe(long amount) {
        if (amount <= 0) return 0;
        long accepted = Math.min(amount, ENERGY_CAPACITY_FE - energyFe);
        energyFe += accepted;
        return accepted;
    }

    public int insertInputFluid(String key, int amount) {
        String normalized = normalize(key);
        if (amount <= 0 || !MoltenFabricatorRecipes.isAcceptedInputKey(normalized)
                || (!inputFluid.isEmpty() && !inputFluid.equals(normalized))) return 0;
        int accepted = Math.min(amount, TANK_CAPACITY_MB - inputMb);
        if (accepted > 0) inputFluid = normalized;
        inputMb += accepted;
        return accepted;
    }

    public int insertDiorite(int amount) {
        if (amount <= 0) return 0;
        int accepted = Math.min(amount, DIORITE_CAPACITY - diorite);
        diorite += accepted;
        return accepted;
    }

    public int insertMaterialItem(String itemId, int amount) {
        if (amount <= 0) return 0;
        MoltenFabricatorRecipes.MaterialInfo info = MoltenFabricatorRecipes.identifyMaterial(itemId).orElse(null);
        if (info == null || (!materialItem.isEmpty() && !materialItem.equals(info.itemId()))) return 0;
        int limit = MoltenFabricatorRecipes.maxStackSize(info.itemId());
        int accepted = Math.min(amount, limit - materialCount);
        if (accepted <= 0) return 0;
        if (materialCount == 0) {
            materialItem = info.itemId();
            materialMetal = info.metal();
            materialRaw = info.raw();
        }
        materialCount += accepted;
        return accepted;
    }

    public int extractOutputMb(int amount) {
        if (amount <= 0 || outputMb == 0) return 0;
        int extracted = Math.min(amount, outputMb);
        outputMb -= extracted;
        if (outputMb == 0) outputMetal = "";
        return extracted;
    }

    public int extractItemOutput(int amount) {
        if (amount <= 0 || itemOutputCount == 0) return 0;
        int extracted = Math.min(amount, itemOutputCount);
        itemOutputCount -= extracted;
        if (itemOutputCount == 0) itemOutput = "";
        return extracted;
    }

    public String inputFluid() { return inputFluid; }
    public int inputMb() { return inputMb; }
    public int diorite() { return diorite; }
    public String materialItem() { return materialItem; }
    public int materialCount() { return materialCount; }
    public String outputMetal() { return outputMetal; }
    public int outputMb() { return outputMb; }
    public String itemOutput() { return itemOutput; }
    public int itemOutputCount() { return itemOutputCount; }
    public long energyFe() { return energyFe; }
    public int progress() { return progress; }
    public CastMode castMode() { return castMode; }
    public boolean enabled() { return enabled; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(NBT_INPUT_FLUID, inputFluid);
        tag.putInt(NBT_INPUT, inputMb);
        tag.putInt(NBT_DIORITE, diorite);
        tag.putString(NBT_MATERIAL_ITEM, materialItem);
        tag.putString(NBT_MATERIAL_METAL, materialMetal);
        tag.putBoolean(NBT_MATERIAL_RAW, materialRaw);
        tag.putInt(NBT_MATERIAL_COUNT, materialCount);
        tag.putString(NBT_OUTPUT_METAL, outputMetal);
        tag.putInt(NBT_OUTPUT, outputMb);
        tag.putString(NBT_ITEM_OUTPUT, itemOutput);
        tag.putInt(NBT_ITEM_OUTPUT_COUNT, itemOutputCount);
        tag.putLong(NBT_ENERGY, energyFe);
        tag.putInt(NBT_PROGRESS, progress);
        tag.putString(NBT_PROCESSING_RECIPE, processingRecipe);
        tag.putInt(NBT_CAST_MODE, castMode.ordinal());
        tag.putBoolean(NBT_ENABLED, enabled);
        return tag;
    }

    public static MoltenFabricatorMachine load(CompoundTag tag) {
        MoltenFabricatorMachine machine = new MoltenFabricatorMachine();
        if (!tag.contains(NBT_INPUT) && tag.contains(LEGACY_LAVA)) {
            // Preserve machines saved by the original lava-only continuous-offline implementation.
            machine.inputMb = clamp(tag.getInt(LEGACY_LAVA), 0, TANK_CAPACITY_MB);
            machine.inputFluid = machine.inputMb == 0 ? "" : "lava";
            machine.diorite = clamp(tag.getInt(NBT_DIORITE), 0, DIORITE_CAPACITY);
            String selector = normalize(tag.getString(LEGACY_SELECTOR));
            if (!selector.isEmpty()) {
                MoltenFabricatorRecipes.findIngotItem(selector).ifPresent(item -> {
                    machine.materialItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
                    machine.materialMetal = selector;
                    machine.materialRaw = false;
                    machine.materialCount = 1;
                });
            }
            machine.outputMb = clamp(tag.getInt(LEGACY_OUTPUT), 0, TANK_CAPACITY_MB);
            machine.outputMetal = machine.outputMb == 0 ? "" : normalize(tag.getString(NBT_OUTPUT_METAL));
            machine.energyFe = Math.max(0L, Math.min(tag.getLong(NBT_ENERGY), ENERGY_CAPACITY_FE));
            machine.progress = clamp(tag.getInt(NBT_PROGRESS), 0, TICKS_REQUIRED - 1);
            machine.enabled = !tag.contains(NBT_ENABLED) || tag.getBoolean(NBT_ENABLED);
            return machine;
        }

        machine.inputMb = clamp(tag.getInt(NBT_INPUT), 0, TANK_CAPACITY_MB);
        machine.inputFluid = machine.inputMb == 0 ? "" : normalize(tag.getString(NBT_INPUT_FLUID));
        machine.diorite = clamp(tag.getInt(NBT_DIORITE), 0, DIORITE_CAPACITY);
        machine.materialItem = normalizeId(tag.getString(NBT_MATERIAL_ITEM));
        int materialLimit = machine.materialItem.isEmpty() ? 64 : MoltenFabricatorRecipes.maxStackSize(machine.materialItem);
        machine.materialCount = clamp(tag.getInt(NBT_MATERIAL_COUNT), 0, materialLimit);
        if (machine.materialCount == 0) machine.materialItem = "";
        machine.materialMetal = machine.materialCount == 0 ? "" : normalize(tag.getString(NBT_MATERIAL_METAL));
        machine.materialRaw = machine.materialCount > 0 && tag.getBoolean(NBT_MATERIAL_RAW);
        machine.outputMb = clamp(tag.getInt(NBT_OUTPUT), 0, TANK_CAPACITY_MB);
        machine.outputMetal = machine.outputMb == 0 ? "" : normalize(tag.getString(NBT_OUTPUT_METAL));
        machine.itemOutput = normalizeId(tag.getString(NBT_ITEM_OUTPUT));
        int itemLimit = machine.itemOutput.isEmpty() ? 64 : MoltenFabricatorRecipes.maxStackSize(machine.itemOutput);
        machine.itemOutputCount = clamp(tag.getInt(NBT_ITEM_OUTPUT_COUNT), 0, itemLimit);
        if (machine.itemOutputCount == 0) machine.itemOutput = "";
        machine.energyFe = Math.max(0L, Math.min(tag.getLong(NBT_ENERGY), ENERGY_CAPACITY_FE));
        machine.progress = clamp(tag.getInt(NBT_PROGRESS), 0, TICKS_REQUIRED - 1);
        machine.processingRecipe = tag.getString(NBT_PROCESSING_RECIPE);
        machine.castMode = CastMode.fromOrdinal(tag.getInt(NBT_CAST_MODE));
        machine.enabled = !tag.contains(NBT_ENABLED) || tag.getBoolean(NBT_ENABLED);
        return machine;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private enum Kind { LAVA, RAW, CAST }
    private record Operation(Kind kind, String metal, String outputItem, String key) { }
    public record TickResult(boolean changed, boolean active) { }

    public enum CastMode {
        INGOT,
        STEEL;

        public CastMode next() { return this == INGOT ? STEEL : INGOT; }
        public CastMode previous() { return next(); }
        public static CastMode fromOrdinal(int ordinal) { return ordinal == STEEL.ordinal() ? STEEL : INGOT; }
    }
}
