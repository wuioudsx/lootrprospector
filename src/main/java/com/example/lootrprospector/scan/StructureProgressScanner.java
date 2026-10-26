package com.example.lootrprospector.scan;

import com.example.lootrprospector.Config;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import noobanidus.mods.lootr.api.IHasOpeners;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Counts Lootr containers belonging to the structure the player is standing in.
 *
 * <p>Only structure starts that are already loaded are used. The lookup never
 * calls {@code getChunk}; it reads structure references from the player's loaded
 * chunk and only accepts start chunks returned by {@code getChunkNow}. This keeps
 * the progress lookup from changing the world's loading state.</p>
 */
public final class StructureProgressScanner {
    private static final TagKey<Block> LOOTR_CONTAINERS =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("lootr", "containers"));
    private static final int BUDGET_CHECK_INTERVAL = 256;

    private StructureProgressScanner() {
    }

    public static StructureProgress scan(ServerPlayer player) {
        if (!Config.enableStructureProgress) {
            return null;
        }

        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        LevelChunk playerChunk = level.getChunkSource().getChunkNow(center.getX() >> 4, center.getZ() >> 4);
        if (playerChunk == null) {
            return null;
        }

        List<StructureStart> candidates = new ArrayList<>();

        // Normal path: start positions referenced by the player's chunk.
        Map<Structure, LongSet> references = playerChunk.getAllReferences();
        for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
            Structure structure = entry.getKey();
            for (LongIterator iterator = entry.getValue().iterator(); iterator.hasNext(); ) {
                ChunkPos startChunkPos = new ChunkPos(iterator.nextLong());
                LevelChunk startChunk = level.getChunkSource()
                        .getChunkNow(startChunkPos.x, startChunkPos.z);
                if (startChunk == null) {
                    continue;
                }
                StructureStart start = startChunk.getStartForStructure(structure);
                if (start != null && start.isValid() && hasPieceAt(start, center)) {
                    candidates.add(start);
                }
            }
        }

        // Fallback for a structure whose start is in the player's own chunk.
        for (StructureStart start : playerChunk.getAllStarts().values()) {
            if (start != null && start.isValid() && hasPieceAt(start, center)
                    && !candidates.contains(start)) {
                candidates.add(start);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator.comparing(start -> String.valueOf(structureId(level, start.getStructure()))));
        long startNanos = System.nanoTime();
        long deadline = Config.structureProgressBudgetMs > 0
                ? startNanos + Config.structureProgressBudgetMs * 1_000_000L
                : Long.MAX_VALUE;

        StructureStart selected = candidates.get(0);
        return count(level, selected, player, deadline);
    }

    private static StructureProgress count(ServerLevel level, StructureStart start,
                                           ServerPlayer player, long deadline) {
        List<BoundingBox> pieces = new ArrayList<>(start.getPieces().size());
        for (StructurePiece piece : start.getPieces()) {
            pieces.add(piece.getBoundingBox());
        }
        if (pieces.isEmpty()) {
            return null;
        }

        BoundingBox bounds = start.getBoundingBox();
        int minChunkX = bounds.minX() >> 4;
        int maxChunkX = bounds.maxX() >> 4;
        int minChunkZ = bounds.minZ() >> 4;
        int maxChunkZ = bounds.maxZ() >> 4;

        int opened = 0;
        int total = 0;
        int unloadedChunks = 0;
        int scannedChunks = 0;
        int scannedBlockEntities = 0;
        boolean budgetExceeded = false;

        outer:
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (isBudgetExceeded(deadline)) {
                    budgetExceeded = true;
                    break outer;
                }

                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    unloadedChunks++;
                    continue;
                }
                scannedChunks++;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    scannedBlockEntities++;
                    if (scannedBlockEntities % BUDGET_CHECK_INTERVAL == 0
                            && isBudgetExceeded(deadline)) {
                        budgetExceeded = true;
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

                    BlockPos pos = blockEntity.getBlockPos();
                    if (!insideAnyPiece(pieces, pos)) {
                        continue;
                    }

                    total++;
                    if (openers.getOpeners().contains(player.getUUID())) {
                        opened++;
                    }
                }
            }
        }

        ResourceLocation id = structureId(level, start.getStructure());
        String name = id == null ? "unknown" : id.getPath().replace('_', ' ');
        return new StructureProgress(name, opened, total, unloadedChunks, scannedChunks, budgetExceeded);
    }

    private static boolean hasPieceAt(StructureStart start, BlockPos pos) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece.getBoundingBox().isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideAnyPiece(List<BoundingBox> pieces, BlockPos pos) {
        for (BoundingBox box : pieces) {
            if (box.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation structureId(ServerLevel level, Structure structure) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
    }

    private static boolean isBudgetExceeded(long deadline) {
        return deadline != Long.MAX_VALUE && System.nanoTime() >= deadline;
    }
}
