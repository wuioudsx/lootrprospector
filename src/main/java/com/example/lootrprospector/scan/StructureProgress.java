package com.example.lootrprospector.scan;

public record StructureProgress(
        String name,
        int opened,
        int total,
        int unloadedChunks,
        int scannedChunks,
        boolean budgetExceeded) {
    public boolean incomplete() {
        return budgetExceeded || unloadedChunks > 0;
    }
}
