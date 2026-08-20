/*
 * Copyright (c) 2026 [Hoogkids]
 * Licensed under the Creative Commons Attribution 4.0 International License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/
 */

package com.guicreator.guiCreator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GuiCommandExecutor implements CommandExecutor {

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can design menus interactively!", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /guicreator [create/edit/delete/list/help]", NamedTextColor.RED));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                openGuiCreatorCanvas(player);
                break;

            case "edit":
                player.sendMessage(Component.text("Usage: /guicreator edit <name>", NamedTextColor.YELLOW));
                break;

            case "delete":
                player.sendMessage(Component.text("Usage: /guicreator delete <name>", NamedTextColor.RED));
                break;

            case "list":
                player.sendMessage(Component.text("Use /guicreator list to print all profiles.", NamedTextColor.AQUA));
                break;

            case "help":
            default:
                player.sendMessage(Component.text("=== GuiCreator Help Menu ===", NamedTextColor.GOLD));
                player.sendMessage(Component.text("/guicreator create <name> - Design a new workspace setup.", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/guicreator edit <name>   - Modify an existing template profile.", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/guicreator list          - Show all registered GUI creations.", NamedTextColor.GRAY));
                break;
        }
        return true;
    }

    private void openGuiCreatorCanvas(Player player) {
        Component title = Component.text("GuiCreator Canvas Workspace", NamedTextColor.DARK_GRAY);
        Inventory gui = Bukkit.createInventory(player, 27, title);

        ItemStack borderPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = borderPane.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(Component.text(" ", NamedTextColor.DARK_GRAY));
            borderPane.setItemMeta(borderMeta);
        }

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, borderPane);
            gui.setItem(i + 18, borderPane);
        }
        gui.setItem(9, borderPane);
        gui.setItem(17, borderPane);

        ItemStack createButton = new ItemStack(Material.ANVIL);
        ItemMeta createMeta = createButton.getItemMeta();
        if (createMeta != null) {
            createMeta.displayName(Component.text("Create New Menu", NamedTextColor.GREEN));
            createMeta.lore(List.of(
                    Component.text("Click to configure a customized item layout container.", NamedTextColor.GRAY)
            ));
            createButton.setItemMeta(createMeta);
        }
        gui.setItem(11, createButton);

        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Component.text("Close Workspace", NamedTextColor.RED));
            closeButton.setItemMeta(closeMeta);
        }
        gui.setItem(15, closeButton);

        player.openInventory(gui);
        player.sendMessage(Component.text("Opening your interactive canvas layout grid!", NamedTextColor.GREEN));
    }
}