package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.common.inventory.InventoryBaseImpl;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

public class StatementPlayerHand extends StatementItems {

    @Override
    public boolean match(String text) {
        return text.startsWith("playerhand");
    }

    @Override
    public boolean matchArray(String text) {
        return text.equals("ph");
    }

    @Override
    public Inventory getInventory(MinecartMember<?> member) {
        ItemStack item = null;
        if (member.getEntity().hasPlayerPassenger()) {
            item = member.getPlayerInventory().getItemInMainHand();
        }
        if (LogicUtil.nullOrEmpty(item)) {
            return new InventoryBaseImpl(new org.bukkit.inventory.ItemStack[0]);
        } else {
            return new InventoryBaseImpl(new org.bukkit.inventory.ItemStack[]{item});
        }
    }

    @Override
    public Inventory getInventory(MinecartGroup group) {
        ArrayList<org.bukkit.inventory.ItemStack> items = new ArrayList<>();
        for (MinecartMember<?> member : group) {
            if (member.getEntity().hasPlayerPassenger()) {
                ItemStack item = member.getPlayerInventory().getItemInMainHand();
                if (!LogicUtil.nullOrEmpty(item)) {
                    items.add(item);
                }
            }
        }
        return new InventoryBaseImpl(items, false);
    }
}
