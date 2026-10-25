package com.example.lootrprospector.scan;

import net.minecraft.core.BlockPos;

import java.util.List;

public record ScanResult(int radius, int total, int unloadedChunks, boolean truncated, List<BlockPos> positions) {
    public ScanResult {
        positions = List.copyOf(positions);
    }
}
