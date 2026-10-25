package com.example.lootrprospector.network;

import com.example.lootrprospector.client.ClientProspectingState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ScanResultPacket(List<BlockPos> positions, ResourceLocation dimension, int radius,
                               int highlightTicks, boolean truncated) {
    public ScanResultPacket {
        positions = List.copyOf(positions);
    }

    public static void encode(ScanResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.dimension());
        buffer.writeVarInt(packet.radius());
        buffer.writeVarInt(packet.highlightTicks());
        buffer.writeBoolean(packet.truncated());
        buffer.writeVarInt(packet.positions().size());
        for (BlockPos pos : packet.positions()) {
            buffer.writeBlockPos(pos);
        }
    }

    public static ScanResultPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int radius = buffer.readVarInt();
        int highlightTicks = buffer.readVarInt();
        boolean truncated = buffer.readBoolean();
        int count = buffer.readVarInt();
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(buffer.readBlockPos());
        }
        return new ScanResultPacket(positions, dimension, radius, highlightTicks, truncated);
    }

    public static void handle(ScanResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientProspectingState.accept(packet));
    }
}
