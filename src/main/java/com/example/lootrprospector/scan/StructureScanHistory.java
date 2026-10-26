package com.example.lootrprospector.scan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Remembers which structure instances this player has already scanned.
 * Stored in the player's Forge persistent data, so the "first scan" hint is
 * shown once per structure, not once per login.
 */
public final class StructureScanHistory {
    private static final String TAG_SCANNED = "lootrprospector_scanned_structures";

    private StructureScanHistory() {
    }

    /**
     * @return true if this structure key was not present before (the first scan)
     */
    public static boolean markFirstScan(ServerPlayer player, String key) {
        CompoundTag data = player.getPersistentData();
        ListTag scanned = data.getList(TAG_SCANNED, Tag.TAG_STRING);
        for (int i = 0; i < scanned.size(); i++) {
            if (key.equals(scanned.getString(i))) {
                return false;
            }
        }
        scanned.add(StringTag.valueOf(key));
        data.put(TAG_SCANNED, scanned);
        return true;
    }
}
