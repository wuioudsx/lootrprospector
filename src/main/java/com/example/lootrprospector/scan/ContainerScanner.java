package com.example.lootrprospector.scan;

import com.example.lootrprospector.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
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
    private static final int BUDGET_CHECK_INTERVAL = 256;

    private ContainerScanner() {
    }

    public static ScanResult scan(ServerPlayer player, int radius) {
        long start = System.nanoTime();
        int budgetMs = Config.scanTimeBudgetMs;
        long deadline = budgetMs > 0
                ? start + budgetMs * 1_000_000L
                : Long.MAX_VALUE;

        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        long radiusSq = (long) radius * radius;

        List<BlockPos> found = new ArrayList<>();
        int unloadedChunks = 0;
        int scannedChunks = 0;
        int scannedBlockEntities = 0;
        boolean overflow = false;
        boolean budgetExceeded = false;

        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;

        // Visit chunks nearest-first so that a time-budget stop still covers the
        // most useful area around the player.
        List<ChunkPos> chunks = new ArrayList<>(
                (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        chunks.sort(Comparator.comparingLong((ChunkPos pos) -> {
            long dx = (long) pos.x - centerChunkX;
            long dz = (long) pos.z - centerChunkZ;
            return dx * dx + dz * dz;
        }));

        outer:
        for (ChunkPos chunkPos : chunks) {
            if (isBudgetExceeded(deadline)) {
                budgetExceeded = true;
                break;
            }

            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                unloadedChunks++;
                continue;
            }
            scannedChunks++;

            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                scannedBlockEntities++;
                if (scannedBlockEntities % BUDGET_CHECK_INTERVAL == 0 && isBudgetExceeded(deadline)) {
                    budgetExceeded = true;
                    break outer;
                }
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

        if (Config.scanMinecarts && !overflow && !budgetExceeded) {
            if (isBudgetExceeded(deadline)) {
                budgetExceeded = true;
            } else {
                AABB area = player.getBoundingBox().inflate(radius);
                for (Entity entity : level.getEntities(player, area, e -> e instanceof ILootCart)) {
                    if (isBudgetExceeded(deadline)) {
                        budgetExceeded = true;
                        break;
                    }
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
        }

        found.sort(Comparator.comparingDouble(pos -> center.distSqr(pos)));
        int total = found.size();
        boolean truncated = overflow || total > Config.maxHighlights;
        int limit = Math.min(Config.maxHighlights, total);
        List<BlockPos> positions = List.copyOf(found.subList(0, limit));

        return new ScanResult(radius, total, unloadedChunks, scannedChunks, scannedBlockEntities,
                truncated, budgetExceeded, positions);
    }

    private static boolean isBudgetExceeded(long deadline) {
        return deadline != Long.MAX_VALUE && System.nanoTime() >= deadline;
    }
}
