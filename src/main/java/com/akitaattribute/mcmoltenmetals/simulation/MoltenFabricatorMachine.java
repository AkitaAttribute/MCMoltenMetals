package com.akitaattribute.mcmoltenmetals.simulation;

import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;

/**
 * Chunk-independent logical state and processing rules for a Molten Fabricator.
 *
 * The Mekanism tile mirrors this state while its chunk is loaded. When the chunk is not loaded,
 * {@link OfflineMachineRegistry} ticks this same logical machine directly once per server game tick.
 */
public final class MoltenFabricatorMachine {
    public static final int TANK_CAPACITY_MB = 1_000_000_000;
    public static final long ENERGY_CAPACITY_FE = 1_000_000_000L;
    public static final long ENERGY_PER_TICK_FE = 100L;
    public static final int LAVA_PER_OPERATION_MB = 1_000;
    public static final int OUTPUT_PER_OPERATION_MB = 1_000;
    public static final int TICKS_REQUIRED = 5 * SharedConstants.TICKS_PER_SECOND;
    public static final int DIORITE_CAPACITY = 64;

    private static final String NBT_LAVA = "Lava";
    private static final String NBT_DIORITE = "Diorite";
    private static final String NBT_SELECTOR = "SelectorMetal";
    private static final String NBT_OUTPUT_METAL = "OutputMetal";
    private static final String NBT_OUTPUT = "Output";
    private static final String NBT_ENERGY = "EnergyFE";
    private static final String NBT_PROGRESS = "Progress";
    private static final String NBT_PROCESSING_METAL = "ProcessingMetal";
    private static final String NBT_ENABLED = "Enabled";

    private int lavaMb;
    private int diorite;
    private String selectorMetal = "";
    private String outputMetal = "";
    private int outputMb;
    private long energyFe;
    private int progress;
    private String processingMetal = "";
    private boolean enabled = true;

    // Runtime-only guard for chunk load/unload boundaries. It prevents the loaded tile and the
    // offline registry from both advancing the same logical machine during one server game tick.
    private transient long lastTick = Long.MIN_VALUE;
    private transient boolean lastActive;

    public TickResult tick(long gameTime) {
        if (lastTick == gameTime) {
            return new TickResult(false, lastActive);
        }
        lastTick = gameTime;

        boolean changed = normalizeProcessingMetal();
        if (!canWork()) {
            lastActive = false;
            return new TickResult(changed, false);
        }

        energyFe -= ENERGY_PER_TICK_FE;
        progress++;
        changed = true;
        lastActive = true;

        if (progress >= TICKS_REQUIRED) {
            lavaMb -= LAVA_PER_OPERATION_MB;
            diorite--;
            if (outputMb == 0) {
                outputMetal = selectorMetal;
            }
            outputMb += OUTPUT_PER_OPERATION_MB;
            progress = 0;
            processingMetal = selectorMetal;
        }
        return new TickResult(changed, true);
    }

    private boolean normalizeProcessingMetal() {
        if (selectorMetal.isEmpty()) {
            return false;
        }
        if (processingMetal.isEmpty()) {
            processingMetal = selectorMetal;
            return true;
        }
        if (!processingMetal.equals(selectorMetal)) {
            processingMetal = selectorMetal;
            if (progress != 0) {
                progress = 0;
            }
            return true;
        }
        return false;
    }

    private boolean canWork() {
        return enabled
                && !selectorMetal.isEmpty()
                && energyFe >= ENERGY_PER_TICK_FE
                && lavaMb >= LAVA_PER_OPERATION_MB
                && diorite > 0
                && canAcceptOutput(selectorMetal, OUTPUT_PER_OPERATION_MB);
    }

    private boolean canAcceptOutput(String metal, int amount) {
        return amount > 0
                && outputMb <= TANK_CAPACITY_MB - amount
                && (outputMb == 0 || outputMetal.equals(metal));
    }

    /**
     * Updates the logical machine from the live Mekanism capability state. Returns whether the
     * persistent logical state changed.
     */
    public boolean syncLoadedState(
            int lavaMb,
            int diorite,
            String selectorMetal,
            String outputMetal,
            int outputMb,
            long energyFe,
            int progress,
            boolean enabled) {
        int safeLava = clamp(lavaMb, 0, TANK_CAPACITY_MB);
        int safeDiorite = clamp(diorite, 0, DIORITE_CAPACITY);
        String safeSelector = normalizeMetal(selectorMetal);
        int safeOutput = clamp(outputMb, 0, TANK_CAPACITY_MB);
        String safeOutputMetal = safeOutput == 0 ? "" : normalizeMetal(outputMetal);
        long safeEnergy = Math.max(0L, Math.min(energyFe, ENERGY_CAPACITY_FE));
        int safeProgress = clamp(progress, 0, TICKS_REQUIRED - 1);

        boolean changed = this.lavaMb != safeLava
                || this.diorite != safeDiorite
                || !this.selectorMetal.equals(safeSelector)
                || !this.outputMetal.equals(safeOutputMetal)
                || this.outputMb != safeOutput
                || this.energyFe != safeEnergy
                || this.progress != safeProgress
                || this.enabled != enabled;

        this.lavaMb = safeLava;
        this.diorite = safeDiorite;
        this.selectorMetal = safeSelector;
        this.outputMetal = safeOutputMetal;
        this.outputMb = safeOutput;
        this.energyFe = safeEnergy;
        this.progress = safeProgress;
        this.enabled = enabled;
        return changed;
    }

    // These resource methods are intentionally chunk-independent. Future offline power/fluid/item
    // networks can use them before this machine's simulation phase without depending on Mekanism.
    public long insertEnergyFe(long amount) {
        if (amount <= 0) {
            return 0L;
        }
        long accepted = Math.min(amount, ENERGY_CAPACITY_FE - energyFe);
        energyFe += accepted;
        return accepted;
    }

    public int insertLavaMb(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int accepted = Math.min(amount, TANK_CAPACITY_MB - lavaMb);
        lavaMb += accepted;
        return accepted;
    }

    public int insertDiorite(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int accepted = Math.min(amount, DIORITE_CAPACITY - diorite);
        diorite += accepted;
        return accepted;
    }

    public int extractOutputMb(int amount) {
        if (amount <= 0 || outputMb == 0) {
            return 0;
        }
        int extracted = Math.min(amount, outputMb);
        outputMb -= extracted;
        if (outputMb == 0) {
            outputMetal = "";
        }
        return extracted;
    }

    public boolean setSelectorMetal(String metal) {
        String normalized = normalizeMetal(metal);
        if (selectorMetal.equals(normalized)) {
            return false;
        }
        selectorMetal = normalized;
        return true;
    }

    public int lavaMb() {
        return lavaMb;
    }

    public int diorite() {
        return diorite;
    }

    public String selectorMetal() {
        return selectorMetal;
    }

    public String outputMetal() {
        return outputMetal;
    }

    public int outputMb() {
        return outputMb;
    }

    public long energyFe() {
        return energyFe;
    }

    public int progress() {
        return progress;
    }

    public boolean enabled() {
        return enabled;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_LAVA, lavaMb);
        tag.putInt(NBT_DIORITE, diorite);
        tag.putString(NBT_SELECTOR, selectorMetal);
        tag.putString(NBT_OUTPUT_METAL, outputMetal);
        tag.putInt(NBT_OUTPUT, outputMb);
        tag.putLong(NBT_ENERGY, energyFe);
        tag.putInt(NBT_PROGRESS, progress);
        tag.putString(NBT_PROCESSING_METAL, processingMetal);
        tag.putBoolean(NBT_ENABLED, enabled);
        return tag;
    }

    public static MoltenFabricatorMachine load(CompoundTag tag) {
        MoltenFabricatorMachine machine = new MoltenFabricatorMachine();
        machine.lavaMb = clamp(tag.getInt(NBT_LAVA), 0, TANK_CAPACITY_MB);
        machine.diorite = clamp(tag.getInt(NBT_DIORITE), 0, DIORITE_CAPACITY);
        machine.selectorMetal = normalizeMetal(tag.getString(NBT_SELECTOR));
        machine.outputMb = clamp(tag.getInt(NBT_OUTPUT), 0, TANK_CAPACITY_MB);
        machine.outputMetal = machine.outputMb == 0 ? "" : normalizeMetal(tag.getString(NBT_OUTPUT_METAL));
        machine.energyFe = Math.max(0L, Math.min(tag.getLong(NBT_ENERGY), ENERGY_CAPACITY_FE));
        machine.progress = clamp(tag.getInt(NBT_PROGRESS), 0, TICKS_REQUIRED - 1);
        machine.processingMetal = normalizeMetal(tag.getString(NBT_PROCESSING_METAL));
        machine.enabled = !tag.contains(NBT_ENABLED) || tag.getBoolean(NBT_ENABLED);
        return machine;
    }

    private static String normalizeMetal(String metal) {
        return metal == null ? "" : metal.trim().toLowerCase(Locale.ROOT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public record TickResult(boolean changed, boolean active) {
    }
}
