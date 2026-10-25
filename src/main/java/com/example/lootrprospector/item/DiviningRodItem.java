package com.example.lootrprospector.item;

import com.example.lootrprospector.Config;
import com.example.lootrprospector.LootrProspector;
import com.example.lootrprospector.network.ModNetwork;
import com.example.lootrprospector.network.ScanResultPacket;
import com.example.lootrprospector.scan.ContainerScanner;
import com.example.lootrprospector.scan.ScanResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DiviningRodItem extends Item {
    public DiviningRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        if (serverPlayer.isSpectator()) {
            return InteractionResultHolder.pass(stack);
        }
        if (serverPlayer.getCooldowns().isOnCooldown(this)) {
            serverPlayer.displayClientMessage(Component.translatable("message.lootrprospector.cooldown"), true);
            return InteractionResultHolder.fail(stack);
        }

        scan(serverPlayer);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (serverPlayer.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (serverPlayer.getCooldowns().isOnCooldown(this)) {
            serverPlayer.displayClientMessage(Component.translatable("message.lootrprospector.cooldown"), true);
            return InteractionResult.FAIL;
        }

        scan(serverPlayer);
        return InteractionResult.SUCCESS;
    }

    private void scan(ServerPlayer player) {
        long start = System.nanoTime();
        ScanResult result = ContainerScanner.scan(player, Config.scanRadius);
        long elapsedMicros = (System.nanoTime() - start) / 1_000L;

        if (Config.debugLogScan) {
            LootrProspector.LOGGER.info(
                    "Divining rod scan by {}: total={}, unloadedChunks={}, took={} us",
                    player.getGameProfile().getName(), result.total(), result.unloadedChunks(), elapsedMicros);
        }

        sendFeedback(player, result);

        if (Config.enableHighlight) {
            ModNetwork.sendToPlayer(player, new ScanResultPacket(
                    result.positions(),
                    player.serverLevel().dimension().location(),
                    result.radius(),
                    Config.highlightTicks,
                    result.truncated()));
        }

        player.getCooldowns().addCooldown(this, Config.cooldownTicks);
    }

    private void sendFeedback(ServerPlayer player, ScanResult result) {
        Component message;
        if (result.total() == 0) {
            message = Component.translatable("message.lootrprospector.none", result.radius());
        } else {
            double nearest = Math.sqrt(player.blockPosition().distSqr(result.positions().get(0)));
            message = Component.translatable("message.lootrprospector.found",
                    result.total(), result.radius(), Math.round(nearest));
        }

        if (result.unloadedChunks() > 0 && Config.showUnloadedHint) {
            message = message.copy().append(Component.translatable("message.lootrprospector.unloaded_hint"));
        }
        if (result.truncated()) {
            message = message.copy().append(
                    Component.translatable("message.lootrprospector.truncated", Config.maxHighlights));
        }
        player.displayClientMessage(message, true);

        if (result.total() > 0 && Config.chatListLimit > 0) {
            int limit = Math.min(Config.chatListLimit, result.positions().size());
            player.sendSystemMessage(Component.translatable("message.lootrprospector.list_header", limit)
                    .withStyle(ChatFormatting.GOLD));
            for (int i = 0; i < limit; i++) {
                BlockPos pos = result.positions().get(i);
                double distance = Math.sqrt(player.blockPosition().distSqr(pos));
                player.sendSystemMessage(Component.translatable("message.lootrprospector.list_entry",
                                i + 1, pos.getX(), pos.getY(), pos.getZ(), Math.round(distance))
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        if (Config.playSound) {
            if (result.total() == 0) {
                player.serverLevel().playSound(null, player.blockPosition(),
                        SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
            } else {
                player.serverLevel().playSound(null, player.blockPosition(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lootrprospector.divining_rod.tooltip.radius", Config.scanRadius)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lootrprospector.divining_rod.tooltip.cooldown",
                        Config.cooldownTicks / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.lootrprospector.divining_rod.tooltip.opened")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
