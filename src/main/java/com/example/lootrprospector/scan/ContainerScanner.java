package com.example.lootrprospector.scan;

import com.example.lootrprospector.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import noobanidus.mods.lootr.api.IHasOpeners;
import noobanidus.mods.lootr.api.entity.ILootCart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ContainerScanner {
    private static final TagKey<Block> LOOTR_CONTAINERS =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("lootr", "containers"));
    private static final int MAX_CANDIDATES = 4096;

    private ContainerScanner() {
    }

    public static ScanResult scan(ServerPlayer player, int radius) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        long radiusSq = (long) radius * radius;

        List<BlockPos> found = new ArrayList<>();
        int unloadedChunks = 0;
        boolean overflow = false;

        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;

        outer:
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    unloadedChunks++;
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (found.size() >= MAX_CANDIDATES) {
                        overflow = true;
                        break outer;
                    }
                    if (blockEntity.isRemoved()) {
                        continue;
                    }
                    if (!(blockEntity instanceof IHasOpeners openers)) {
                        continue;
                    }
                    if (!blockEntity.getBlockState().is(LOOTR_CONTAINERS)) {
                        continue;
                    }
                    if (openers.getOpeners().contains(player.getUUID())) {
                        continue;
                    }

                    BlockPos pos = blockEntity.getBlockPos();
                    if (center.distSqr(pos) > radiusSq) {
                        continue;
                    }
                    found.add(pos.immutable());
                }
            }
        }

        if (Config.scanMinecarts && !overflow) {
            AABB area = player.getBoundingBox().inflate(radius);
            for (Entity entity : level.getEntities(player, area, e -> e instanceof ILootCart)) {
                ILootCart cart = (ILootCart) entity;
                if (cart.getOpeners().contains(player.getUUID())) {
                    continue;
                }

                BlockPos pos = entity.blockPosition();
                if (center.distSqr(pos) > radiusSq) {
                    continue;
                }
                if (found.size() >= MAX_CANDIDATES) {
                    overflow = true;
                    break;
                }
                found.add(pos.immutable());
            }
        }

        found.sort(Comparator.comparingDouble(pos -> center.distSqr(pos)));
        int total = found.size();
        boolean truncated = overflow || total > Config.maxHighlights;
        int limit = Math.min(Config.maxHighlights, total);
        List<BlockPos> positions = List.copyOf(found.subList(0, limit));

        return new ScanResult(radius, total, unloadedChunks, truncated, positions);
    }
}
