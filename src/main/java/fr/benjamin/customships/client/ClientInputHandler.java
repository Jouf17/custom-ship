package fr.benjamin.customships.client;

import fr.benjamin.customships.network.ModPackets;
import fr.benjamin.customships.network.ShipControlPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.Input;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side tick handler for ship piloting.
 *
 * When piloting:
 *  - Replaces player.input with FrozenInput so the player character cannot walk.
 *  - Reads actual hardware key state via KeyMapping.isDown() and sends
 *    ShipControlPacket to the server each time inputs change (or every
 *    KEEPALIVE_TICKS ticks to allow deceleration).
 *
 * Key mapping:
 *  W / S    → forward / backward  (ship moves in its own facing direction)
 *  A / D    → turn left / right   (yaw)
 *  Space    → ascend
 *  Shift    → descend
 */
@OnlyIn(Dist.CLIENT)
public class ClientInputHandler {

    private static volatile boolean piloting = false;
    private static final int KEEPALIVE_TICKS = 5;
    private static final int MAX_CLIENT_THROTTLE_LEVEL = 32;
    public static final KeyMapping LEAVE_SHIP_KEY = new KeyMapping(
            "key.customships.leave_ship",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.customships"
    );

    private int ticksSinceLastSend = 0;
    private boolean lastFwd, lastBack, lastLeft, lastRight, lastUp, lastDown;
    private int throttleLevel = 1;
    private int lastSentThrottleLevel = 1;

    public static void setPiloting(boolean value) {
        piloting = value;
        if (!value) {
            restorePlayerInput(Minecraft.getInstance());
        }
    }

    public static boolean isPiloting() {
        return piloting;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            piloting = false;
            return;
        }

        // START: swap player.input before player.aiStep() reads it.
        if (event.phase == TickEvent.Phase.START) {
            if (piloting && !(mc.player.input instanceof FrozenInput)) {
                mc.player.input = new FrozenInput(mc.player.input);
            } else if (!piloting && mc.player.input instanceof FrozenInput fi) {
                mc.player.input = fi.original;
            }
            return;
        }

        // END: send ship control packet using raw hardware key state.
        if (!piloting) {
            return;
        }

        if (LEAVE_SHIP_KEY.consumeClick()) {
            ModPackets.sendToServer(new ShipControlPacket(false, false, false, false, false, false, true,
                    throttleLevel, mc.player.getYRot()));
            return;
        }

        boolean fwd   = mc.options.keyUp.isDown();
        boolean back  = mc.options.keyDown.isDown();
        boolean left  = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        boolean up    = mc.options.keyJump.isDown();
        boolean down  = mc.options.keyShift.isDown();

        boolean changed = fwd   != lastFwd  || back  != lastBack
                       || left  != lastLeft || right != lastRight
                       || up    != lastUp   || down  != lastDown
                       || throttleLevel != lastSentThrottleLevel;

        ticksSinceLastSend++;

        if (changed || ticksSinceLastSend >= KEEPALIVE_TICKS) {
            ModPackets.sendToServer(new ShipControlPacket(fwd, back, left, right, up, down, false,
                    throttleLevel, mc.player.getYRot()));
            lastFwd   = fwd;   lastBack  = back;
            lastLeft  = left;  lastRight = right;
            lastUp    = up;    lastDown  = down;
            lastSentThrottleLevel = throttleLevel;
            ticksSinceLastSend = 0;
        }
    }

    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!piloting) {
            return;
        }
        double delta = event.getScrollDelta();
        if (delta == 0.0D) {
            return;
        }

        throttleLevel = Math.max(1, Math.min(MAX_CLIENT_THROTTLE_LEVEL,
                throttleLevel + (delta > 0.0D ? 1 : -1)));
        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Vitesse " + throttleLevel),
                    true
            );
            ticksSinceLastSend = KEEPALIVE_TICKS;
        }
    }

    @SubscribeEvent
    public void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (piloting) {
            freezeInput(event.getInput());
        }
    }

    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        piloting = false;
        restorePlayerInput(Minecraft.getInstance());
    }

    /**
     * Replaces the player's Input while piloting — zeroes all movement so the
     * player character stays frozen. Restored when piloting ends.
     */
    private static class FrozenInput extends Input {
        final Input original;

        FrozenInput(Input original) {
            this.original = original;
        }

        @Override
        public void tick(boolean sneakingSlowed, float partialTick) {
            freezeInput(this);
        }
    }

    private static void freezeInput(Input input) {
        input.up             = false;
        input.down           = false;
        input.left           = false;
        input.right          = false;
        input.forwardImpulse = 0f;
        input.leftImpulse    = 0f;
        input.jumping        = false;
        input.shiftKeyDown   = false;
    }

    private static void restorePlayerInput(Minecraft mc) {
        if (mc.player != null && mc.player.input instanceof FrozenInput fi) {
            mc.player.input = fi.original;
        }
    }
}
