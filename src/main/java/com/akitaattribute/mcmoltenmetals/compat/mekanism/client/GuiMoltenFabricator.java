package com.akitaattribute.mcmoltenmetals.compat.mekanism.client;

import com.akitaattribute.mcmoltenmetals.compat.mekanism.tile.MoltenFabricatorTile;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.IProgressInfoHandler;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class GuiMoltenFabricator extends GuiConfigurableTile<MoltenFabricatorTile, MekanismTileContainer<MoltenFabricatorTile>> {
    public GuiMoltenFabricator(MekanismTileContainer<MoltenFabricatorTile> container, Inventory inv, Component title) {
        super(container, inv, title);
        dynamicSlots = true;
        titleLabelY = 4;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();

        addRenderableWidget(new GuiFluidGauge(
                () -> tile.lavaTank,
                () -> tile.getFluidTanks(null),
                GaugeType.STANDARD,
                this,
                7,
                13));

        addRenderableWidget(new GuiFluidGauge(
                () -> tile.outputTank,
                () -> tile.getFluidTanks(null),
                GaugeType.STANDARD,
                this,
                131,
                13));

        addRenderableWidget(new GuiProgress(new IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return tile.getScaledProgress();
            }

            @Override
            public boolean isActive() {
                return tile.getActive();
            }
        }, ProgressType.LARGE_RIGHT, this, 91, 39));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }
}
