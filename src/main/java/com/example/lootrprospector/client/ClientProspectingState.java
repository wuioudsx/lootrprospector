package com.example.lootrprospector.client;

import com.example.lootrprospector.network.ScanResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class ClientProspectingState {
    private static List<BlockPos> positions = List.of();
    private static ResourceLocation dimension;
    private static long expiresAt;

    private ClientProspectingState() {
    }

    public static void accept(ScanResultPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (!minecraft.level.dimension().location().equals(packet.dimension())) {
            clear();
            return;
        }

        positions = packet.positions();
        dimension = packet.dimension();
        expiresAt = minecraft.level.getGameTime() + packet.highlightTicks();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        if (dimension != null && (!minecraft.level.dimension().location().equals(dimension)
                || minecraft.level.getGameTime() > expiresAt)) {
            clear();
        }
    }

    public static boolean isActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && dimension != null
                && !positions.isEmpty()
                && minecraft.level.dimension().location().equals(dimension)
                && minecraft.level.getGameTime() <= expiresAt;
    }

    public static List<BlockPos> positions() {
        return positions;
    }

    public static void clear() {
        positions = List.of();
        dimension = null;
        expiresAt = 0L;
    }
}
