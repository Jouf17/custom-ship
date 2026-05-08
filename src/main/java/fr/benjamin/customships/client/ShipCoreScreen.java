package fr.benjamin.customships.client;

import fr.benjamin.customships.menu.ShipCoreMenu;
import fr.benjamin.customships.network.ModPackets;
import fr.benjamin.customships.network.ShipCoreActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public class ShipCoreScreen extends AbstractContainerScreen<ShipCoreMenu> {

    public ShipCoreScreen(ShipCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 152;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 12;
        int y = topPos + 108;

        addRenderableWidget(Button.builder(Component.literal("Assembler"), button -> {
            ModPackets.sendToServer(new ShipCoreActionPacket(menu.getCorePos(), ShipCoreActionPacket.Action.ASSEMBLE));
            minecraft.player.closeContainer();
        }).bounds(x, y, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Désassembler"), button -> {
            ModPackets.sendToServer(new ShipCoreActionPacket(menu.getCorePos(), ShipCoreActionPacket.Action.DISASSEMBLE));
            minecraft.player.closeContainer();
        }).bounds(x + 82, y, 82, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE202020);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + imageHeight - 6, 0xFF30343A);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 8, 0xFFFFFF, false);
        graphics.drawString(font, "État : " + (menu.isAssembled() ? "assemblé" : "non assemblé"), 8, 28, 0xDADADA, false);
        graphics.drawString(font, "Blocs détectés : " + menu.getBlockCount(), 8, 42, 0xDADADA, false);
        graphics.drawString(font, "Capacité : " + menu.getCapacity(), 8, 56, 0xDADADA, false);
        graphics.drawString(font, "Blocs restants : " + menu.getRemainingBlocks(), 8, 70, 0xDADADA, false);
        graphics.drawString(font, "Vitesse max : " + String.format(Locale.ROOT, "%.2f", menu.getMaxSpeed()) + " blocs/s", 8, 84, 0xDADADA, false);
        graphics.drawString(font, "Stabilisateurs : " + menu.getStabilizerCount() + " (" + menu.getStabilizerCapacity() + "/" + menu.getBlockCount() + " blocs)", 8, 98, 0xDADADA, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
