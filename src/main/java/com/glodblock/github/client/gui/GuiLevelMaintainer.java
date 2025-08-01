package com.glodblock.github.client.gui;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.glodblock.github.FluidCraft;
import com.glodblock.github.api.registries.LevelState;
import com.glodblock.github.client.gui.container.ContainerLevelMaintainer;
import com.glodblock.github.common.item.ItemWirelessUltraTerminal;
import com.glodblock.github.common.parts.PartLevelTerminal;
import com.glodblock.github.common.tile.TileLevelMaintainer;
import com.glodblock.github.inventory.gui.GuiType;
import com.glodblock.github.inventory.gui.MouseRegionManager;
import com.glodblock.github.inventory.item.IWirelessTerminal;
import com.glodblock.github.inventory.item.WirelessLevelTerminalInventory;
import com.glodblock.github.inventory.slot.SlotFluidConvertingFake;
import com.glodblock.github.inventory.slot.SlotSingleItem;
import com.glodblock.github.loader.ItemAndBlockHolder;
import com.glodblock.github.network.CPacketLevelMaintainer;
import com.glodblock.github.network.CPacketLevelMaintainer.Action;
import com.glodblock.github.network.CPacketLevelTerminalCommands;
import com.glodblock.github.util.FCGuiColors;
import com.glodblock.github.util.NameConst;
import com.glodblock.github.util.Util;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.DimensionalCoord;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotFake;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketNEIDragClick;
import appeng.util.calculators.ArithHelper;
import appeng.util.calculators.Calculator;
import codechicken.nei.VisiblityData;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;
import cofh.core.render.CoFHFontRenderer;
import cpw.mods.fml.common.Optional;

@Optional.Interface(modid = "NotEnoughItems", iface = "codechicken.nei.api.INEIGuiHandler")
public class GuiLevelMaintainer extends AEBaseGui implements INEIGuiHandler {

    private static final ResourceLocation TEX_BG = FluidCraft.resource("textures/gui/level_maintainer.png");
    private final ContainerLevelMaintainer cont;
    private final Component[] component = new Component[TileLevelMaintainer.REQ_COUNT];
    private final MouseRegionManager mouseRegions = new MouseRegionManager(this);
    private Widget focusedWidget;
    private final CoFHFontRenderer render;
    protected ItemStack icon = null;

    protected GuiType originalGui;
    protected Util.DimensionalCoordSide originalBlockPos;
    protected GuiTabButton originalGuiBtn;

    public GuiLevelMaintainer(InventoryPlayer ipl, TileLevelMaintainer tile) {
        super(new ContainerLevelMaintainer(ipl, tile));
        this.cont = (ContainerLevelMaintainer) inventorySlots;
        this.xSize = 195;
        this.ySize = 214;
        this.render = new CoFHFontRenderer(
                Minecraft.getMinecraft().gameSettings,
                TEX_BG,
                Minecraft.getMinecraft().getTextureManager(),
                true);

        if (ipl.player.openContainer instanceof AEBaseContainer container) {
            var target = container.getTarget();
            if (target instanceof PartLevelTerminal terminal) {
                icon = ItemAndBlockHolder.LEVEL_TERMINAL.stack();
                originalGui = GuiType.LEVEL_TERMINAL;
                DimensionalCoord blockPos = new DimensionalCoord(terminal.getTile());
                originalBlockPos = new Util.DimensionalCoordSide(
                        blockPos.x,
                        blockPos.y,
                        blockPos.z,
                        blockPos.getDimension(),
                        terminal.getSide(),
                        "");
            } else if (target instanceof IWirelessTerminal terminal && terminal.isUniversal(target)) {
                icon = ItemAndBlockHolder.WIRELESS_ULTRA_TERM.stack();
                originalGui = ItemWirelessUltraTerminal.readMode(terminal.getItemStack());
                originalBlockPos = new Util.DimensionalCoordSide(
                        terminal.getInventorySlot(),
                        Util.GuiHelper.encodeType(0, Util.GuiHelper.GuiType.ITEM),
                        0,
                        ipl.player.worldObj.provider.dimensionId,
                        ForgeDirection.UNKNOWN,
                        "");
            } else if (target instanceof WirelessLevelTerminalInventory terminal) {
                icon = ItemAndBlockHolder.LEVEL_TERMINAL.stack();
                originalGui = GuiType.WIRELESS_LEVEL_TERMINAL;
                originalBlockPos = new Util.DimensionalCoordSide(
                        terminal.getInventorySlot(),
                        Util.GuiHelper.encodeType(0, Util.GuiHelper.GuiType.ITEM),
                        0,
                        ipl.player.worldObj.provider.dimensionId,
                        ForgeDirection.UNKNOWN,
                        "");

            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        for (int i = 0; i < TileLevelMaintainer.REQ_COUNT; i++) {
            component[i] = new Component(
                    new Widget(
                            new FCGuiTextField(this.fontRendererObj, guiLeft + 46, guiTop + 19 + 19 * i, 52, 14),
                            NameConst.TT_LEVEL_MAINTAINER_REQUEST_SIZE,
                            i,
                            Action.Quantity),
                    new Widget(
                            new FCGuiTextField(this.fontRendererObj, guiLeft + 100, guiTop + 19 + 19 * i, 52, 14),
                            NameConst.TT_LEVEL_MAINTAINER_BATCH_SIZE,
                            i,
                            Action.Batch),
                    new GuiFCImgButton(guiLeft + 105 + 47, guiTop + 17 + 19 * i, "SUBMIT", "SUBMIT", false),
                    new GuiFCImgButton(guiLeft + 9, guiTop + 20 + 19 * i, "ENABLE", "ENABLE", false),
                    new GuiFCImgButton(guiLeft + 9, guiTop + 20 + 19 * i, "DISABLE", "DISABLE", false),
                    new FCGuiLineField(fontRendererObj, guiLeft + 47, guiTop + 33 + 19 * i, 125),
                    this.buttonList,
                    this.cont);
        }
        if (this.icon != null) {
            this.originalGuiBtn = new GuiTabButton(
                    this.guiLeft + 151,
                    this.guiTop - 4,
                    this.icon,
                    this.icon.getDisplayName(),
                    itemRender);
            this.originalGuiBtn.setHideEdge(13);
            this.buttonList.add(originalGuiBtn);
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        super.drawScreen(mouseX, mouseY, btn);
        for (Component com : this.component) {
            com.getQty().textField.handleTooltip(mouseX, mouseY, this);
            com.getBatch().textField.handleTooltip(mouseX, mouseY, this);
            com.getLine().handleTooltip(mouseX, mouseY, this);
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(TEX_BG);
        drawTexturedModalRect(offsetX, offsetY, 0, 0, 176, ySize);

        for (int i = 0; i < TileLevelMaintainer.REQ_COUNT; i++) {
            this.component[i].draw();
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        fontRendererObj.drawString(getGuiDisplayName(NameConst.i18n(NameConst.GUI_LEVEL_MAINTAINER)), 8, 6, 0x404040);
        mouseRegions.render(mouseX, mouseY);
    }

    @Override
    public void func_146977_a(final Slot s) {
        if (drawSlot0(s)) super.func_146977_a(s);
    }

    public boolean drawSlot0(Slot slot) {
        if (slot instanceof SlotFake) {
            IAEItemStack stack = ((SlotFluidConvertingFake) slot).getAeStack();
            super.func_146977_a(new SlotSingleItem(slot));
            if (stack == null) return true;
            IAEItemStack fake = stack.copy();

            Widget qty = this.component[slot.getSlotIndex()].getQty();
            qty.validate();
            fake.setStackSize(qty.getAmount() != null ? qty.getAmount() : 0);

            GL11.glTranslatef(0.0f, 0.0f, 200.0f);
            aeRenderItem.setAeStack(fake);
            aeRenderItem.renderItemOverlayIntoGUI(
                    fontRendererObj,
                    mc.getTextureManager(),
                    fake.getItemStack(),
                    slot.xDisplayPosition,
                    slot.yDisplayPosition);
            GL11.glTranslatef(0.0f, 0.0f, -200.0f);
            return false;
        }
        return true;
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        if (btn == 0) {
            if (focusedWidget != null) {
                focusedWidget.textField.setFocused(false);
            }
            for (Component com : this.component) {
                Widget textField = com.isMouseIn(xCoord, yCoord);
                if (textField != null) {
                    textField.textField.setFocused(true);
                    this.focusedWidget = textField;
                    super.mouseClicked(xCoord, yCoord, btn);
                    return;
                }
            }
            this.focusedWidget = null;
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (this.focusedWidget == null) {
            super.keyTyped(character, key);
            return;
        }
        if (!this.checkHotbarKeys(key)) {
            if (!((character == ' ') && this.focusedWidget.textField.getText().isEmpty())) {
                this.focusedWidget.textField.textboxKeyTyped(character, key);
            }
            super.keyTyped(character, key);

            this.focusedWidget.validate();

            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.component[this.focusedWidget.componentIndex].submit();
                this.focusedWidget.textField.setFocused(false);
                this.focusedWidget = null;
            }
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        if (btn == originalGuiBtn) {
            switchGui();
        } else {
            super.actionPerformed(btn);
            for (Component com : this.component) {
                if (com.sendToServer(btn)) {
                    break;
                }
            }
        }
    }

    public void switchGui() {
        CPacketLevelTerminalCommands message = new CPacketLevelTerminalCommands(
                CPacketLevelTerminalCommands.Action.BACK,
                originalBlockPos.x,
                originalBlockPos.y,
                originalBlockPos.z,
                originalBlockPos.getDimension(),
                originalBlockPos.getSide());
        if (originalGui != null) {
            message.setOriginalGui(originalGui.ordinal());
        }
        FluidCraft.proxy.netHandler.sendToServer(message);
    }

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return null;
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotIdx, int ctrlDown, int mouseButton) {
        if (slot instanceof SlotFluidConvertingFake && this.cont.getPlayerInv().getItemStack() == null) {
            slot.putStack(null);
            this.component[slot.getSlotIndex()].reset();
        }
        super.handleMouseClick(slot, slotIdx, ctrlDown, mouseButton);
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack draggedStack, int button) {
        if (draggedStack == null) {
            return false;
        }

        draggedStack.stackSize = 0;
        Slot slotAtPosition = this.getSlotAtPosition(mouseX, mouseY);
        if (slotAtPosition == null) {
            return false;
        }

        for (int i = 0; i < this.cont.getRequestSlots().length; i++) {
            SlotFluidConvertingFake slot = this.cont.getRequestSlots()[i];
            if (slotAtPosition.equals(slot)) {
                slot.putStack(draggedStack);
                component[i].reset();
                NetworkHandler.instance.sendToServer(new PacketNEIDragClick(draggedStack, slot.getSlotIndex()));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        return false;
    }

    public void updateComponent(int index, long quantity, long batchSize, boolean isEnabled, LevelState state) {
        if (index < 0 || index >= TileLevelMaintainer.REQ_COUNT) return;
        component[index].setEnable(isEnabled);
        component[index].setState(state);
        component[index].getQty().textField.setText(String.valueOf(quantity));
        component[index].getBatch().textField.setText(String.valueOf(batchSize));
        component[index].getQty().validate();
        component[index].getBatch().validate();
    }

    public void updateComponent(int index, LevelState state) {
        if (index < 0 || index >= TileLevelMaintainer.REQ_COUNT) return;
        component[index].setState(state);
    }

    private class Component {

        public boolean isEnable = false;
        private final Widget qty;
        private final Widget batch;
        private final GuiFCImgButton disable;
        private final GuiFCImgButton enable;
        private final GuiFCImgButton submit;
        private final FCGuiLineField line;
        private LevelState state;
        private final ContainerLevelMaintainer container;

        public Component(Widget qtyInput, Widget batchInput, GuiFCImgButton submitBtn, GuiFCImgButton enableBtn,
                GuiFCImgButton disableBtn, FCGuiLineField line, List<GuiButton> buttonList,
                ContainerLevelMaintainer container) {
            this.qty = qtyInput;
            this.batch = batchInput;
            this.enable = enableBtn;
            this.disable = disableBtn;
            this.submit = submitBtn;
            this.line = line;
            this.state = LevelState.None;
            this.container = container;
            buttonList.add(this.submit);
            buttonList.add(this.enable);
            buttonList.add(this.disable);
        }

        public int getIndex() {
            return this.qty.componentIndex;
        }

        public void setEnable(boolean enable) {
            this.isEnable = enable;
        }

        private void send(Widget widget) {
            if (cont.inventorySlots.get(widget.componentIndex).getHasStack() && widget.getAmount() != null) {
                FluidCraft.proxy.netHandler.sendToServer(
                        new CPacketLevelMaintainer(widget.action, widget.componentIndex, widget.getAmount()));
            }
        }

        public void submit() {
            this.sendToServer(this.submit);
        }

        protected boolean sendToServer(GuiButton btn) {
            boolean didSomething = false;
            if (this.submit == btn) {
                final Widget qty = this.getQty();
                final Widget batch = this.getBatch();
                qty.validate();
                batch.validate();
                if (qty.getAmount() != null) {
                    this.send(qty);
                    qty.textField.setText(String.valueOf(qty.getAmount()));
                }
                if (batch.getAmount() != null) {
                    this.send(batch);
                    batch.textField.setText(String.valueOf(batch.getAmount()));
                }

                didSomething = true;
            } else if (this.enable == btn) {
                this.setEnable(false);
                FluidCraft.proxy.netHandler.sendToServer(new CPacketLevelMaintainer(Action.Enable, this.getIndex()));
                didSomething = true;
            } else if (this.disable == btn) {
                if (this.container.getInventory().get(this.getIndex()) != null) {
                    this.setEnable(true);
                    FluidCraft.proxy.netHandler
                            .sendToServer(new CPacketLevelMaintainer(Action.Disable, this.getIndex()));
                    didSomething = true;
                }
            }
            return didSomething;
        }

        public Widget isMouseIn(final int xCoord, final int yCoord) {
            if (this.qty.textField.isMouseIn(xCoord, yCoord)) return this.getQty();
            if (this.batch.textField.isMouseIn(xCoord, yCoord)) return this.getBatch();
            return null;
        }

        public Widget getQty() {
            return this.qty;
        }

        public Widget getBatch() {
            return this.batch;
        }

        public FCGuiLineField getLine() {
            return this.line;
        }

        public void draw() {
            this.qty.draw();
            this.batch.draw();
            ArrayList<String> message = new ArrayList<>();
            message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_TITLE) + "\n");
            switch (this.state) {
                case Idle -> {
                    this.line.setColor(FCGuiColors.StateIdle.getColor());
                    message.add(
                            NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_CURRENT) + " "
                                    + NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_IDLE));
                }
                case Craft -> {
                    this.line.setColor(FCGuiColors.StateCraft.getColor());
                    message.add(
                            NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_CURRENT) + " "
                                    + NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_LINK));
                }
                case Export -> {
                    this.line.setColor(FCGuiColors.StateExport.getColor());
                    message.add(
                            NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_CURRENT) + " "
                                    + NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_EXPORT));
                }
                case Error -> {
                    this.line.setColor(FCGuiColors.StateError.getColor());
                    message.add(
                            NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_CURRENT) + " "
                                    + NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_ERROR));
                }
                case NotFound -> {
                    this.line.setColor(FCGuiColors.StateError.getColor());
                    message.add(
                            NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_CURRENT) + " "
                                    + NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_NOT_FOUND));
                }
                case CantCraft -> {
                    this.line.setColor(FCGuiColors.StateError.getColor());
                    message.add(
                            NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_CURRENT) + " "
                                    + NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_CANT_CRAFT));
                }
                default -> {
                    this.line.setColor(FCGuiColors.StateNone.getColor());
                    message.add(
                            NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_CURRENT) + " "
                                    + NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_NONE));
                }
            }
            message.add("");
            if (isShiftKeyDown()) {
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_IDLE));
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_IDLE_DESC) + "\n");
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_LINK));
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_LINK_DESC) + "\n");
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_EXPORT));
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_EXPORT_DESC) + "\n");
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_ERROR));
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_ERROR_DESC) + "\n");
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_NOT_FOUND));
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_NOT_FOUND_DESC) + "\n");
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_CANT_CRAFT));
                message.add(NameConst.i18n(NameConst.TT_LEVEL_MAINTAINER_CANT_CRAFT_DESC));
            } else {
                message.add(NameConst.i18n(NameConst.TT_SHIFT_FOR_MORE));
            }
            this.line.setMessage(
                    render.wrapFormattedStringToWidth(String.join("\n", message), (int) Math.floor(xSize * 0.8)));
            this.line.drawTextBox();
            if (this.isEnable) {
                this.enable.visible = true;
                this.disable.visible = false;
            } else {
                this.enable.visible = false;
                this.disable.visible = true;
            }
        }

        public void setState(LevelState state) {
            this.state = state;
        }

        public void reset() {
            this.qty.textField.setText("0");
            this.batch.textField.setText("0");
            this.qty.validate();
            this.batch.validate();
            this.setEnable(false);
            this.setState(LevelState.None);
        }
    }

    private class Widget {

        public final int componentIndex;
        public final Action action;
        public final FCGuiTextField textField;
        private final String tooltip;
        private Long amount;

        public Widget(FCGuiTextField textField, String tooltip, int componentIndex, Action action) {
            this.textField = textField;
            this.textField.setEnableBackgroundDrawing(false);
            this.textField.setText("0");
            this.textField.setMaxStringLength(16); // this length is enough to be useful
            this.componentIndex = componentIndex;
            this.action = action;
            this.tooltip = tooltip;
        }

        public void draw() {
            String current = amount != null
                    ? StatCollector.translateToLocal(NameConst.TT_LEVEL_MAINTAINER_CURRENT) + " "
                            + NumberFormat.getNumberInstance().format(amount)
                            + "\n"
                    : "";
            if (isShiftKeyDown()) {
                this.setTooltip(
                        render.wrapFormattedStringToWidth(
                                StatCollector.translateToLocal(this.tooltip) + "\n"
                                        + current
                                        + "\n"
                                        + StatCollector.translateToLocal(this.tooltip + ".hint"),
                                xSize / 2));
            } else {
                this.setTooltip(
                        render.wrapFormattedStringToWidth(
                                NameConst.i18n(this.tooltip, "\n", false) + "\n"
                                        + current
                                        + NameConst.i18n(NameConst.TT_SHIFT_FOR_MORE),
                                (int) Math.floor(xSize * 0.8)));
            }
            this.textField.drawTextBox();
        }

        public void setTooltip(String message) {
            this.textField.setMessage(message);
        }

        public void validate() {
            final double result = Calculator.conversion(this.textField.getText());
            if (Double.isNaN(result) || result < 0) {
                this.amount = null;
                this.textField.setTextColor(0xFF0000);
            } else {
                this.amount = (long) ArithHelper.round(result, 0);
                this.textField.setTextColor(0xFFFFFF);
            }
        }

        @Nullable
        public Long getAmount() {
            return this.amount;
        }
    }
}
