package com.example.lootrprospector.item;

import com.example.lootrprospector.LootrProspector;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final RegistryObject<Item> DIVINING_ROD = LootrProspector.ITEMS.register("divining_rod",
            () -> new DiviningRodItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private ModItems() {
    }

    /**
     * Forces this class to initialize, which adds the registry entries to the
     * mod's DeferredRegister. The mod constructor calls this before registering
     * the DeferredRegister on the mod event bus.
     */
    public static void init() {
    }
}
