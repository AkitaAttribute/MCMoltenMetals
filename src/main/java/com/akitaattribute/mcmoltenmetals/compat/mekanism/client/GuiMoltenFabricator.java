package com.akitaattribute.mcmoltenmetals.compat.mekanism.client;

import com.akitaattribute.mcmoltenmetals.compat.mekanism.tile.MoltenFabricatorTile;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.IProgressInfoHandler;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.network.PacketUtils;
import mekanism.common.network.to_server.PacketGuiInteract;
import mekanism.common.network.to_server.PacketGuiInteract.GuiInteraction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class GuiMoltenFabricator extends GuiConfigurableTile<MoltenFabricatorTile, MekanismTileContainer<MoltenFabricatorTile>> {
    private Button castModeButton;

    public GuiMoltenFabricator(MekanismTileContainer<MoltenFabricatorTile> container, Inventory inv, Component title) {
        super(container, inv, title);
        dynamicSlots = true;
        titleLabelY = 4;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiFluidGauge(() -> tile.inputTank, () -> tile.getFluidTanks(null), GaugeType.STANDARD, this, 7, 13));
        addRenderableWidget(new GuiFluidGauge(() -> tile.outputTank, () -> tile.getFluidTanks(null), GaugeType.STANDARD, this, 131, 13));
        addRenderableWidget(new GuiVerticalPowerBar(this, tile.getEnergyContainer(), 164, 15));
        addRenderableWidget(new GuiEnergyTab(this, tile.getEnergyContainer(), tile::getActive));
        addRenderableWidget(new GuiProgress(new IProgressInfoHandler() {
            @Override public double getProgress() { return tile.getScaledProgress(); }
            @Override public boolean isActive() { return tile.getActive(); }
        }, ProgressType.LARGE_RIGHT, this, 91, 39));

        castModeButton = addRenderableWidget(Button.builder(modeText(), button ->
                PacketUtils.sendToServer(new PacketGuiInteract(GuiInteraction.NEXT_MODE, tile)))
                .bounds(leftPos + 77, topPos + 68, 78, 14)
                .build());
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (castModeButton != null) castModeButton.setMessage(modeText());
    }

    private Component modeText() {
        String key = tile.getCastMode() == com.akitaattribute.mcmoltenmetals.simulation.MoltenFabricatorMachine.CastMode.STEEL
                ? "gui.mcmoltenmetals.fabricator.mode_steel"
                : "gui.mcmoltenmetals.fabricator.mode_ingot";
        return Component.translatable("gui.mcmoltenmetals.fabricator.mode", Component.translatable(key));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
