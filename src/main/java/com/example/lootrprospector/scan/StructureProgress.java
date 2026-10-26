package com.example.lootrprospector.scan;

public record StructureProgress(
        String name,
        String key,
        int opened,
        int total,
        int unloadedChunks,
        int scannedChunks,
        boolean budgetExceeded) {
    public boolean incomplete() {
        return budgetExceeded || unloadedChunks > 0;
    }
}
