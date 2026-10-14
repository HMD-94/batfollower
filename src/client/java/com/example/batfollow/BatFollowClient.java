package com.example.batfollow;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.Optional;

public class BatFollowClient implements ClientModInitializer {

    private static final double SEARCH_RADIUS = 32.0;
    private static final double STOP_DISTANCE = 2.0;

    private static KeyMapping toggleKey;
    private static boolean following = false;
    private static Bat target = null;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.batfollow.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("batfollow", "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            following = false;
            target = null;
            return;
        }

        while (toggleKey.consumeClick()) {
            following = !following;
            if (following) {
                target = findNearestBat(mc, player);
                if (target == null) {
                    following = false;
                    player.displayClientMessage(Component.literal("Aucune chauve-souris à proximité"), true);
                } else {
                    player.displayClientMessage(Component.literal("Suivi de la chauve-souris : ON"), true);
                }
            } else {
                stop(mc);
                player.displayClientMessage(Component.literal("Suivi de la chauve-souris : OFF"), true);
            }
        }

        if (!following) return;

        // Si la cible est morte ou trop loin, on en cherche une autre (la plus proche)
        if (target == null || !target.isAlive() || target.distanceTo(player) > SEARCH_RADIUS) {
            target = findNearestBat(mc, player);
            if (target == null) {
                following = false;
                stop(mc);
                player.displayClientMessage(Component.literal("Plus de chauve-souris à suivre"), true);
                return;
            }
        } else {
            // Rebascule sur la plus proche si une autre est devenue plus proche
            Bat nearest = findNearestBat(mc, player);
            if (nearest != null && nearest != target && nearest.distanceTo(player) + 2.0 < target.distanceTo(player)) {
                target = nearest;
            }
        }

        // Orientation vers la cible
        Vec3 eye = player.getEyePosition();
        Vec3 dest = target.getBoundingBox().getCenter();
        double dx = dest.x - eye.x;
        double dy = dest.y - eye.y;
        double dz = dest.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float targetPitch = (float) -(Mth.atan2(dy, horiz) * 180.0 / Math.PI);

        player.setYRot(Mth.approachDegrees(player.getYRot(), targetYaw, 25.0f));
        player.setXRot(Mth.clamp(Mth.approach(player.getXRot(), targetPitch, 15.0f), -90.0f, 90.0f));

        // Déplacement
        boolean close = horiz < STOP_DISTANCE;
        mc.options.keyUp.setDown(!close);
        mc.options.keySprint.setDown(!close && horiz > 6.0);

        boolean needJump = !close && player.onGround()
                && (player.horizontalCollision || dy > 1.0);
        mc.options.keyJump.setDown(needJump);
    }

    private static Bat findNearestBat(Minecraft mc, LocalPlayer player) {
        Optional<Bat> best = mc.level
                .getEntitiesOfClass(Bat.class, player.getBoundingBox().inflate(SEARCH_RADIUS), Bat::isAlive)
                .stream()
                .min(Comparator.comparingDouble(b -> b.distanceToSqr(player)));
        return best.orElse(null);
    }

    private static void stop(Minecraft mc) {
        following = false;
        target = null;
        mc.options.keyUp.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
    }
}
