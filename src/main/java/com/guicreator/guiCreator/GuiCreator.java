/*
 * Copyright (c) 2026 [Hoogkids]
 * Licensed under the Creative Commons Attribution 4.0 International License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/
 */

package com.guicreator.guiCreator;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.RegistryBuilderFactory;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public class GuiCreator extends JavaPlugin implements Listener, TabCompleter {

    private static Economy econ = null;
    private final Map<UUID, String> activeEditingGui = new HashMap<>();
    private final Map<UUID, Integer> activeGuiRows = new HashMap<>();
    private final Map<UUID, Integer> activeEditingPage = new HashMap<>();
    private final Map<UUID, String> activeGuiTitle = new HashMap<>();

    private final Map<UUID, Integer> editState = new HashMap<>();
    private final Map<UUID, Integer> targetEditSlot = new HashMap<>();
    private final Map<UUID, Enchantment> selectedEnchantment = new HashMap<>();
    private final Map<UUID, Attribute> selectedAttribute = new HashMap<>();
    private final Set<UUID> navigatingSubMenu = new HashSet<>();
    private final Map<UUID, ItemStack> itemBeingEdited = new HashMap<>();

    private final Set<UUID> insidePropertiesMenu = new HashSet<>();
    private final Set<UUID> insideEnchantSelector = new HashSet<>();
    private final Set<UUID> insideAttributeSelector = new HashSet<>();
    private final Set<UUID> insideRemovalMenu = new HashSet<>();

    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();
    private final Map<UUID, Stack<String>> playerGuiHistory = new HashMap<>();
    private final Map<UUID, String> currentOpenGuiName = new HashMap<>();
    private final Set<UUID> processingWarpShift = new HashSet<>();

    private File guiDirectory;
    private CommandMap commandMap;
    private NamespacedKey enchantKey;
    private NamespacedKey attributeKey;
    private NamespacedKey removeEnchantKey;
    private NamespacedKey removeAttributeKey;
    private final Set<UUID> insideAddonsMenu = new HashSet<>();
    private final Set<UUID> insideVaultMenu = new HashSet<>();
    private final Map<Integer, Double> itemClickCosts = new HashMap<>();
    private final Map<UUID, org.bukkit.GameMode> previousGameMode = new HashMap<>();
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> pendingAutoSave = new HashMap<>();
    private final Set<UUID> insideCommandsMenu = new HashSet<>();
    private final Set<UUID> insideBindCommandMenu = new HashSet<>();
    private final Set<UUID> insideGuiTypeMenu = new HashSet<>();
    private final Set<UUID> insideBossbarMenu = new HashSet<>();
    private final Set<UUID> insideDialogMenu = new HashSet<>();
    private final Set<UUID> insideSoundMenu = new HashSet<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) getLogger().warning("Vault not found! Economy features disabled.");

        this.guiDirectory = new File(getDataFolder(), "guis");
        if (!guiDirectory.exists() && !guiDirectory.mkdirs()) {
            getLogger().warning("Could not create GUIs directory!");
        }
        this.enchantKey = new NamespacedKey(this, "enchant_id");
        this.attributeKey = new NamespacedKey(this, "attribute_id");
        this.removeEnchantKey = new NamespacedKey(this, "rem_enchant_id");
        this.removeAttributeKey = new NamespacedKey(this, "rem_attribute_id");

        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            this.commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            getLogger().severe("Failed to hook into server CommandMap!");
        }

        var mainCommand = this.getCommand("guicreator");
        if (mainCommand != null) {
            mainCommand.setExecutor(this);
            mainCommand.setTabCompleter(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        loadCustomCommandsFromFiles();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return true;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("guicreator")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                sendHelpMenu(player);
                return true;
            }

            String action = args[0].toLowerCase();

            if (action.equals("delete")) {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Error: Provide the GUI file name! Example: /guicreator delete mygui", NamedTextColor.RED));
                    return true;
                }
                String targetName = args[1].toLowerCase().replace(" ", "");
                File targetFile = new File(guiDirectory, targetName + ".yml");

                if (!targetFile.exists()) {
                    player.sendMessage(Component.text("Error: That GUI file doesn't exist!", NamedTextColor.RED));
                    return true;
                }

                if (targetFile.delete()) {
                    player.sendMessage(Component.text("Successfully deleted " + targetName + ".yml!", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Error: Critical error attempting to delete file.", NamedTextColor.RED));
                }
                return true;
            }

            if (action.equals("list")) {
                File[] files = guiDirectory.listFiles((dir, name) -> name.endsWith(".yml"));

                player.sendMessage(Component.text("\n========== Registered GUIs ==========", NamedTextColor.GOLD));

                if (files == null || files.length == 0) {
                    player.sendMessage(Component.text(" No GUI profiles found. Use /guicreator create <name> to build one!", NamedTextColor.GRAY));
                } else {
                    for (File file : files) {
                        String guiName = file.getName().replace(".yml", "");
                        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

                        String attachedCmd = config.getString("command", "None");
                        int rows = config.getInt("rows", 1);

                        TextComponent guiLine = Component.text(" → ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(guiName, NamedTextColor.GREEN))
                                .append(Component.text(" (" + (rows * 9) + " slots)", NamedTextColor.GRAY))
                                .append(Component.text(" [Command: /" + attachedCmd + "]", NamedTextColor.AQUA))
                                .hoverEvent(HoverEvent.showText(Component.text("§eClick here to instantly enter the §a" + guiName + " §eediting workspace framework.")))
                                .clickEvent(ClickEvent.runCommand("/guicreator edit " + guiName));

                        player.sendMessage(guiLine);
                    }
                }
                player.sendMessage(Component.text("=====================================", NamedTextColor.GOLD));
                return true;
            }

            if (action.equals("create") || action.equals("edit")) {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Error: Provide a name! Example: /guicreator " + action + " mygui", NamedTextColor.RED));
                    return true;
                }

                String guiName = args[1].toLowerCase().replace(" ", "");
                File guiFile = new File(guiDirectory, guiName + ".yml");

                playerGuiHistory.remove(player.getUniqueId());
                currentOpenGuiName.remove(player.getUniqueId());
                insidePropertiesMenu.remove(player.getUniqueId());
                insideEnchantSelector.remove(player.getUniqueId());
                insideAttributeSelector.remove(player.getUniqueId());
                insideRemovalMenu.remove(player.getUniqueId());
                clearTimeout(player.getUniqueId());

                if (action.equals("create")) {
                    if (guiFile.exists()) {
                        player.sendMessage(Component.text("Error: GUI already exists! Use /guicreator edit " + guiName, NamedTextColor.RED));
                        return true;
                    }
                    activeEditingGui.put(player.getUniqueId(), guiName);
                    activeGuiRows.put(player.getUniqueId(), 1);
                    activeGuiTitle.put(player.getUniqueId(), "&8Custom Menu: " + guiName);
                    openWorkspace(player);
                } else {
                    if (!guiFile.exists()) {
                        player.sendMessage(Component.text("Error: GUI not found! Use /guicreator create " + guiName, NamedTextColor.RED));
                        return true;
                    }
                    activeEditingGui.put(player.getUniqueId(), guiName);
                    FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
                    activeGuiRows.put(player.getUniqueId(), config.getInt("rows", 1));
                    activeGuiTitle.put(player.getUniqueId(), config.getString("title", "&8Custom Menu: " + guiName));
                    openWorkspace(player);
                }
                return true;
            }
            sendHelpMenu(player);
            return true;
        }
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("guicreator")) {
            if (args.length == 1) {
                List<String> subCommands = Arrays.asList("create", "edit", "delete", "list", "help");
                String input = args[0].toLowerCase();

                for (String sub : subCommands) {
                    if (sub.startsWith(input)) {
                        completions.add(sub);
                    }
                }
                return completions;
            }

            if (args.length == 2) {
                String action = args[0].toLowerCase();
                if (action.equals("edit") || action.equals("delete")) {
                    String input = args[1].toLowerCase();
                    File[] files = guiDirectory.listFiles((dir, name) -> name.endsWith(".yml"));

                    if (files != null) {
                        for (File file : files) {
                            String guiName = file.getName().replace(".yml", "");
                            if (guiName.startsWith(input)) {
                                completions.add(guiName);
                            }
                        }
                    }
                    return completions;
                }
            }
        }
        return completions;
    }

    private void sendHelpMenu(Player player) {
        player.sendMessage(Component.text("\n========= GuiCreator Help Menu =========", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/guicreator create <name> - Fresh setup.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/guicreator edit <name> - Edit old files.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/guicreator delete <name> - Completely remove file.", NamedTextColor.RED));
        player.sendMessage(Component.text("/guicreator list - Display every saved GUI menu file.", NamedTextColor.AQUA));
        player.sendMessage(Component.text("===========================================", NamedTextColor.GOLD));
    }

    public void openWorkspace(Player player) {
        insidePropertiesMenu.remove(player.getUniqueId());
        insideEnchantSelector.remove(player.getUniqueId());
        insideAttributeSelector.remove(player.getUniqueId());
        insideRemovalMenu.remove(player.getUniqueId());
        insideAddonsMenu.remove(player.getUniqueId());
        insideVaultMenu.remove(player.getUniqueId());
        insideSoundMenu.remove(player.getUniqueId());
        insideCommandsMenu.remove(player.getUniqueId());
        insideGuiTypeMenu.remove(player.getUniqueId());
        insideDialogMenu.remove(player.getUniqueId());
        clearTimeout(player.getUniqueId());

        String guiName = activeEditingGui.get(player.getUniqueId());
        int rows = activeGuiRows.getOrDefault(player.getUniqueId(), 1);
        String titleString = activeGuiTitle.getOrDefault(player.getUniqueId(), "Editing Canvas");

        String guiTypeStr = "CHEST";
        File guiFileForType = new File(guiDirectory, guiName + ".yml");
        if (guiFileForType.exists()) {
            FileConfiguration typeConfig = YamlConfiguration.loadConfiguration(guiFileForType);
            guiTypeStr = typeConfig.getString("gui_type", "CHEST");
        }
        if ("DIALOG".equals(guiTypeStr)) guiTypeStr = "CHEST";

        org.bukkit.event.inventory.InventoryType workspaceType;
        try {
            workspaceType = org.bukkit.event.inventory.InventoryType.valueOf(guiTypeStr);
        } catch (IllegalArgumentException e) {
            workspaceType = org.bukkit.event.inventory.InventoryType.CHEST;
        }

        Component workspaceTitle = LegacyComponentSerializer.legacyAmpersand().deserialize(titleString + " &7(Editing)");
        Inventory workspace;
        if (workspaceType == org.bukkit.event.inventory.InventoryType.CHEST) {
            workspace = Bukkit.createInventory(null, rows * 9, workspaceTitle);
        } else {
            workspace = Bukkit.createInventory(null, workspaceType, workspaceTitle);
        }

        File guiFile = new File(guiDirectory, guiName + ".yml");
        if (guiFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
            int editPage = activeEditingPage.getOrDefault(player.getUniqueId(), 1);
            String itemsKey = editPage == 1 ? "items" : "dialog.page." + editPage + ".items";
            ConfigurationSection section = config.getConfigurationSection(itemsKey);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    int slot = Integer.parseInt(key);
                    if (slot < workspace.getSize()) {
                        ItemStack stored = config.getItemStack(itemsKey + "." + key + ".item");
                        String navType = config.getString(itemsKey + "." + key + ".dialog_nav", "");
                        if (!navType.isEmpty()) {
                            ItemStack savedNavItem = config.getItemStack(itemsKey + "." + key + ".item");
                            workspace.setItem(slot, savedNavItem != null ? savedNavItem : createNavButton(navType));
                        } else if (stored != null) {
                            workspace.setItem(slot, stored);
                        }
                    }
                }
            }
        }
        if (guiFile.exists()) {
            FileConfiguration config2 = YamlConfiguration.loadConfiguration(guiFile);
            int editPage = activeEditingPage.getOrDefault(player.getUniqueId(), 1);
            if (editPage > 1) {
                String backKey = "dialog.page." + editPage + ".items.0.item";
                ItemStack savedBack = config2.getItemStack(backKey);
                workspace.setItem(0, savedBack != null ? savedBack : createNavButton("back_page"));
            }
        }

        navigatingSubMenu.add(player.getUniqueId());

        Inventory pInv = player.getInventory();
        for (int i = 9; i < 36; i++) pInv.setItem(i, null);

        player.openInventory(workspace);

        Bukkit.getScheduler().runTaskLater(this, (Runnable) () -> {
            giveEditorButtons(player);
            navigatingSubMenu.remove(player.getUniqueId());
        }, 2L);
    }

    private void giveEditorButtons(Player player) {
        if (!activeEditingGui.containsKey(player.getUniqueId())) return;

        Inventory pInv = player.getInventory();
        for (int i = 9; i < 36; i++) pInv.setItem(i, null);

        pInv.setItem(9, createActionButton(Material.GREEN_CONCRETE, "§a§l[ Add Row ]", "§7Expands menu size up to 6 rows.\n§7At max rows, creates a new page."));
        pInv.setItem(10, createActionButton(Material.RED_CONCRETE, "§c§l[ Delete Row ]", "§7Removes the last row."));
        pInv.setItem(11, createActionButton(Material.BLUE_CONCRETE, "§b§l[ Bind Command ]", "§7Manage the command trigger for this GUI."));
        pInv.setItem(12, createActionButton(Material.ANVIL, "§6§l[ Rename GUI Title ]", "§7Changes the global window name banner."));
        pInv.setItem(13, createActionButton(Material.BLAZE_POWDER, "§c§l[ Item Properties Changer ]", "§eClick to configure individual properties."));
        pInv.setItem(14, createActionButton(Material.COMPASS, "§e§l[ Creative Picker ]", "§7Open menu to grab any block."));
        pInv.setItem(15, createActionButton(Material.IRON_BARS, "§3§l[ Edit Permissions ]", "§7Type a global permission required to view this menu."));
        pInv.setItem(16, createActionButton(Material.BARREL, "§9§l[ Change GUI Type ]", "§7Change the inventory type (chest, anvil, etc)."));
        pInv.setItem(17, createDialogToggleButton(player));

        pInv.setItem(25, null);
        pInv.setItem(26, createActionButton(Material.RED_CONCRETE, "§c§l[ Close ]", "§7Closes the editor.\n§7Your layout is auto-saved."));
        player.updateInventory();
    }

    /** Creates the visual ItemStack used to represent a page-navigation slot in the canvas. */
    private ItemStack createNavButton(String navType) {
        if ("next_page".equals(navType)) {
            return createActionButton(Material.SPECTRAL_ARROW,
                    "§6§l» Next Page »",
                    "§7Takes players to the next page.\n§8(Protected nav slot)");
        } else {
            return createActionButton(Material.ARROW,
                    "§c§l« Back «",
                    "§7Takes players to the previous page.\n§8(Protected nav slot)");
        }
    }

    private ItemStack createDialogToggleButton(Player player) {
        String guiName = activeEditingGui.get(player.getUniqueId());
        boolean isDialog = false;
        if (guiName != null) {
            File guiFile = new File(guiDirectory, guiName + ".yml");
            if (guiFile.exists()) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
                isDialog = "DIALOG".equals(config.getString("gui_type", "CHEST"));
            }
        }
        return createActionButton(
                isDialog ? Material.OAK_SIGN : Material.GRAY_DYE,
                isDialog ? "§a§l[ Dialog Mode: ON ]" : "§7§l[ Dialog Mode: OFF ]",
                "§7Current: §f" + (isDialog ? "Dialog UI" : "Inventory GUI")
                        + "\n§eClick to switch this GUI between a\n§enative dialog and a normal inventory."
        );
    }

    private ItemStack createActionButton(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            List<Component> loreLines = new ArrayList<>();
            for (String line : lore.split("\n", -1)) {
                loreLines.add(Component.text(line));
            }
            meta.lore(loreLines);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void runConfiguredItemActions(Player player, int slot, FileConfiguration conf) {
        runConfiguredItemActionsFromSection(player, slot, "items", conf);
    }

    private void runConfiguredItemActionsFromSection(Player player, int slot, String sectionKey, FileConfiguration conf) {
        boolean hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        String base = sectionKey + "." + slot + ".";

        List<String> actionCmds = conf.getStringList(base + "action_commands");
        for (String cmd : actionCmds) {
            String finalCmd = cmd.replace("%player%", player.getName());
            if (hasPapi) finalCmd = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, finalCmd);
            player.performCommand(finalCmd);
        }

        String consoleCmd = conf.getString(base + "console_command");
        if (consoleCmd != null && !consoleCmd.isEmpty() && !consoleCmd.equalsIgnoreCase("None")) {
            String finalCmd = consoleCmd.replace("%player%", player.getName());
            if (hasPapi) finalCmd = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, finalCmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
        }

        String clickSound = conf.getString(base + "click_sound");
        if (clickSound != null && !clickSound.isEmpty() && !clickSound.equalsIgnoreCase("None")) {
            try {
                org.bukkit.NamespacedKey soundKey = org.bukkit.NamespacedKey.minecraft(clickSound.toLowerCase());
                org.bukkit.Sound sound = org.bukkit.Registry.SOUNDS.get(soundKey);
                if (sound != null) player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Exception ignored) {}
        }

        String broadcastMsg = conf.getString(base + "broadcast_message");
        if (broadcastMsg != null && !broadcastMsg.isEmpty() && !broadcastMsg.equalsIgnoreCase("None")) {
            String finalMsg = broadcastMsg.replace("%player%", player.getName());
            if (hasPapi) finalMsg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, finalMsg);
            Component broadcastComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(finalMsg);
            Bukkit.broadcast(broadcastComponent);
        }

        String actionBarMsg = conf.getString(base + "action_bar_message");
        if (actionBarMsg != null && !actionBarMsg.isEmpty() && !actionBarMsg.equalsIgnoreCase("None")) {
            String finalMsg = actionBarMsg.replace("%player%", player.getName());
            if (hasPapi) finalMsg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, finalMsg);
            int durationTicks = conf.getInt(base + "action_bar_duration", 3) * 20;
            Component barComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(finalMsg);
            player.sendActionBar(barComponent);
            final String fMsg = finalMsg;
            for (int t = 20; t < durationTicks; t += 20) {
                final Component bar = LegacyComponentSerializer.legacyAmpersand().deserialize(fMsg);
                Bukkit.getScheduler().runTaskLater(this, () -> player.sendActionBar(bar), t);
            }
        }

        int expAmount = conf.getInt(base + "exp_amount", 0);
        if (expAmount > 0) {
            player.giveExp(expAmount);
        }

        String bossbarText = conf.getString(base + "bossbar_text");
        if (bossbarText != null && !bossbarText.isEmpty() && !bossbarText.equalsIgnoreCase("None")) {
            String finalBossbarText = bossbarText.replace("%player%", player.getName());
            if (hasPapi) finalBossbarText = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, finalBossbarText);
            String bossbarColorStr = conf.getString(base + "bossbar_color", "PURPLE");
            int bossbarDuration = conf.getInt(base + "bossbar_duration", 5);
            BarColor barColor;
            try { barColor = BarColor.valueOf(bossbarColorStr); } catch (Exception e) { barColor = BarColor.PURPLE; }
            BossBar bar = Bukkit.createBossBar(
                    LegacyComponentSerializer.legacyAmpersand().serialize(
                            LegacyComponentSerializer.legacyAmpersand().deserialize(finalBossbarText)),
                    barColor, BarStyle.SOLID);
            bar.addPlayer(player);
            bar.setVisible(true);
            final BossBar finalBar = bar;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                finalBar.removePlayer(player);
                finalBar.setVisible(false);
            }, bossbarDuration * 20L);
        }

        boolean giveItem = conf.getBoolean(base + "give_item", false);
        if (giveItem) {
            ItemStack itemCopy = conf.getItemStack(base + "item");
            if (itemCopy != null) player.getInventory().addItem(itemCopy.clone());
        }
    }

    private void openItemPropertyMenu(Player player, int slot) {
        insidePropertiesMenu.add(player.getUniqueId());
        insideEnchantSelector.remove(player.getUniqueId());
        insideAttributeSelector.remove(player.getUniqueId());
        insideRemovalMenu.remove(player.getUniqueId());
        insideSoundMenu.remove(player.getUniqueId());
        insideCommandsMenu.remove(player.getUniqueId());
        insideBossbarMenu.remove(player.getUniqueId());
        clearTimeout(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());

        Inventory settingsMenu = Bukkit.createInventory(null, 36, Component.text("Configure Item Actions"));

        String guiName = activeEditingGui.get(player.getUniqueId());
        File guiFile = new File(guiDirectory, guiName + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

        boolean collectable = config.getBoolean("items." + slot + ".collectable", false);
        boolean giveItem = config.getBoolean("items." + slot + ".give_item", false);
        boolean closeOnClick = config.getBoolean("items." + slot + ".close_on_click", false);
        List<String> commands = config.getStringList("items." + slot + ".action_commands");
        String cmdDisplay = commands.isEmpty() ? "None" : commands.size() + " command(s)";
        String currentWarp = config.getString("items." + slot + ".warp_gui", "None");
        String itemPermission = config.getString("items." + slot + ".click_permission", "None");
        String currentSound = config.getString("items." + slot + ".click_sound", "None");
        String broadcastMsg = config.getString("items." + slot + ".broadcast_message", "None");
        String actionBarMsg = config.getString("items." + slot + ".action_bar_message", "None");
        int actionBarDuration = config.getInt("items." + slot + ".action_bar_duration", 3);
        int expAmount = config.getInt("items." + slot + ".exp_amount", 0);
        int customModelData = config.getInt("items." + slot + ".custom_model_data", 0);
        String consoleCmd = config.getString("items." + slot + ".console_command", "None");
        String bossbarText = config.getString("items." + slot + ".bossbar_text", "None");
        String bossbarColor = config.getString("items." + slot + ".bossbar_color", "PURPLE");
        int bossbarDuration = config.getInt("items." + slot + ".bossbar_duration", 5);

        settingsMenu.setItem(0, createActionButton(Material.NAME_TAG, "§d§l[ Change Item Name ]", "§7Modify the display title for this slot."));
        settingsMenu.setItem(1, createActionButton(Material.FEATHER, "§d§l[ Change Item Lore ]", "§7Modify description text array elements."));
        settingsMenu.setItem(2, createActionButton(Material.ENCHANTED_BOOK, "§5§l[ Change Enchantments ]", "§7Manage enchant rules directly on this slot."));
        settingsMenu.setItem(3, createActionButton(Material.NETHERITE_CHESTPLATE, "§c§l[ Item Attributes ]", "§7Modify stat modifiers like health, damage, armor."));
        settingsMenu.setItem(4, createActionButton(Material.PAINTING, "§b§l[ Custom Model Data ]", "§7Current: §f" + (customModelData == 0 ? "None" : customModelData) + "\n§7Set a custom model data integer."));
        settingsMenu.setItem(5, createActionButton(Material.CHEST, "§e§l[ Can Pick Up Item ]", "§7Current: §f" + collectable + "\n§bClick to switch setting."));
        settingsMenu.setItem(6, createActionButton(Material.LEVER, "§b§l[ Close On Click ]", "§7Current: §f" + closeOnClick + "\n§bClick to switch toggle state."));
        settingsMenu.setItem(7, createActionButton(Material.HOPPER, "§d§l[ Give Item Copy On Click ]", "§7Current: §f" + giveItem + "\n§bClick to switch setting."));
        settingsMenu.setItem(8, createActionButton(Material.NETHER_STAR, "§6§l[ Change Item ]", "§7Pick a new item in Creative.\n§7All other settings (name, lore,\n§7commands, etc.) are kept."));

        settingsMenu.setItem(9, createActionButton(Material.COMMAND_BLOCK, "§a§l[ Player Commands ]", "§7Commands: §f" + cmdDisplay + "\n§bManage multiple player commands."));
        settingsMenu.setItem(10, createActionButton(Material.REPEATING_COMMAND_BLOCK, "§c§l[ Console Command ]", "§7Current: §f" + consoleCmd + "\n§bRun a command as console on click."));
        settingsMenu.setItem(11, createActionButton(Material.OAK_DOOR, "§d§l[ Open Another Custom GUI ]", "§7Current: §f" + currentWarp + "\n§bClick to map target gui file name."));
        settingsMenu.setItem(12, createActionButton(Material.TRIPWIRE_HOOK, "§3§l[ Edit Item Click Permission ]", "§7Current: §f" + itemPermission + "\n§eClick to add permission requirement."));
        settingsMenu.setItem(13, createActionButton(Material.GOLD_INGOT, "§6§l[ Plugin Addons ]", "§7Configure economy/vault options."));

        settingsMenu.setItem(18, createActionButton(Material.NOTE_BLOCK, "§e§l[ Click Sound ]", "§7Current: §f" + currentSound + "\n§7Play a sound when this item is clicked."));
        settingsMenu.setItem(19, createActionButton(Material.PAPER, "§b§l[ Broadcast Message ]", "§7Current: §f" + broadcastMsg + "\n§7Send a message to all players on click."));
        settingsMenu.setItem(20, createActionButton(Material.EXPERIENCE_BOTTLE, "§a§l[ Action Bar Message ]", "§7Message: §f" + actionBarMsg + "\n§7Duration: §f" + actionBarDuration + "s\n§7Show action bar text on click."));
        settingsMenu.setItem(21, createActionButton(Material.EMERALD, "§2§l[ Add EXP On Click ]", "§7Current: §f" + (expAmount == 0 ? "None" : expAmount + " exp") + "\n§7Give experience points on click."));
        settingsMenu.setItem(22, createActionButton(Material.DRAGON_HEAD, "§5§l[ Bossbar On Click ]", "§7Text: §f" + bossbarText + "\n§7Color: §f" + bossbarColor + "\n§7Duration: §f" + bossbarDuration + "s\n§7Show a bossbar to the player on click."));

        settingsMenu.setItem(35, createActionButton(Material.ARROW, "§7§l← Go Back to Canvas", "§7Return back into active workspace."));

        navigatingSubMenu.add(player.getUniqueId());
        player.openInventory(settingsMenu);
        Bukkit.getScheduler().runTaskLater(this, () -> navigatingSubMenu.remove(player.getUniqueId()), 1L);
    }

    private void openSoundMenu(Player player, int slot) {
        insideSoundMenu.add(player.getUniqueId());
        insidePropertiesMenu.remove(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());

        Inventory menu = Bukkit.createInventory(null, 27, Component.text("Select Click Sound"));
        String[] sounds = {
                "ENTITY_VILLAGER_YES", "ENTITY_VILLAGER_NO", "BLOCK_NOTE_BLOCK_PLING",
                "BLOCK_NOTE_BLOCK_BELL", "UI_BUTTON_CLICK", "ENTITY_PLAYER_LEVELUP",
                "BLOCK_CHEST_OPEN", "BLOCK_CHEST_CLOSE", "ENTITY_EXPERIENCE_ORB_PICKUP",
                "ENTITY_ARROW_HIT_PLAYER", "BLOCK_ANVIL_USE", "ENTITY_GENERIC_EXPLODE",
                "ENTITY_ENDERMAN_TELEPORT", "BLOCK_PORTAL_TRAVEL", "ENTITY_BLAZE_SHOOT",
                "ENTITY_LIGHTNING_BOLT_THUNDER", "ENTITY_TRIDENT_THROW", "BLOCK_BELL_USE",
                "ENTITY_WITHER_SPAWN", "ENTITY_ELDER_GUARDIAN_CURSE", "BLOCK_BEACON_ACTIVATE",
                "ENTITY_FIREWORK_ROCKET_LAUNCH", "BLOCK_ENCHANTMENT_TABLE_USE", "ENTITY_ILLUSIONER_PREPARE_BLINDNESS",
                "MUSIC_DISC_PIGSTEP", "BLOCK_AMETHYST_CLUSTER_BREAK"
        };
        Material[] mats = {
                Material.EMERALD, Material.RED_STAINED_GLASS_PANE, Material.NOTE_BLOCK,
                Material.BELL, Material.STONE_BUTTON, Material.EXPERIENCE_BOTTLE,
                Material.CHEST, Material.TRAPPED_CHEST, Material.EXPERIENCE_BOTTLE,
                Material.ARROW, Material.ANVIL, Material.TNT,
                Material.ENDER_PEARL, Material.OBSIDIAN, Material.BLAZE_ROD,
                Material.LIGHTNING_ROD, Material.TRIDENT, Material.BELL,
                Material.WITHER_SKELETON_SKULL, Material.ELDER_GUARDIAN_SPAWN_EGG, Material.BEACON,
                Material.FIREWORK_ROCKET, Material.ENCHANTING_TABLE, Material.PHANTOM_MEMBRANE,
                Material.MUSIC_DISC_PIGSTEP, Material.AMETHYST_CLUSTER
        };

        for (int i = 0; i < Math.min(sounds.length, 26); i++) {
            String soundName = sounds[i];
            boolean valid = true;
            org.bukkit.NamespacedKey vKey = org.bukkit.NamespacedKey.minecraft(soundName.toLowerCase());
            if (org.bukkit.Registry.SOUNDS.get(vKey) == null) { valid = false; }
            String lore = valid
                    ? "§7Click to preview & select this sound.\n§aValid sound ✔"
                    : "§7Click to try to set this sound.\n§cMay not work on this server version!";
            ItemStack soundBtn = createActionButton(mats[i], "§e" + soundName, lore);
            ItemMeta btnMeta = soundBtn.getItemMeta();
            if (btnMeta != null) {
                btnMeta.getPersistentDataContainer().set(new NamespacedKey(this, "sound_name"), PersistentDataType.STRING, soundName);
                soundBtn.setItemMeta(btnMeta);
            }
            menu.setItem(i, soundBtn);
        }
        menu.setItem(26, createActionButton(Material.BARRIER, "§c[ Remove Sound ]", "§7Clear the click sound."));
        player.openInventory(menu);
        Bukkit.getScheduler().runTaskLater(this, () -> navigatingSubMenu.remove(player.getUniqueId()), 1L);
    }

    private void openBossbarMenu(Player player, int slot) {
        insideBossbarMenu.add(player.getUniqueId());
        insidePropertiesMenu.remove(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());

        String guiName = activeEditingGui.get(player.getUniqueId());
        File guiFile = new File(guiDirectory, guiName + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
        String currentText = config.getString("items." + slot + ".bossbar_text", "None");
        String currentColor = config.getString("items." + slot + ".bossbar_color", "PURPLE");
        int currentDuration = config.getInt("items." + slot + ".bossbar_duration", 5);

        Inventory menu = Bukkit.createInventory(null, 27, Component.text("Bossbar Settings"));

        menu.setItem(0, createActionButton(Material.NAME_TAG, "§d§l[ Set Bossbar Text ]", "§7Current: §f" + currentText + "\n§7Click to type the bossbar message."));

        menu.setItem(1, createActionButton(Material.CLOCK, "§e§l[ Set Duration ]", "§7Current: §f" + currentDuration + "s\n§7Click to set how long the bossbar shows."));

        String[] colorNames = {"PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE"};
        Material[] colorMats = {Material.PINK_CONCRETE, Material.BLUE_CONCRETE, Material.RED_CONCRETE,
                Material.GREEN_CONCRETE, Material.YELLOW_CONCRETE, Material.PURPLE_CONCRETE, Material.WHITE_CONCRETE};
        for (int i = 0; i < colorNames.length; i++) {
            String label = colorNames[i].equals(currentColor) ? "§f§l" + colorNames[i] + " §a✔" : "§7" + colorNames[i];
            ItemStack btn = createActionButton(colorMats[i], label, "§7Click to use this bossbar color.");
            ItemMeta m = btn.getItemMeta();
            if (m != null) {
                m.getPersistentDataContainer().set(new NamespacedKey(this, "bossbar_color_key"), PersistentDataType.STRING, colorNames[i]);
                btn.setItemMeta(m);
            }
            menu.setItem(9 + i, btn);
        }

        menu.setItem(17, createActionButton(Material.BARRIER, "§c§l[ Remove Bossbar ]", "§7Clear the bossbar from this item."));
        menu.setItem(26, createActionButton(Material.ARROW, "§7← Back", ""));

        player.openInventory(menu);
        Bukkit.getScheduler().runTaskLater(this, () -> navigatingSubMenu.remove(player.getUniqueId()), 1L);
    }

    private void openCommandsMenu(Player player, int slot) {
        insideCommandsMenu.add(player.getUniqueId());
        insidePropertiesMenu.remove(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());

        String guiName = activeEditingGui.get(player.getUniqueId());
        File guiFile = new File(guiDirectory, guiName + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
        List<String> commands = config.getStringList("items." + slot + ".action_commands");

        Inventory menu = Bukkit.createInventory(null, 54, Component.text("Manage Player Commands"));
        menu.setItem(0, createActionButton(Material.LIME_CONCRETE, "§a§l[ + Add Command ]", "§7Click to type a new command."));

        for (int i = 0; i < Math.min(commands.size(), 52); i++) {
            ItemStack btn = createActionButton(Material.COMMAND_BLOCK, "§f/" + commands.get(i), "§7Left-click: run\n§cRight-click: delete");
            ItemMeta m = btn.getItemMeta();
            if (m != null) {
                m.getPersistentDataContainer().set(new NamespacedKey(this, "cmd_index"), PersistentDataType.INTEGER, i);
                btn.setItemMeta(m);
            }
            menu.setItem(i + 1, btn);
        }
        menu.setItem(53, createActionButton(Material.ARROW, "§7← Back", ""));
        player.openInventory(menu);
        Bukkit.getScheduler().runTaskLater(this, () -> navigatingSubMenu.remove(player.getUniqueId()), 1L);
    }

    private void openBindCommandMenu(Player player) {
        insideBindCommandMenu.add(player.getUniqueId());
        insideCommandsMenu.remove(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());

        String guiName = activeEditingGui.get(player.getUniqueId());
        File guiFile = new File(guiDirectory, guiName + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

        List<String> boundCmds = config.getStringList("commands");
        String legacyCmd = config.getString("command", "");
        if (!legacyCmd.isEmpty() && !legacyCmd.equalsIgnoreCase("None") && !boundCmds.contains(legacyCmd)) {
            boundCmds = new ArrayList<>(boundCmds);
            boundCmds.add(0, legacyCmd);
        }

        Inventory menu = Bukkit.createInventory(null, 54, Component.text("Bind GUI Command"));
        menu.setItem(0, createActionButton(Material.LIME_CONCRETE, "§a§l[ + Add Command ]", "§7Click to type a new command trigger word."));

        for (int i = 0; i < Math.min(boundCmds.size(), 52); i++) {
            ItemStack btn = createActionButton(Material.COMMAND_BLOCK, "§f/" + boundCmds.get(i), "§7Bound command alias.\n§cRight-click: remove");
            ItemMeta m = btn.getItemMeta();
            if (m != null) {
                m.getPersistentDataContainer().set(new NamespacedKey(this, "bound_cmd_index"), PersistentDataType.INTEGER, i);
                btn.setItemMeta(m);
            }
            menu.setItem(i + 1, btn);
        }

        menu.setItem(53, createActionButton(Material.ARROW, "§7← Back", ""));
        player.openInventory(menu);
        Bukkit.getScheduler().runTaskLater(this, () -> navigatingSubMenu.remove(player.getUniqueId()), 1L);
    }

    private void openGuiTypeMenu(Player player) {
        insideGuiTypeMenu.add(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());

        String guiName = activeEditingGui.get(player.getUniqueId());
        File guiFile = new File(guiDirectory, guiName + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
        String currentType = config.getString("gui_type", "CHEST");

        Inventory menu = Bukkit.createInventory(null, 27, Component.text("Select GUI Type"));
        String[][] types = {
                {"CHEST", "§7§l[ Chest ]", "" + Material.CHEST},
                {"ANVIL", "§8§l[ Anvil ]", "" + Material.ANVIL},
                {"ENCHANTMENT", "§5§l[ Enchantment Table ]", "" + Material.ENCHANTING_TABLE},
                {"BARREL", "§6§l[ Barrel ]", "" + Material.BARREL},
                {"FURNACE", "§c§l[ Furnace ]", "" + Material.FURNACE},
                {"BLAST_FURNACE", "§c§l[ Blast Furnace ]", "" + Material.BLAST_FURNACE},
                {"SMOKER", "§e§l[ Smoker ]", "" + Material.SMOKER},
                {"STONECUTTER", "§7§l[ Stonecutter ]", "" + Material.STONECUTTER},
                {"CARTOGRAPHY", "§b§l[ Cartography Table ]", "" + Material.CARTOGRAPHY_TABLE},
                {"SMITHING", "§8§l[ Smithing Table ]", "" + Material.SMITHING_TABLE},
                {"LOOM", "§d§l[ Loom ]", "" + Material.LOOM},
                {"GRINDSTONE", "§a§l[ Grindstone ]", "" + Material.GRINDSTONE},
                {"BREWING", "§9§l[ Brewing Stand ]", "" + Material.BREWING_STAND}
        };

        for (int i = 0; i < types.length; i++) {
            Material mat;
            try { mat = Material.valueOf(types[i][2]); } catch (Exception e) { mat = Material.CHEST; }
            String label = types[i][0].equals(currentType) ? types[i][1] + " §a✔" : types[i][1];
            menu.setItem(i, createActionButton(mat, label, "§7Click to use this inventory type."));
            ItemStack btn = menu.getItem(i);
            if (btn != null) {
                ItemMeta m = btn.getItemMeta();
                if (m != null) {
                    m.getPersistentDataContainer().set(new NamespacedKey(this, "gui_type_key"), PersistentDataType.STRING, types[i][0]);
                    btn.setItemMeta(m);
                    menu.setItem(i, btn);
                }
            }
        }
        String dialogLabel = "DIALOG".equals(currentType)
                ? "§a§l[ Dialog UI (Minecraft Dialog) ] §a✔"
                : "§a§l[ Dialog UI (Minecraft Dialog) ]";
        ItemStack dialogBtn = createActionButton(Material.WRITABLE_BOOK, dialogLabel,
                "§7Opens a native Minecraft dialog window\n§7with configurable action buttons.\n§eRequires Paper 1.21.6+");
        ItemMeta dialogMeta = dialogBtn.getItemMeta();
        if (dialogMeta != null) {
            dialogMeta.getPersistentDataContainer().set(new NamespacedKey(this, "gui_type_key"), PersistentDataType.STRING, "DIALOG");
            dialogBtn.setItemMeta(dialogMeta);
        }
        menu.setItem(13, dialogBtn);

        menu.setItem(26, createActionButton(Material.ARROW, "§7← Back", ""));
        player.openInventory(menu);
        Bukkit.getScheduler().runTaskLater(this, () -> navigatingSubMenu.remove(player.getUniqueId()), 1L);
    }

    private void openDialogMenu(Player player) {
        insideDialogMenu.add(player.getUniqueId());
        insideGuiTypeMenu.remove(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());

        String guiName = activeEditingGui.get(player.getUniqueId());
        File guiFile = new File(guiDirectory, guiName + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

        String dialogTitle   = config.getString("dialog.title",   "None");
        String dialogMessage = config.getString("dialog.message", "None");
        int columns = config.getInt("dialog.columns", 2);

        Inventory menu = Bukkit.createInventory(null, 9, Component.text("Dialog UI Settings"));

        menu.setItem(0, createActionButton(Material.NAME_TAG,
                "§d§l[ Set Dialog Title ]",
                "§7Current: §f" + dialogTitle + "\n§7Shown as the dialog header."));
        menu.setItem(1, createActionButton(Material.PAPER,
                "§b§l[ Set Dialog Message ]",
                "§7Current: §f" + dialogMessage + "\n§7Body text shown in the dialog."));
        menu.setItem(2, createActionButton(Material.COMPARATOR,
                "§6§l[ Buttons Per Row: " + columns + " ]",
                "§7How many buttons appear side by side\n§7before wrapping to the next row.\n§7§f1 §7= stacked (top to bottom)\n§7§f2 §7= two columns\n§7§f5+ §7= buttons auto-shrink to fit\n\n§7Button width is calculated automatically\n§7so they always fit on screen.\n§eClick to type a new number."));
        menu.setItem(4, createActionButton(Material.BOOK,
                "§e§l[ Dialog Buttons ]",
                "§7Dialog buttons come from the canvas.\n§7Each item you place = one button.\n§7Use §f§lClose On Click§7 in item properties\n§7to choose if clicking closes or keeps\n§7the dialog open."));

        menu.setItem(8, createActionButton(Material.ARROW, "§7← Back", "§7Return to workspace."));
        player.openInventory(menu);
        Bukkit.getScheduler().runTaskLater(this, () -> navigatingSubMenu.remove(player.getUniqueId()), 1L);
    }

    private void openEnchantmentSelector(Player player) {
        insideEnchantSelector.add(player.getUniqueId());
        insidePropertiesMenu.remove(player.getUniqueId());
        insideAttributeSelector.remove(player.getUniqueId());
        insideRemovalMenu.remove(player.getUniqueId());
        clearTimeout(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());
        Inventory enchantMenu = Bukkit.createInventory(null, 54, Component.text("Select an Enchantment"));

        enchantMenu.setItem(0, createActionButton(Material.TNT, "§c§l[ Remove Enchantment ]", "§7Open list to selectively strip enchants off this item."));

        int slot = 1;
        for (Enchantment enchant : Registry.ENCHANTMENT) {
            if (slot >= 54) break;
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = book.getItemMeta();
            if (meta != null) {
                String friendlyName = enchant.getKey().getKey().replace("_", " ").toUpperCase();
                meta.displayName(Component.text("§b" + friendlyName));
                meta.lore(List.of(Component.text("§7Click to select this enchant rule.")));
                meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchant.getKey().toString());
                book.setItemMeta(meta);
            }
            enchantMenu.setItem(slot, book);
            slot++;
        }
        player.openInventory(enchantMenu);
    }

    private void openEnchantmentRemovalMenu(Player player, ItemStack item) {
        insideRemovalMenu.add(player.getUniqueId());
        insideEnchantSelector.remove(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());
        Inventory removeMenu = Bukkit.createInventory(null, 27, Component.text("Strip Item Enchantments"));

        int slot = 0;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasEnchants()) {
            for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                if (slot >= 27) break;
                Enchantment enchant = entry.getKey();
                int level = entry.getValue();

                ItemStack activeBook = new ItemStack(Material.BARRIER);
                ItemMeta bookMeta = activeBook.getItemMeta();
                if (bookMeta != null) {
                    String cleanName = enchant.getKey().getKey().replace("_", " ").toUpperCase();
                    bookMeta.displayName(Component.text("§cRemove: " + cleanName + " (" + level + ")"));
                    bookMeta.lore(List.of(Component.text("§7Click to permanently strip this enchantment rule.")));
                    bookMeta.getPersistentDataContainer().set(removeEnchantKey, PersistentDataType.STRING, enchant.getKey().toString());
                    activeBook.setItemMeta(bookMeta);
                }
                removeMenu.setItem(slot, activeBook);
                slot++;
            }
        }

        if (slot == 0) {
            removeMenu.setItem(13, createActionButton(Material.PAPER, "§7No Enchantments Found", "§7This item possesses no active enchant rules."));
        }
        player.openInventory(removeMenu);
    }

    private void openAttributeSelector(Player player) {
        insideAttributeSelector.add(player.getUniqueId());
        insidePropertiesMenu.remove(player.getUniqueId());
        insideEnchantSelector.remove(player.getUniqueId());
        insideRemovalMenu.remove(player.getUniqueId());
        clearTimeout(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());
        Inventory attrMenu = Bukkit.createInventory(null, 36, Component.text("Select an Attribute"));

        attrMenu.setItem(0, createActionButton(Material.TNT, "§c§l[ Remove Attribute ]", "§7Open list to selectively strip attributes off this item."));

        int slot = 1;
        for (Attribute attr : Registry.ATTRIBUTE) {
            if (slot >= 36) break;
            ItemStack icon = new ItemStack(Material.BOOK);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                String friendlyName = attr.getKey().getKey().replace("_", " ").toUpperCase();
                meta.displayName(Component.text("§c" + friendlyName));
                meta.lore(List.of(Component.text("§7Click to modify this attribute value.")));
                meta.getPersistentDataContainer().set(attributeKey, PersistentDataType.STRING, attr.getKey().toString());
                icon.setItemMeta(meta);
            }
            attrMenu.setItem(slot, icon);
            slot++;
        }
        player.openInventory(attrMenu);
    }

    private void openAttributeRemovalMenu(Player player, ItemStack item) {
        insideRemovalMenu.add(player.getUniqueId());
        insideAttributeSelector.remove(player.getUniqueId());
        navigatingSubMenu.add(player.getUniqueId());
        Inventory removeMenu = Bukkit.createInventory(null, 27, Component.text("Strip Item Attributes"));

        int slot = 0;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasAttributeModifiers()) {
            var modifiers = meta.getAttributeModifiers();
            if (modifiers != null) {
                for (Attribute attr : modifiers.keySet()) {
                    if (slot >= 27) break;
                    Collection<AttributeModifier> mods = modifiers.get(attr);
                    if (mods != null) {
                        for (AttributeModifier modifier : mods) {
                            ItemStack icon = new ItemStack(Material.BARRIER);
                            ItemMeta iconMeta = icon.getItemMeta();
                            if (iconMeta != null) {
                                String cleanName = attr.getKey().getKey().replace("_", " ").toUpperCase();
                                iconMeta.displayName(Component.text("§cRemove: " + cleanName + " (" + modifier.getAmount() + ")"));
                                iconMeta.lore(List.of(Component.text("§7Click to permanently drop this statutory modifier.")));
                                iconMeta.getPersistentDataContainer().set(removeAttributeKey, PersistentDataType.STRING, attr.getKey().toString() + ":::" + modifier.getKey().toString());
                                icon.setItemMeta(iconMeta);
                            }
                            removeMenu.setItem(slot, icon);
                            slot++;
                        }
                    }
                }
            }
        }

        if (slot == 0) {
            removeMenu.setItem(13, createActionButton(Material.PAPER, "§7No Attributes Found", "§7This item possesses no active stat modifiers."));
        }
        player.openInventory(removeMenu);
    }

    private void startInactivityTimeout(Player player, int fallbackState) {
        clearTimeout(player.getUniqueId());
        UUID uuid = player.getUniqueId();

        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, (Runnable) () -> {
            editState.remove(uuid);
            selectedEnchantment.remove(uuid);
            selectedAttribute.remove(uuid);
            itemBeingEdited.remove(uuid);
            timeoutTasks.remove(uuid);

            player.sendMessage(Component.text("Error: Action timed out due to 20 seconds of inactivity.", NamedTextColor.RED));

            Integer lastSlot = targetEditSlot.get(uuid);
            if ((fallbackState == 8 || fallbackState == 9 || fallbackState == 10 || fallbackState == 6 || fallbackState == 11 || fallbackState == 2 || fallbackState == 3 || fallbackState == 12
                    || fallbackState == 14 || fallbackState == 16 || fallbackState == 17 || fallbackState == 18 || fallbackState == 19 || fallbackState == 20 || fallbackState == 21 || fallbackState == 22 || fallbackState == 23) && lastSlot != null) {
                openItemPropertyMenu(player, lastSlot);
            } else if (fallbackState == 13 && lastSlot != null) {
                openVaultMenu(player, lastSlot);
            } else if (fallbackState == 30 || fallbackState == 31 || fallbackState == 32 || fallbackState == 33) {
                openDialogMenu(player);
            } else {
                targetEditSlot.remove(uuid);
                openWorkspace(player);
            }
        }, 400L);

        timeoutTasks.put(uuid, task);
    }

    private void clearTimeout(UUID uuid) {
        if (timeoutTasks.containsKey(uuid)) {
            timeoutTasks.remove(uuid).cancel();
        }
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title()).trim();

        if (!activeEditingGui.containsKey(player.getUniqueId())) {
            String openGui = currentOpenGuiName.get(player.getUniqueId());
            if (openGui != null) {
                if (event.getClickedInventory() != null && event.getClickedInventory() == player.getOpenInventory().getTopInventory()) {
                    event.setCancelled(true);

                    File file = new File(guiDirectory, openGui + ".yml");
                    if (!file.exists()) return;

                    FileConfiguration conf = YamlConfiguration.loadConfiguration(file);
                    int slot = event.getSlot();

                    if (!conf.contains("items." + slot)) return;

                    String requiredPerm = conf.getString("items." + slot + ".click_permission", "None");
                    if (requiredPerm != null && !requiredPerm.isEmpty() && !requiredPerm.equalsIgnoreCase("None")) {
                        if (!player.hasPermission(requiredPerm)) {
                            player.sendMessage(Component.text("You don't have permission to interact with this item!", NamedTextColor.RED));
                            return;
                        }
                    }

                    double clickCost = conf.getDouble("items." + slot + ".click_cost", 0.0);
                    boolean deductCost = conf.getBoolean("items." + slot + ".deduct_cost", true);
                    if (clickCost > 0.0 && deductCost) {
                        if (econ == null) {
                            player.sendMessage(Component.text("Economy is not available!", NamedTextColor.RED));
                            return;
                        }
                        if (!econ.has(player, clickCost)) {
                            player.sendMessage(Component.text("You need $" + clickCost + " to use this!", NamedTextColor.RED));
                            return;
                        }
                        econ.withdrawPlayer(player, clickCost);
                        player.sendMessage(Component.text("$" + clickCost + " has been deducted from your balance.", NamedTextColor.YELLOW));
                    } else if (clickCost > 0.0) {
                        if (econ == null) {
                            player.sendMessage(Component.text("Economy is not available!", NamedTextColor.RED));
                            return;
                        }
                        if (!econ.has(player, clickCost)) {
                            player.sendMessage(Component.text("You need $" + clickCost + " to use this!", NamedTextColor.RED));
                            return;
                        }
                    }

                    boolean collectable = conf.getBoolean("items." + slot + ".collectable", false);
                    if (collectable) {
                        event.setCancelled(false);
                    }

                    runConfiguredItemActions(player, slot, conf);

                    boolean closeOnClick = conf.getBoolean("items." + slot + ".close_on_click", false);
                    if (closeOnClick) {
                        Bukkit.getScheduler().runTask(this, (Runnable) player::closeInventory);
                        return;
                    }

                    String warpGui = conf.getString("items." + slot + ".warp_gui");
                    if (warpGui != null && !warpGui.isEmpty() && !warpGui.equalsIgnoreCase("None")) {
                        String cleanTarget = warpGui.toLowerCase().trim();

                        playerGuiHistory.computeIfAbsent(player.getUniqueId(), k -> new Stack<>()).push(openGui);
                        processingWarpShift.add(player.getUniqueId());

                        Bukkit.getScheduler().runTask(this, (Runnable) () -> {
                            openSavedGuiForPlayer(player, cleanTarget);
                            processingWarpShift.remove(player.getUniqueId());
                        });
                    }
                }
            }
            return;
        }

        if (insidePropertiesMenu.contains(player.getUniqueId()) || insideEnchantSelector.contains(player.getUniqueId()) ||
                insideAttributeSelector.contains(player.getUniqueId()) || insideRemovalMenu.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }

        if (title.equalsIgnoreCase("Strip Item Enchantments")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.BARRIER) return;

            ItemMeta clickMeta = clicked.getItemMeta();
            if (clickMeta == null) return;
            String keyStr = clickMeta.getPersistentDataContainer().get(removeEnchantKey, PersistentDataType.STRING);
            if (keyStr == null) return;

            NamespacedKey parsedKey = NamespacedKey.fromString(keyStr);
            Enchantment enchant = parsedKey != null ? Registry.ENCHANTMENT.get(parsedKey) : null;
            Integer configSlot = targetEditSlot.get(player.getUniqueId());
            ItemStack targetItem = itemBeingEdited.get(player.getUniqueId());

            if (configSlot != null && targetItem != null && enchant != null) {
                ItemMeta meta = targetItem.getItemMeta();
                if (meta != null) {
                    meta.removeEnchant(enchant);
                    targetItem.setItemMeta(meta);

                    String guiName = activeEditingGui.get(player.getUniqueId());
                    File guiFile = new File(guiDirectory, guiName + ".yml");
                    FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
                    config.set("items." + configSlot + ".item", targetItem);
                    try {
                        config.save(guiFile);
                    } catch (IOException ignored) {
                    }

                    player.sendMessage(Component.text("Successfully dropped enchantment modifier rules.", NamedTextColor.GREEN));
                }
            }
            openItemPropertyMenu(player, configSlot != null ? configSlot : 0);
            return;
        }

        if (title.equalsIgnoreCase("Strip Item Attributes")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.BARRIER) return;

            ItemMeta clickMeta = clicked.getItemMeta();
            if (clickMeta == null) return;
            String containerValue = clickMeta.getPersistentDataContainer().get(removeAttributeKey, PersistentDataType.STRING);
            if (containerValue == null || !containerValue.contains(":::")) return;

            String[] split = containerValue.split(":::");
            String attrKeyStr = split[0];
            String modifierKeyStr = split[1];

            Integer configSlot = targetEditSlot.get(player.getUniqueId());
            ItemStack targetItem = itemBeingEdited.get(player.getUniqueId());

            if (configSlot != null && targetItem != null) {
                ItemMeta meta = targetItem.getItemMeta();
                if (meta != null && meta.hasAttributeModifiers()) {
                    try {
                        NamespacedKey attrKey = NamespacedKey.fromString(attrKeyStr);
                        NamespacedKey modKey = NamespacedKey.fromString(modifierKeyStr);
                        Attribute attr = attrKey != null ? Registry.ATTRIBUTE.get(attrKey) : null;

                        if (attr != null && modKey != null) {
                            Collection<AttributeModifier> currentModifiers = meta.getAttributeModifiers(attr);
                            if (currentModifiers != null) {
                                meta.removeAttributeModifier(attr);
                                for (AttributeModifier modifier : currentModifiers) {
                                    if (!modifier.getKey().equals(modKey)) {
                                        meta.addAttributeModifier(attr, modifier);
                                    }
                                }
                            }
                            targetItem.setItemMeta(meta);

                            String guiName = activeEditingGui.get(player.getUniqueId());
                            File guiFile = new File(guiDirectory, guiName + ".yml");
                            FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
                            config.set("items." + configSlot + ".item", targetItem);
                            try {
                                config.save(guiFile);
                            } catch (IOException ignored) {
                            }

                            player.sendMessage(Component.text("Successfully dropped structural attribute stat modifier.", NamedTextColor.GREEN));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            openItemPropertyMenu(player, configSlot != null ? configSlot : 0);
            return;
        }

        if (title.equalsIgnoreCase("Configure Item Actions")) {
            event.setCancelled(true);
            int configSlot = targetEditSlot.getOrDefault(player.getUniqueId(), -1);
            if (configSlot == -1) return;

            String guiName = activeEditingGui.get(player.getUniqueId());
            File guiFile = new File(guiDirectory, guiName + ".yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

            int slotClicked = event.getRawSlot();

            if (slotClicked == 0) {
                ItemStack item = config.getItemStack("items." + configSlot + ".item");
                if (item != null) {
                    itemBeingEdited.put(player.getUniqueId(), item.clone());
                    editState.put(player.getUniqueId(), 2);
                    navigatingSubMenu.add(player.getUniqueId());
                    insidePropertiesMenu.remove(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage(Component.text("Type the new Display Name in chat (or type 'cancel'):", NamedTextColor.LIGHT_PURPLE));
                    startInactivityTimeout(player, 2);
                }
            } else if (slotClicked == 1) {
                ItemStack item = config.getItemStack("items." + configSlot + ".item");
                if (item != null) {
                    itemBeingEdited.put(player.getUniqueId(), item.clone());
                    editState.put(player.getUniqueId(), 3);
                    navigatingSubMenu.add(player.getUniqueId());
                    insidePropertiesMenu.remove(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage(Component.text("Type the new Lore line in chat (or type 'cancel'):", NamedTextColor.LIGHT_PURPLE));
                    startInactivityTimeout(player, 3);
                }
            } else if (slotClicked == 2) {
                ItemStack item = config.getItemStack("items." + configSlot + ".item");
                if (item != null) {
                    itemBeingEdited.put(player.getUniqueId(), item.clone());
                    openEnchantmentSelector(player);
                }
            } else if (slotClicked == 3) {
                ItemStack item = config.getItemStack("items." + configSlot + ".item");
                if (item != null) {
                    itemBeingEdited.put(player.getUniqueId(), item.clone());
                    openAttributeSelector(player);
                }
            } else if (slotClicked == 4) {
                editState.put(player.getUniqueId(), 20);
                navigatingSubMenu.add(player.getUniqueId());
                insidePropertiesMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the Custom Model Data integer (or 'cancel'):", NamedTextColor.AQUA));
                startInactivityTimeout(player, 20);
            } else if (slotClicked == 5) {
                boolean collectable = config.getBoolean("items." + configSlot + ".collectable", false);
                config.set("items." + configSlot + ".collectable", !collectable);
                try { config.save(guiFile); } catch (IOException ignored) {}
                openItemPropertyMenu(player, configSlot);
            } else if (slotClicked == 6) {
                boolean closeOnClick = config.getBoolean("items." + configSlot + ".close_on_click", false);
                config.set("items." + configSlot + ".close_on_click", !closeOnClick);
                try { config.save(guiFile); } catch (IOException ignored) {}
                openItemPropertyMenu(player, configSlot);
            } else if (slotClicked == 7) {
                boolean giveItem = config.getBoolean("items." + configSlot + ".give_item", false);
                config.set("items." + configSlot + ".give_item", !giveItem);
                try { config.save(guiFile); } catch (IOException ignored) {}
                openItemPropertyMenu(player, configSlot);
            } else if (slotClicked == 8) {
                previousGameMode.put(player.getUniqueId(), player.getGameMode());
                editState.put(player.getUniqueId(), 37);
                insidePropertiesMenu.remove(player.getUniqueId());
                navigatingSubMenu.add(player.getUniqueId());
                player.closeInventory();
                player.setGameMode(org.bukkit.GameMode.CREATIVE);
                player.sendMessage(Component.text("§6[Change Item] §ePick any item from the Creative menu — pick it up and it will replace the button's item. All other settings are kept.", NamedTextColor.YELLOW));
            } else if (slotClicked == 9) {
                openCommandsMenu(player, configSlot);
            } else if (slotClicked == 10) {
                editState.put(player.getUniqueId(), 14);
                navigatingSubMenu.add(player.getUniqueId());
                insidePropertiesMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the console command (without /) (or 'cancel'):", NamedTextColor.RED));
                startInactivityTimeout(player, 14);
            } else if (slotClicked == 11) {
                editState.put(player.getUniqueId(), 9);
                navigatingSubMenu.add(player.getUniqueId());
                insidePropertiesMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the GUI name to warp to (or type 'cancel'):", NamedTextColor.YELLOW));
                startInactivityTimeout(player, 9);
            } else if (slotClicked == 12) {
                editState.put(player.getUniqueId(), 12);
                navigatingSubMenu.add(player.getUniqueId());
                insidePropertiesMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the custom permission node required to click this item in chat (or type 'cancel'):", NamedTextColor.AQUA));
                startInactivityTimeout(player, 12);
            } else if (slotClicked == 13) {
                openAddonsMenu(player, configSlot);
            } else if (slotClicked == 18) {
                openSoundMenu(player, configSlot);
            } else if (slotClicked == 19) {
                editState.put(player.getUniqueId(), 16);
                navigatingSubMenu.add(player.getUniqueId());
                insidePropertiesMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the broadcast message (supports &colors, %player%) (or 'cancel'):", NamedTextColor.YELLOW));
                startInactivityTimeout(player, 16);
            } else if (slotClicked == 20) {
                editState.put(player.getUniqueId(), 17);
                navigatingSubMenu.add(player.getUniqueId());
                insidePropertiesMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the action bar message (supports &colors, %player%) (or 'cancel'):", NamedTextColor.AQUA));
                startInactivityTimeout(player, 17);
            } else if (slotClicked == 21) {
                editState.put(player.getUniqueId(), 19);
                navigatingSubMenu.add(player.getUniqueId());
                insidePropertiesMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the EXP amount to give on click (or 'cancel'):", NamedTextColor.GREEN));
                startInactivityTimeout(player, 19);
            } else if (slotClicked == 22) {
                openBossbarMenu(player, configSlot);
            } else if (slotClicked == 35) {
                player.closeInventory();
                Bukkit.getScheduler().runTask(this, () -> openWorkspace(player));
            }
            return;
        }

        if (title.equalsIgnoreCase("Bossbar Settings")) {
            event.setCancelled(true);
            int configSlot = targetEditSlot.getOrDefault(player.getUniqueId(), -1);
            if (configSlot == -1) return;
            String guiName = activeEditingGui.get(player.getUniqueId());
            if (guiName == null) return;
            File guiFile = new File(guiDirectory, guiName + ".yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

            int rawSlot = event.getRawSlot();
            if (rawSlot == 0) {
                editState.put(player.getUniqueId(), 22);
                navigatingSubMenu.add(player.getUniqueId());
                insideBossbarMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the bossbar message (supports &colors, %player%) (or 'cancel'):", NamedTextColor.LIGHT_PURPLE));
                startInactivityTimeout(player, 22);
            } else if (rawSlot == 1) {
                editState.put(player.getUniqueId(), 23);
                navigatingSubMenu.add(player.getUniqueId());
                insideBossbarMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the bossbar duration in seconds (or 'cancel'):", NamedTextColor.YELLOW));
                startInactivityTimeout(player, 23);
            } else if (rawSlot >= 9 && rawSlot <= 15) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType() == Material.AIR) return;
                ItemMeta m = clicked.getItemMeta();
                if (m == null) return;
                String colorKey = m.getPersistentDataContainer().get(new NamespacedKey(this, "bossbar_color_key"), PersistentDataType.STRING);
                if (colorKey != null) {
                    config.set("items." + configSlot + ".bossbar_color", colorKey);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("Bossbar color set to: " + colorKey, NamedTextColor.GREEN));
                    openBossbarMenu(player, configSlot);
                }
            } else if (rawSlot == 17) {
                config.set("items." + configSlot + ".bossbar_text", null);
                config.set("items." + configSlot + ".bossbar_color", null);
                config.set("items." + configSlot + ".bossbar_duration", null);
                try { config.save(guiFile); } catch (IOException ignored) {}
                player.sendMessage(Component.text("Bossbar removed.", NamedTextColor.RED));
                openItemPropertyMenu(player, configSlot);
            } else if (rawSlot == 26) {
                openItemPropertyMenu(player, configSlot);
            }
            return;
        }

        if (title.equalsIgnoreCase("Select Click Sound")) {
            event.setCancelled(true);
            int configSlot = targetEditSlot.getOrDefault(player.getUniqueId(), -1);
            if (configSlot == -1) return;
            String guiName = activeEditingGui.get(player.getUniqueId());
            if (guiName == null) return;
            File guiFile = new File(guiDirectory, guiName + ".yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            ItemMeta m = clicked.getItemMeta();
            if (m == null) return;

            if (clicked.getType() == Material.BARRIER) {
                config.set("items." + configSlot + ".click_sound", null);
                try { config.save(guiFile); } catch (IOException ignored) {}
                player.sendMessage(Component.text("Click sound removed.", NamedTextColor.RED));
                openItemPropertyMenu(player, configSlot);
            } else {
                String soundName = m.getPersistentDataContainer().get(new NamespacedKey(this, "sound_name"), PersistentDataType.STRING);
                if (soundName == null) {
                    soundName = PlainTextComponentSerializer.plainText()
                            .serialize(m.displayName() != null ? m.displayName() : Component.empty()).trim();
                }
                try {
                    org.bukkit.NamespacedKey previewKey = org.bukkit.NamespacedKey.minecraft(soundName.toLowerCase());
                    org.bukkit.Sound previewSound = org.bukkit.Registry.SOUNDS.get(previewKey);
                    if (previewSound != null) {
                        player.playSound(player.getLocation(), previewSound, 1.0f, 1.0f);
                    } else {
                        player.sendMessage(Component.text("Warning: Sound '" + soundName + "' could not be previewed (invalid on this server version).", NamedTextColor.YELLOW));
                    }
                } catch (Exception ignored) {
                    player.sendMessage(Component.text("Warning: Sound '" + soundName + "' could not be previewed (invalid on this server version).", NamedTextColor.YELLOW));
                }
                config.set("items." + configSlot + ".click_sound", soundName);
                try { config.save(guiFile); } catch (IOException ignored) {}
                player.sendMessage(Component.text("Click sound set to: §e" + soundName, NamedTextColor.GREEN));
                openItemPropertyMenu(player, configSlot);
            }
            return;
        }

        if (title.equalsIgnoreCase("Manage Player Commands")) {
            event.setCancelled(true);
            int configSlot = targetEditSlot.getOrDefault(player.getUniqueId(), -1);
            if (configSlot == -1) return;
            String guiName = activeEditingGui.get(player.getUniqueId());
            if (guiName == null) return;
            File guiFile = new File(guiDirectory, guiName + ".yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
            List<String> commands = config.getStringList("items." + configSlot + ".action_commands");

            int rawSlot = event.getRawSlot();
            if (rawSlot == 0) {
                editState.put(player.getUniqueId(), 21);
                navigatingSubMenu.add(player.getUniqueId());
                insideCommandsMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the command to add (without /) (or 'cancel'):", NamedTextColor.YELLOW));
                startInactivityTimeout(player, 21);
            } else if (rawSlot == 53) {
                openItemPropertyMenu(player, configSlot);
            } else if (rawSlot >= 1 && rawSlot <= 52) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType() == Material.AIR) return;
                if (event.getClick() == ClickType.RIGHT) {
                    ItemMeta m = clicked.getItemMeta();
                    if (m == null) return;
                    Integer idx = m.getPersistentDataContainer().get(new NamespacedKey(this, "cmd_index"), PersistentDataType.INTEGER);
                    if (idx != null && idx >= 0 && idx < commands.size()) {
                        commands.remove((int) idx);
                        config.set("items." + configSlot + ".action_commands", commands);
                        try { config.save(guiFile); } catch (IOException ignored) {}
                        player.sendMessage(Component.text("Command removed.", NamedTextColor.RED));
                        openCommandsMenu(player, configSlot);
                    }
                }
            }
            return;
        }

        if (title.equalsIgnoreCase("Bind GUI Command")) {
            event.setCancelled(true);
            String guiName = activeEditingGui.get(player.getUniqueId());
            if (guiName == null) return;
            File guiFile = new File(guiDirectory, guiName + ".yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

            List<String> boundCmds = new ArrayList<>(config.getStringList("commands"));
            String legacyCmd = config.getString("command", "");
            if (!legacyCmd.isEmpty() && !legacyCmd.equalsIgnoreCase("None") && !boundCmds.contains(legacyCmd)) {
                boundCmds.add(0, legacyCmd);
                config.set("command", null);
                config.set("commands", boundCmds);
                try { config.save(guiFile); } catch (IOException ignored) {}
            }

            int rawSlot = event.getRawSlot();
            if (rawSlot == 0) {
                editState.put(player.getUniqueId(), 1);
                navigatingSubMenu.add(player.getUniqueId());
                insideBindCommandMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type your command trigger word in chat (or type 'cancel'):", NamedTextColor.YELLOW));
                startInactivityTimeout(player, 1);
            } else if (rawSlot == 53) {
                insideBindCommandMenu.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(this, () -> openWorkspace(player));
            } else if (rawSlot >= 1 && rawSlot <= 52) {
                if (event.getClick() == ClickType.RIGHT) {
                    ItemStack clicked = event.getCurrentItem();
                    if (clicked == null || clicked.getType() == Material.AIR) return;
                    ItemMeta m = clicked.getItemMeta();
                    if (m == null) return;
                    Integer idx = m.getPersistentDataContainer().get(new NamespacedKey(this, "bound_cmd_index"), PersistentDataType.INTEGER);
                    if (idx != null && idx >= 0 && idx < boundCmds.size()) {
                        String removed = boundCmds.remove((int) idx);
                        config.set("commands", boundCmds);
                        if (!boundCmds.isEmpty()) {
                            config.set("command", boundCmds.get(0));
                        } else {
                            config.set("command", null);
                        }
                        try { config.save(guiFile); } catch (IOException ignored) {}
                        player.sendMessage(Component.text("Removed command: /" + removed, NamedTextColor.RED));
                        openBindCommandMenu(player);
                    }
                }
            }
            return;
        }

        if (title.equalsIgnoreCase("Select GUI Type")) {
            event.setCancelled(true);
            String guiName = activeEditingGui.get(player.getUniqueId());
            if (guiName == null) return;
            File guiFile = new File(guiDirectory, guiName + ".yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

            int rawSlot = event.getRawSlot();
            if (rawSlot == 26) {
                insideGuiTypeMenu.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(this, () -> openWorkspace(player));
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            ItemMeta m = clicked.getItemMeta();
            if (m == null) return;
            String typeKey = m.getPersistentDataContainer().get(new NamespacedKey(this, "gui_type_key"), PersistentDataType.STRING);
            if (typeKey != null) {
                config.set("gui_type", typeKey);
                try { config.save(guiFile); } catch (IOException ignored) {}
                if ("DIALOG".equals(typeKey)) {
                    player.sendMessage(Component.text("GUI type set to: Dialog UI", NamedTextColor.GREEN));
                    insideGuiTypeMenu.remove(player.getUniqueId());
                    Bukkit.getScheduler().runTask(this, () -> openDialogMenu(player));
                } else {
                    player.sendMessage(Component.text("GUI type set to: " + typeKey, NamedTextColor.GREEN));
                    insideGuiTypeMenu.remove(player.getUniqueId());
                    Bukkit.getScheduler().runTask(this, () -> openWorkspace(player));
                }
            }
            return;
        }

        if (title.equalsIgnoreCase("Dialog UI Settings")) {
            event.setCancelled(true);
            String guiName = activeEditingGui.get(player.getUniqueId());
            if (guiName == null) return;
            int rawSlot = event.getRawSlot();

            if (rawSlot == 8) {
                insideDialogMenu.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(this, () -> openWorkspace(player));
                return;
            }
            if (rawSlot == 0) {
                editState.put(player.getUniqueId(), 30);
                navigatingSubMenu.add(player.getUniqueId());
                insideDialogMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the dialog title (supports &colours) (or 'cancel'):", NamedTextColor.LIGHT_PURPLE));
                startInactivityTimeout(player, 30);
                return;
            }
            if (rawSlot == 1) {
                editState.put(player.getUniqueId(), 31);
                navigatingSubMenu.add(player.getUniqueId());
                insideDialogMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the dialog body message (or 'cancel'):", NamedTextColor.AQUA));
                startInactivityTimeout(player, 31);
                return;
            }
            if (rawSlot == 2) {
                editState.put(player.getUniqueId(), 34);
                navigatingSubMenu.add(player.getUniqueId());
                insideDialogMenu.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type how many buttons should appear per row (e.g. 1 = stacked, 2 = two columns, 5 = five across) (or 'cancel'):", NamedTextColor.GOLD));
                startInactivityTimeout(player, 34);
                return;
            }
            return;
        }

        if (title.equalsIgnoreCase("Plugin Addons")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 0) {
                openVaultMenu(player, targetEditSlot.get(player.getUniqueId()));
            }
            if (event.getRawSlot() == 8) {
                openItemPropertyMenu(player, targetEditSlot.get(player.getUniqueId()));
            }
            return;
        }

        if (title.equalsIgnoreCase("Vault Cost Settings")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 0) {
                editState.put(player.getUniqueId(), 13);
                navigatingSubMenu.add(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the cost amount:", NamedTextColor.GOLD));
                startInactivityTimeout(player, 13);
            }
            if (event.getRawSlot() == 4) {
                int configSlot = targetEditSlot.getOrDefault(player.getUniqueId(), -1);
                if (configSlot == -1) return;
                String guiName = activeEditingGui.get(player.getUniqueId());
                if (guiName == null) return;
                File guiFile = new File(guiDirectory, guiName + ".yml");
                FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
                boolean current = config.getBoolean("items." + configSlot + ".deduct_cost", true);
                config.set("items." + configSlot + ".deduct_cost", !current);
                try {
                    config.save(guiFile);
                } catch (IOException ignored) {}
                openVaultMenu(player, configSlot);
            }
            if (event.getRawSlot() == 8) {
                openAddonsMenu(player, targetEditSlot.get(player.getUniqueId()));
            }
            return;
        }

        if (title.equalsIgnoreCase("Select an Enchantment")) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();

            if (rawSlot == 0) {
                ItemStack item = itemBeingEdited.get(player.getUniqueId());
                if (item != null) openEnchantmentRemovalMenu(player, item);
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.ENCHANTED_BOOK) return;

            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;
            String technicalKey = meta.getPersistentDataContainer().get(enchantKey, PersistentDataType.STRING);
            if (technicalKey == null) return;

            NamespacedKey parsedKey = NamespacedKey.fromString(technicalKey);
            Enchantment found = parsedKey != null ? Registry.ENCHANTMENT.get(parsedKey) : null;
            if (found != null) {
                selectedEnchantment.put(player.getUniqueId(), found);
                editState.put(player.getUniqueId(), 6);
                navigatingSubMenu.add(player.getUniqueId());
                insideEnchantSelector.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the enchantment level value (1-255) (or type 'cancel'):", NamedTextColor.LIGHT_PURPLE));
                startInactivityTimeout(player, 6);
            }
            return;
        }

        if (title.equalsIgnoreCase("Select an Attribute")) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();

            if (rawSlot == 0) {
                ItemStack item = itemBeingEdited.get(player.getUniqueId());
                if (item != null) openAttributeRemovalMenu(player, item);
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.BOOK) return;

            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;
            String attrName = meta.getPersistentDataContainer().get(attributeKey, PersistentDataType.STRING);
            if (attrName == null) return;

            NamespacedKey parsedKey = NamespacedKey.fromString(attrName);
            Attribute attribute = parsedKey != null ? Registry.ATTRIBUTE.get(parsedKey) : null;
            if (attribute != null) {
                selectedAttribute.put(player.getUniqueId(), attribute);
                editState.put(player.getUniqueId(), 10);
                navigatingSubMenu.add(player.getUniqueId());
                insideAttributeSelector.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("Type the attribute modifier decimal amount (or type 'cancel'):", NamedTextColor.RED));
                startInactivityTimeout(player, 10);
            }
            return;
        }

        if (event.getClickedInventory() == player.getOpenInventory().getTopInventory()) {
            int state = editState.getOrDefault(player.getUniqueId(), 0);
            int slot = event.getSlot();

            String guiNameNav = activeEditingGui.get(player.getUniqueId());
            if (guiNameNav != null) {
                int editPage = activeEditingPage.getOrDefault(player.getUniqueId(), 1);
                FileConfiguration navCfg = YamlConfiguration.loadConfiguration(new File(guiDirectory, guiNameNav + ".yml"));
                String navKey = editPage == 1 ? "items" : "dialog.page." + editPage + ".items";
                String navType = navCfg.getString(navKey + "." + slot + ".dialog_nav", "");
                if (!navType.isEmpty()) {
                    event.setCancelled(true);

                    if (state == 14) {
                        editState.remove(player.getUniqueId());
                        saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                        targetEditSlot.put(player.getUniqueId(), slot);
                        openItemPropertyMenu(player, slot);
                        return;
                    }

                    int pg = activeEditingPage.getOrDefault(player.getUniqueId(), 1);
                    if ("next_page".equals(navType)) {
                        int totalPgs = navCfg.getInt("dialog.page_count", 1);
                        if (pg < totalPgs) {
                            saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                            int nextRows = navCfg.getInt("dialog.page." + (pg + 1) + ".rows", 1);
                            activeGuiRows.put(player.getUniqueId(), nextRows);
                            activeEditingPage.put(player.getUniqueId(), pg + 1);
                            navigatingSubMenu.add(player.getUniqueId());
                            player.closeInventory();
                            Bukkit.getScheduler().runTaskLater(this, (Runnable) () -> openWorkspace(player), 1L);
                        }
                    } else if ("back_page".equals(navType)) {
                        if (pg > 1) {
                            saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                            int prevRows = pg == 2 ? navCfg.getInt("rows", 1) : navCfg.getInt("dialog.page." + (pg - 1) + ".rows", 1);
                            activeGuiRows.put(player.getUniqueId(), prevRows);
                            activeEditingPage.put(player.getUniqueId(), pg - 1);
                            navigatingSubMenu.add(player.getUniqueId());
                            player.closeInventory();
                            Bukkit.getScheduler().runTaskLater(this, (Runnable) () -> openWorkspace(player), 1L);
                        }
                    }
                    return;
                }
            }

            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT || state == 14) {
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType() != Material.AIR) {
                    event.setCancelled(true);
                    saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                    targetEditSlot.put(player.getUniqueId(), slot);
                    openItemPropertyMenu(player, slot);
                    if (state == 14) editState.remove(player.getUniqueId());
                    return;
                }
            }
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (activeEditingGui.containsKey(player.getUniqueId())) {
                    scheduleAutoSave(player);
                }
            }, 1L);
        }

        if (event.getClickedInventory() == player.getInventory()) {
            int slot = event.getSlot();
            if ((slot >= 9 && slot <= 20) || slot == 25 || slot == 26) {
                event.setCancelled(true);
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || !clickedItem.hasItemMeta()) return;

                ItemMeta meta = clickedItem.getItemMeta();
                if (meta == null) return;
                Component nameComp = meta.displayName();
                String displayName = nameComp != null ? PlainTextComponentSerializer.plainText().serialize(nameComp) : "";

                if (displayName.contains("[ Add Row ]")) {
                    int currentRows = activeGuiRows.getOrDefault(player.getUniqueId(), 1);
                    if (currentRows < 6) {
                        saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                        activeGuiRows.put(player.getUniqueId(), currentRows + 1);
                        navigatingSubMenu.add(player.getUniqueId());
                        player.closeInventory();
                        Bukkit.getScheduler().runTaskLater(this, (Runnable) () -> openWorkspace(player), 1L);
                    } else {
                        saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                        String gn = activeEditingGui.get(player.getUniqueId());
                        if (gn != null) {
                            File gf = new File(guiDirectory, gn + ".yml");
                            FileConfiguration cfg = YamlConfiguration.loadConfiguration(gf);
                            int curPage = activeEditingPage.getOrDefault(player.getUniqueId(), 1);
                            int pageCount = cfg.getInt("dialog.page_count", 1);

                            if (curPage < pageCount) {
                                int nextRows = cfg.getInt("dialog.page." + (curPage + 1) + ".rows", 6);
                                activeGuiRows.put(player.getUniqueId(), nextRows);
                                activeEditingPage.put(player.getUniqueId(), curPage + 1);
                            } else {
                                int newPage = pageCount + 1;
                                cfg.set("dialog.page_count", newPage);

                                String curKey = curPage == 1 ? "items" : "dialog.page." + curPage + ".items";
                                int curRows = activeGuiRows.getOrDefault(player.getUniqueId(), 1);
                                int nextNavSlot = curRows * 9 - 1;
                                cfg.set(curKey + "." + nextNavSlot + ".dialog_nav", "next_page");
                                cfg.set(curKey + "." + nextNavSlot + ".item", createNavButton("next_page"));

                                String newKey = "dialog.page." + newPage + ".items";
                                cfg.set(newKey + ".0.dialog_nav", "back_page");
                                cfg.set(newKey + ".0.item", createNavButton("back_page"));
                                cfg.set("dialog.page." + newPage + ".rows", 1);
                                try { cfg.save(gf); } catch (IOException ignored) {}
                                activeGuiRows.put(player.getUniqueId(), 1);
                                activeEditingPage.put(player.getUniqueId(), newPage);
                                player.sendMessage(Component.text("Page " + newPage + " created! Editing page " + newPage + " now.", NamedTextColor.GREEN));
                            }
                            navigatingSubMenu.add(player.getUniqueId());
                            player.closeInventory();
                            Bukkit.getScheduler().runTaskLater(this, (Runnable) () -> openWorkspace(player), 1L);
                        }
                    }
                } else if (displayName.contains("[ Delete Row ]")) {
                    int currentRows = activeGuiRows.getOrDefault(player.getUniqueId(), 1);
                    int curPage = activeEditingPage.getOrDefault(player.getUniqueId(), 1);
                    if (currentRows > 1) {
                        saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                        activeGuiRows.put(player.getUniqueId(), currentRows - 1);
                        navigatingSubMenu.add(player.getUniqueId());
                        player.closeInventory();
                        Bukkit.getScheduler().runTaskLater(this, (Runnable) () -> openWorkspace(player), 1L);
                    } else if (curPage > 1) {
                        String gn = activeEditingGui.get(player.getUniqueId());
                        if (gn != null) {
                            File gf = new File(guiDirectory, gn + ".yml");
                            FileConfiguration cfg = YamlConfiguration.loadConfiguration(gf);
                            int pageCount = cfg.getInt("dialog.page_count", 1);

                            cfg.set("dialog.page." + curPage, null);
                            for (int p = curPage + 1; p <= pageCount; p++) {
                                Object pageData = cfg.get("dialog.page." + p);
                                cfg.set("dialog.page." + (p - 1), pageData);
                                cfg.set("dialog.page." + p, null);
                            }
                            cfg.set("dialog.page_count", Math.max(1, pageCount - 1));

                            int prevPage = curPage - 1;
                            String prevKey = prevPage == 1 ? "items" : "dialog.page." + prevPage + ".items";
                            ConfigurationSection prevSection = cfg.getConfigurationSection(prevKey);
                            if (prevSection != null) {
                                for (String k : new ArrayList<>(prevSection.getKeys(false))) {
                                    if ("next_page".equals(cfg.getString(prevKey + "." + k + ".dialog_nav", ""))) {
                                        cfg.set(prevKey + "." + k, null);
                                    }
                                }
                            }

                            int prevRows = prevPage == 1 ? cfg.getInt("rows", 1) : cfg.getInt("dialog.page." + prevPage + ".rows", 1);
                            try { cfg.save(gf); } catch (IOException ignored) {}

                            activeEditingPage.put(player.getUniqueId(), prevPage);
                            activeGuiRows.put(player.getUniqueId(), prevRows);
                            player.sendMessage(Component.text("Page " + curPage + " deleted.", NamedTextColor.RED));
                            navigatingSubMenu.add(player.getUniqueId());
                            player.closeInventory();
                            Bukkit.getScheduler().runTaskLater(this, (Runnable) () -> openWorkspace(player), 1L);
                        }
                    }
                } else if (displayName.contains("[ Bind Command ]")) {
                    saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                    navigatingSubMenu.add(player.getUniqueId());
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(this, () -> openBindCommandMenu(player), 1L);
                } else if (displayName.contains("[ Rename GUI Title ]")) {
                    editState.put(player.getUniqueId(), 7);
                    navigatingSubMenu.add(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage(Component.text("Type the new Title Banner name in chat (or type 'cancel'):", NamedTextColor.GOLD));
                    startInactivityTimeout(player, 7);
                } else if (displayName.contains("[ Item Properties Changer ]")) {
                    player.sendMessage(Component.text("Click any item inside your canvas layout to configure its actions/properties properties!", NamedTextColor.LIGHT_PURPLE));
                    editState.put(player.getUniqueId(), 14);
                } else if (displayName.contains("[ Creative Picker ]")) {
                    saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                    editState.put(player.getUniqueId(), 5);
                    previousGameMode.put(player.getUniqueId(), player.getGameMode());
                    navigatingSubMenu.add(player.getUniqueId());
                    player.closeInventory();
                    player.setGameMode(org.bukkit.GameMode.CREATIVE);
                    player.sendMessage(Component.text("Grab an item from creative mode!", NamedTextColor.YELLOW));
                } else if (displayName.contains("[ Edit Permissions ]")) {
                    editState.put(player.getUniqueId(), 11);
                    navigatingSubMenu.add(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage(Component.text("Type the required permission node string in chat (or type 'cancel'):", NamedTextColor.AQUA));
                    startInactivityTimeout(player, 11);
                } else if (displayName.contains("[ Change GUI Type ]")) {
                    saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                    navigatingSubMenu.add(player.getUniqueId());
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(this, () -> openGuiTypeMenu(player), 1L);
                } else if (displayName.contains("[ Dialog Mode:")) {
                    saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                    String guiName = activeEditingGui.get(player.getUniqueId());
                    if (guiName != null) {
                        File guiFile = new File(guiDirectory, guiName + ".yml");
                        FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
                        String currentType = config.getString("gui_type", "CHEST");

                        if ("DIALOG".equals(currentType)) {
                            String restoreType = config.getString("previous_gui_type", "CHEST");
                            config.set("gui_type", restoreType);
                            player.sendMessage(Component.text("Dialog mode disabled — back to a normal inventory GUI.", NamedTextColor.YELLOW));
                        } else {
                            config.set("previous_gui_type", currentType);
                            config.set("gui_type", "DIALOG");
                            player.sendMessage(Component.text("Dialog mode enabled — this GUI will show as a native dialog.", NamedTextColor.GREEN));
                        }
                        try { config.save(guiFile); } catch (IOException ignored) {}
                    }
                    navigatingSubMenu.add(player.getUniqueId());
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(this, () -> openWorkspace(player), 1L);
                } else if (displayName.contains("[ Close ]")) {
                    if (!title.equalsIgnoreCase("Configure Item Actions")) {
                        saveCurrentLayout(player, player.getOpenInventory().getTopInventory(), false);
                    }
                    player.closeInventory();
                    cleanSession(player);
                }
            }
        }
    }

    @EventHandler
    public void onCreativeMenuInteract(@NotNull InventoryCreativeEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!activeEditingGui.containsKey(player.getUniqueId())) return;

        if (editState.getOrDefault(player.getUniqueId(), 0) == 5) {
            ItemStack picked = event.getCursor();
            if (picked.getType() != Material.AIR) {
                editState.remove(player.getUniqueId());
                ItemStack finalItem = picked.clone();
                event.setCancelled(true);
                org.bukkit.GameMode restoreMode = previousGameMode.getOrDefault(player.getUniqueId(), org.bukkit.GameMode.SURVIVAL);
                previousGameMode.remove(player.getUniqueId());
                player.setGameMode(restoreMode);

                Bukkit.getScheduler().runTask(this, (Runnable) () -> {
                    openWorkspace(player);
                    Bukkit.getScheduler().runTaskLater(this, (Runnable) () -> {
                        player.getInventory().addItem(finalItem);
                        player.updateInventory();
                    }, 2L);
                });
            }
        }

        if (editState.getOrDefault(player.getUniqueId(), 0) == 37) {
            ItemStack picked = event.getCursor();
            if (picked != null && picked.getType() != Material.AIR) {
                event.setCancelled(true);
                ItemStack chosenItem = picked.clone();

                org.bukkit.GameMode restoreMode = previousGameMode.getOrDefault(player.getUniqueId(), org.bukkit.GameMode.SURVIVAL);
                previousGameMode.remove(player.getUniqueId());
                player.setGameMode(restoreMode);
                editState.remove(player.getUniqueId());

                String guiName = activeEditingGui.get(player.getUniqueId());
                Integer configSlot = targetEditSlot.get(player.getUniqueId());
                if (guiName != null && configSlot != null) {
                    File guiFile = new File(guiDirectory, guiName + ".yml");
                    FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

                    config.set("items." + configSlot + ".item", chosenItem);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("§a[Change Item] §fItem changed successfully!", NamedTextColor.GREEN));
                }

                final int returnSlot = targetEditSlot.getOrDefault(player.getUniqueId(), 0);
                Bukkit.getScheduler().runTask(this, () -> {
                    insidePropertiesMenu.add(player.getUniqueId());
                    openItemPropertyMenu(player, returnSlot);
                });
            }
        }

    }

    @EventHandler
    public void onInventoryDrag(@NotNull org.bukkit.event.inventory.InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!activeEditingGui.containsKey(player.getUniqueId())) return;
        int topSize = event.getInventory().getSize();
        boolean affectsCanvas = event.getRawSlots().stream().anyMatch(s -> s < topSize);
        if (affectsCanvas) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (activeEditingGui.containsKey(player.getUniqueId())) {
                    scheduleAutoSave(player);
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        int state = editState.getOrDefault(player.getUniqueId(), 0);
        if (!activeEditingGui.containsKey(player.getUniqueId()) || state == 0) return;

        event.setCancelled(true);
        clearTimeout(player.getUniqueId());
        String rawText = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (rawText.equalsIgnoreCase("cancel")) {
            editState.remove(player.getUniqueId());
            selectedEnchantment.remove(player.getUniqueId());
            selectedAttribute.remove(player.getUniqueId());

            Integer lastSlot = targetEditSlot.get(player.getUniqueId());
            itemBeingEdited.remove(player.getUniqueId());

            Bukkit.getScheduler().runTask(this, (Runnable) () -> {
                player.sendMessage(Component.text("Error: Setup modification canceled.", NamedTextColor.RED));
                if ((state == 8 || state == 9 || state == 10 || state == 6 || state == 11 || state == 2 || state == 3 || state == 12
                        || state == 14 || state == 16 || state == 17 || state == 18 || state == 19 || state == 20 || state == 21 || state == 22 || state == 23) && lastSlot != null) {
                    openItemPropertyMenu(player, lastSlot);
                } else if (state == 13 && lastSlot != null) {
                    openVaultMenu(player, lastSlot);
                } else if (state == 1) {
                    openBindCommandMenu(player);
                } else if (state == 30 || state == 31 || state == 32 || state == 33) {
                    openDialogMenu(player);
                } else {
                    targetEditSlot.remove(player.getUniqueId());
                    openWorkspace(player);
                }
            });
            return;
        }

        String inputMessage = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());

        Bukkit.getScheduler().runTask(this, (Runnable) () -> {
            String guiName = activeEditingGui.get(player.getUniqueId());
            File guiFile = new File(guiDirectory, guiName + ".yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

            if (state == 1) {
                String commandWord = inputMessage.toLowerCase().replace(" ", "");
                List<String> boundCmds = new ArrayList<>(config.getStringList("commands"));
                if (!boundCmds.contains(commandWord)) {
                    boundCmds.add(commandWord);
                }
                config.set("commands", boundCmds);
                config.set("command", boundCmds.get(0));
                try {
                    config.save(guiFile);
                } catch (IOException ignored) {
                }
                registerCoreServerCommand(commandWord, guiName);
                player.sendMessage(Component.text("Command /" + commandWord + " linked successfully!", NamedTextColor.GOLD));
                editState.remove(player.getUniqueId());
                openBindCommandMenu(player);
            } else if (state == 2 || state == 3) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                ItemStack liveItem = itemBeingEdited.remove(player.getUniqueId());
                if (slotObj != null && liveItem != null) {
                    ItemMeta meta = liveItem.getItemMeta();
                    if (meta != null) {
                        if (state == 2)
                            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(inputMessage));
                        else meta.lore(List.of(LegacyComponentSerializer.legacyAmpersand().deserialize(inputMessage)));
                        liveItem.setItemMeta(meta);
                        config.set("items." + slotObj + ".item", liveItem);
                        try {
                            config.save(guiFile);
                        } catch (IOException ignored) {
                        }
                    }
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
                else openWorkspace(player);
            } else if (state == 6) {
                Enchantment enchant = selectedEnchantment.remove(player.getUniqueId());
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                ItemStack liveItem = itemBeingEdited.get(player.getUniqueId());
                editState.remove(player.getUniqueId());

                int level = 1;
                try {
                    level = Integer.parseInt(rawText);
                } catch (NumberFormatException ignored) {
                }

                if (slotObj != null && enchant != null && liveItem != null) {
                    ItemMeta m = liveItem.getItemMeta();
                    if (m != null) {
                        m.addEnchant(enchant, level, true);
                        liveItem.setItemMeta(m);
                        config.set("items." + slotObj + ".item", liveItem);
                        try {
                            config.save(guiFile);
                        } catch (IOException ignored) {
                        }
                    }
                }
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
                else openWorkspace(player);
            } else if (state == 7) {
                activeGuiTitle.put(player.getUniqueId(), inputMessage);
                config.set("title", inputMessage);
                try {
                    config.save(guiFile);
                } catch (IOException ignored) {
                }
                editState.remove(player.getUniqueId());
                openWorkspace(player);
            } else if (state == 8) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    config.set("items." + slotObj + ".action_command", inputMessage);
                    try {
                        config.save(guiFile);
                    } catch (IOException ignored) {
                    }
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
            } else if (state == 9) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    config.set("items." + slotObj + ".warp_gui", inputMessage.toLowerCase().replace(" ", ""));
                    try {
                        config.save(guiFile);
                    } catch (IOException ignored) {
                    }
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
            } else if (state == 10) {
                Attribute attribute = selectedAttribute.remove(player.getUniqueId());
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                ItemStack liveItem = itemBeingEdited.get(player.getUniqueId());
                editState.remove(player.getUniqueId());

                double value = 0.0;
                try {
                    value = Double.parseDouble(rawText);
                } catch (NumberFormatException ignored) {
                }

                if (slotObj != null && attribute != null && liveItem != null) {
                    ItemMeta m = liveItem.getItemMeta();
                    if (m != null) {
                        m.addAttributeModifier(attribute, new AttributeModifier(
                                NamespacedKey.fromString(attribute.getKey().getKey(), this),
                                value,
                                AttributeModifier.Operation.ADD_NUMBER,
                                EquipmentSlotGroup.ANY
                        ));
                        liveItem.setItemMeta(m);
                        config.set("items." + slotObj + ".item", liveItem);
                        try {
                            config.save(guiFile);
                        } catch (IOException ignored) {
                        }
                    }
                }
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
                else openWorkspace(player);
            } else if (state == 11) {
                config.set("permission", rawText);
                try {
                    config.save(guiFile);
                } catch (IOException ignored) {
                }
                player.sendMessage(Component.text("Global layout restriction node set to: " + rawText, NamedTextColor.GOLD));
                editState.remove(player.getUniqueId());
                openWorkspace(player);
            } else if (state == 12) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    config.set("items." + slotObj + ".click_permission", rawText);
                    try {
                        config.save(guiFile);
                    } catch (IOException ignored) {
                    }
                    player.sendMessage(Component.text("Item click action permission set to: " + rawText, NamedTextColor.GREEN));
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
            } else if (state == 13) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                double cost = 0.0;

                if (slotObj != null && guiName != null) {

                    try {
                        cost = Double.parseDouble(rawText);
                        config.set("items." + slotObj + ".click_cost", cost);
                        config.save(guiFile);
                        player.sendMessage(Component.text("Cost set to: " + cost, NamedTextColor.GREEN));
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("Invalid number! Please enter a valid amount.", NamedTextColor.RED));
                        editState.remove(player.getUniqueId());
                        openVaultMenu(player, slotObj);
                        return;
                    } catch (IOException e) {
                        player.sendMessage(Component.text("Error saving file!", NamedTextColor.RED));
                        e.printStackTrace();
                    }
                }

                editState.remove(player.getUniqueId());
                openVaultMenu(player, slotObj);
            } else if (state == 14) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    config.set("items." + slotObj + ".console_command", rawText);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("Console command set to: " + rawText, NamedTextColor.GREEN));
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
            } else if (state == 16) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    config.set("items." + slotObj + ".broadcast_message", inputMessage);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("Broadcast message set!", NamedTextColor.GREEN));
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
            } else if (state == 17) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    config.set("items." + slotObj + ".action_bar_message", inputMessage);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("Action bar message set! Now type the duration in seconds (or 'cancel'):", NamedTextColor.GREEN));
                    editState.put(player.getUniqueId(), 18);
                    startInactivityTimeout(player, 18);
                    return;
                }
                editState.remove(player.getUniqueId());
            } else if (state == 18) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    int duration = 3;
                    try { duration = Integer.parseInt(rawText); } catch (NumberFormatException ignored) {}
                    config.set("items." + slotObj + ".action_bar_duration", duration);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("Action bar duration set to " + duration + "s!", NamedTextColor.GREEN));
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
            } else if (state == 19) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    int exp = 0;
                    try { exp = Integer.parseInt(rawText); } catch (NumberFormatException ignored) {}
                    config.set("items." + slotObj + ".exp_amount", exp);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("EXP amount set to " + exp + "!", NamedTextColor.GREEN));
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
            } else if (state == 20) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    int cmd = 0;
                    try { cmd = Integer.parseInt(rawText); } catch (NumberFormatException ignored) {}
                    ItemStack item = config.getItemStack("items." + slotObj + ".item");
                    if (item != null) {
                        ItemMeta m = item.getItemMeta();
                        if (m != null) {
                            m.setCustomModelData(cmd == 0 ? null : cmd);
                            item.setItemMeta(m);
                            config.set("items." + slotObj + ".item", item);
                        }
                    }
                    config.set("items." + slotObj + ".custom_model_data", cmd);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("Custom model data set to " + cmd + "!", NamedTextColor.GREEN));
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openItemPropertyMenu(player, slotObj);
            } else if (state == 21) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    List<String> cmds = config.getStringList("items." + slotObj + ".action_commands");
                    cmds.add(rawText.replace("/", ""));
                    config.set("items." + slotObj + ".action_commands", cmds);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("Command added: /" + rawText, NamedTextColor.GREEN));
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openCommandsMenu(player, slotObj);
            } else if (state == 22) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    config.set("items." + slotObj + ".bossbar_text", inputMessage);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("Bossbar text set!", NamedTextColor.GREEN));
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openBossbarMenu(player, slotObj);
            } else if (state == 23) {
                Integer slotObj = targetEditSlot.get(player.getUniqueId());
                if (slotObj != null) {
                    int dur = 5;
                    try { dur = Integer.parseInt(rawText); } catch (NumberFormatException ignored) {}
                    config.set("items." + slotObj + ".bossbar_duration", dur);
                    try { config.save(guiFile); } catch (IOException ignored) {}
                    player.sendMessage(Component.text("Bossbar duration set to " + dur + "s!", NamedTextColor.GREEN));
                }
                editState.remove(player.getUniqueId());
                if (slotObj != null) openBossbarMenu(player, slotObj);
            } else if (state == 30) {
                config.set("dialog.title", inputMessage);
                try { config.save(guiFile); } catch (IOException ignored) {}
                player.sendMessage(Component.text("Dialog title set!", NamedTextColor.GREEN));
                editState.remove(player.getUniqueId());
                openDialogMenu(player);
            } else if (state == 31) {
                config.set("dialog.message", inputMessage);
                try { config.save(guiFile); } catch (IOException ignored) {}
                player.sendMessage(Component.text("Dialog message set!", NamedTextColor.GREEN));
                editState.remove(player.getUniqueId());
                openDialogMenu(player);
            } else if (state == 34) {
                int cols = 2;
                try { cols = Integer.parseInt(rawText.trim()); } catch (NumberFormatException ignored) {}
                cols = Math.max(1, cols);
                config.set("dialog.columns", cols);
                try { config.save(guiFile); } catch (IOException ignored) {}
                player.sendMessage(Component.text("Buttons per row set to " + cols + "!", NamedTextColor.GREEN));
                editState.remove(player.getUniqueId());
                openDialogMenu(player);
            }
        });
    }

    private void registerCoreServerCommand(String commandName, String guiTargetName) {
        if (commandMap == null) return;
        BukkitCommand dynamicCommand = new BukkitCommand(commandName) {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                if (sender instanceof Player player) {
                    File file = new File(guiDirectory, guiTargetName + ".yml");
                    if (file.exists()) {
                        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                        String perm = config.getString("permission");
                        if (perm != null && !perm.isEmpty() && !perm.equalsIgnoreCase("None") && !player.hasPermission(perm)) {
                            player.sendMessage(Component.text("Error: You do not possess structural permissions to view this menu framework.", NamedTextColor.RED));
                            return true;
                        }
                    }
                    playerGuiHistory.remove(player.getUniqueId());
                    openSavedGuiForPlayer(player, guiTargetName);
                }
                return true;
            }
        };
        commandMap.register("guicreator", dynamicCommand);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) onlinePlayer.updateCommands();
    }

    /** Schedules a debounced auto-save 1 tick after the last canvas change. */
    private void scheduleAutoSave(Player player) {
        UUID uuid = player.getUniqueId();
        org.bukkit.scheduler.BukkitTask existing = pendingAutoSave.remove(uuid);
        if (existing != null) existing.cancel();
        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            pendingAutoSave.remove(uuid);
            Inventory top = player.getOpenInventory().getTopInventory();
            saveCurrentLayout(player, top, false);
        }, 1L);
        pendingAutoSave.put(uuid, task);
    }

    private void saveCurrentLayout(Player player, Inventory topInventory, boolean notify) {
        String guiName = activeEditingGui.get(player.getUniqueId());
        if (guiName == null) return;

        int rows = activeGuiRows.getOrDefault(player.getUniqueId(), 1);
        int editPage = activeEditingPage.getOrDefault(player.getUniqueId(), 1);
        String customTitle = activeGuiTitle.getOrDefault(player.getUniqueId(), "&8Custom Menu: " + guiName);
        String itemsKey = editPage == 1 ? "items" : "dialog.page." + editPage + ".items";

        File guiFile = new File(guiDirectory, guiName + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

        Map<Integer, Boolean> colCache = new HashMap<>();
        Map<Integer, Boolean> giveCache = new HashMap<>();
        Map<Integer, Boolean> closeCache = new HashMap<>();
        Map<Integer, String> warpCache = new HashMap<>();
        Map<Integer, String> permCache = new HashMap<>();
        Map<Integer, Double> costCache = new HashMap<>();
        Map<Integer, Boolean> deductCache = new HashMap<>();
        Map<Integer, List<String>> cmdsCache = new HashMap<>();
        Map<Integer, String> consoleCmdCache = new HashMap<>();
        Map<Integer, String> soundCache = new HashMap<>();
        Map<Integer, String> broadcastCache = new HashMap<>();
        Map<Integer, String> actionBarMsgCache = new HashMap<>();
        Map<Integer, Integer> actionBarDurCache = new HashMap<>();
        Map<Integer, Integer> expCache = new HashMap<>();
        Map<Integer, Integer> customModelDataCache = new HashMap<>();
        Map<Integer, String> bossbarTextCache = new HashMap<>();
        Map<Integer, String> bossbarColorCache = new HashMap<>();
        Map<Integer, Integer> bossbarDurCache = new HashMap<>();
        Map<Integer, String> dialogNavCache = new HashMap<>();

        ConfigurationSection section = config.getConfigurationSection(itemsKey);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int s = Integer.parseInt(key);
                    colCache.put(s, config.getBoolean(itemsKey + "." + key + ".collectable", false));
                    giveCache.put(s, config.getBoolean(itemsKey + "." + key + ".give_item", false));
                    closeCache.put(s, config.getBoolean(itemsKey + "." + key + ".close_on_click", false));
                    warpCache.put(s, config.getString(itemsKey + "." + key + ".warp_gui", "None"));
                    permCache.put(s, config.getString(itemsKey + "." + key + ".click_permission", "None"));
                    costCache.put(s, config.getDouble(itemsKey + "." + key + ".click_cost", 0.0));
                    deductCache.put(s, config.getBoolean(itemsKey + "." + key + ".deduct_cost", true));
                    cmdsCache.put(s, config.getStringList(itemsKey + "." + key + ".action_commands"));
                    consoleCmdCache.put(s, config.getString(itemsKey + "." + key + ".console_command", "None"));
                    soundCache.put(s, config.getString(itemsKey + "." + key + ".click_sound", "None"));
                    broadcastCache.put(s, config.getString(itemsKey + "." + key + ".broadcast_message", "None"));
                    actionBarMsgCache.put(s, config.getString(itemsKey + "." + key + ".action_bar_message", "None"));
                    actionBarDurCache.put(s, config.getInt(itemsKey + "." + key + ".action_bar_duration", 3));
                    expCache.put(s, config.getInt(itemsKey + "." + key + ".exp_amount", 0));
                    customModelDataCache.put(s, config.getInt(itemsKey + "." + key + ".custom_model_data", 0));
                    bossbarTextCache.put(s, config.getString(itemsKey + "." + key + ".bossbar_text", "None"));
                    bossbarColorCache.put(s, config.getString(itemsKey + "." + key + ".bossbar_color", "PURPLE"));
                    bossbarDurCache.put(s, config.getInt(itemsKey + "." + key + ".bossbar_duration", 5));
                    String navType = config.getString(itemsKey + "." + key + ".dialog_nav", "");
                    if (!navType.isEmpty()) dialogNavCache.put(s, navType);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (editPage == 1) {
            config.set("rows", rows);
            config.set("title", customTitle);
        } else {
            config.set("dialog.page." + editPage + ".rows", rows);
        }
        config.set(itemsKey, null);

        for (int i = 0; i < topInventory.getSize(); i++) {
            ItemStack item = topInventory.getItem(i);
            String navType = dialogNavCache.getOrDefault(i, "");
            if (!navType.isEmpty()) {
                config.set(itemsKey + "." + i + ".dialog_nav", navType);
                ItemStack liveNavItem = topInventory.getItem(i);
                if (liveNavItem != null && liveNavItem.getType() != Material.AIR) {
                    config.set(itemsKey + "." + i + ".item", liveNavItem);
                } else {
                    config.set(itemsKey + "." + i + ".item", createNavButton(navType));
                }
            } else if (item != null && item.getType() != Material.AIR) {
                config.set(itemsKey + "." + i + ".item", item);
                config.set(itemsKey + "." + i + ".collectable", colCache.getOrDefault(i, false));
                config.set(itemsKey + "." + i + ".give_item", giveCache.getOrDefault(i, false));
                config.set(itemsKey + "." + i + ".close_on_click", closeCache.getOrDefault(i, false));
                config.set(itemsKey + "." + i + ".warp_gui", warpCache.getOrDefault(i, "None"));
                config.set(itemsKey + "." + i + ".click_permission", permCache.getOrDefault(i, "None"));
                double savedCost = costCache.getOrDefault(i, 0.0);
                if (savedCost > 0.0) config.set(itemsKey + "." + i + ".click_cost", savedCost);
                config.set(itemsKey + "." + i + ".deduct_cost", deductCache.getOrDefault(i, true));
                List<String> savedCmds = cmdsCache.getOrDefault(i, new ArrayList<>());
                if (!savedCmds.isEmpty()) config.set(itemsKey + "." + i + ".action_commands", savedCmds);
                String savedConsole = consoleCmdCache.getOrDefault(i, "None");
                if (!savedConsole.equalsIgnoreCase("None")) config.set(itemsKey + "." + i + ".console_command", savedConsole);
                String savedSound = soundCache.getOrDefault(i, "None");
                if (!savedSound.equalsIgnoreCase("None")) config.set(itemsKey + "." + i + ".click_sound", savedSound);
                String savedBroadcast = broadcastCache.getOrDefault(i, "None");
                if (!savedBroadcast.equalsIgnoreCase("None")) config.set(itemsKey + "." + i + ".broadcast_message", savedBroadcast);
                String savedActionBar = actionBarMsgCache.getOrDefault(i, "None");
                if (!savedActionBar.equalsIgnoreCase("None")) {
                    config.set(itemsKey + "." + i + ".action_bar_message", savedActionBar);
                    config.set(itemsKey + "." + i + ".action_bar_duration", actionBarDurCache.getOrDefault(i, 3));
                }
                int savedExp = expCache.getOrDefault(i, 0);
                if (savedExp > 0) config.set(itemsKey + "." + i + ".exp_amount", savedExp);
                int savedCmd = customModelDataCache.getOrDefault(i, 0);
                if (savedCmd > 0) config.set(itemsKey + "." + i + ".custom_model_data", savedCmd);
                String savedBossbarText = bossbarTextCache.getOrDefault(i, "None");
                if (!savedBossbarText.equalsIgnoreCase("None")) {
                    config.set(itemsKey + "." + i + ".bossbar_text", savedBossbarText);
                    config.set(itemsKey + "." + i + ".bossbar_color", bossbarColorCache.getOrDefault(i, "PURPLE"));
                    config.set(itemsKey + "." + i + ".bossbar_duration", bossbarDurCache.getOrDefault(i, 5));
                }
            }
        }

        try {
            config.save(guiFile);
            if (notify) player.sendMessage(Component.text("Layout saved successfully!", NamedTextColor.GREEN));
        } catch (IOException e) {
            player.sendMessage(Component.text("Error saving layout file.", NamedTextColor.RED));
        }
    }

    private void openSavedGuiForPlayer(Player player, String guiName) {
        File guiFile = new File(guiDirectory, guiName + ".yml");
        if (!guiFile.exists()) {
            player.sendMessage(Component.text("Error: Linked GUI menu (" + guiName + ".yml) was not found!", NamedTextColor.RED));
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);

        if ("DIALOG".equals(config.getString("gui_type", "CHEST"))) {
            showNativeDialog(player, guiName, config);
            return;
        }

        int rows = config.getInt("rows", 1);
        String savedTitle = config.getString("title", "&8" + guiName);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            savedTitle = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, savedTitle);
        }

        String guiTypeStr = config.getString("gui_type", "CHEST");
        org.bukkit.event.inventory.InventoryType invType;
        try {
            invType = org.bukkit.event.inventory.InventoryType.valueOf(guiTypeStr);
        } catch (IllegalArgumentException e) {
            invType = org.bukkit.event.inventory.InventoryType.CHEST;
        }

        Inventory display;
        if (invType == org.bukkit.event.inventory.InventoryType.CHEST) {
            display = Bukkit.createInventory(null, rows * 9, LegacyComponentSerializer.legacyAmpersand().deserialize(savedTitle));
        } else {
            display = Bukkit.createInventory(null, invType, LegacyComponentSerializer.legacyAmpersand().deserialize(savedTitle));
        }

        ConfigurationSection section = config.getConfigurationSection("items");
        boolean hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot < display.getSize()) {
                        ItemStack item = config.getItemStack("items." + key + ".item");
                        if (item != null && hasPapi) {
                            ItemMeta meta = item.getItemMeta();
                            if (meta != null) {
                                if (meta.displayName() != null) {
                                    String rawName = LegacyComponentSerializer.legacyAmpersand().serialize(meta.displayName());
                                    rawName = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, rawName);
                                    meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(rawName));
                                }
                                if (meta.lore() != null) {
                                    List<Component> newLore = new ArrayList<>();
                                    for (Component line : meta.lore()) {
                                        String rawLine = LegacyComponentSerializer.legacyAmpersand().serialize(line);
                                        rawLine = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, rawLine);
                                        newLore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(rawLine));
                                    }
                                    meta.lore(newLore);
                                }
                                item.setItemMeta(meta);
                            }
                        }
                        display.setItem(slot, item);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        currentOpenGuiName.put(player.getUniqueId(), guiName);
        navigatingSubMenu.add(player.getUniqueId());
        player.openInventory(display);
        navigatingSubMenu.remove(player.getUniqueId());
    }

    @SuppressWarnings("UnstableApiUsage")
    private void showNativeDialog(Player player, String guiName, FileConfiguration config) {
        boolean hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        File guiFile = new File(guiDirectory, guiName + ".yml");

        String rawTitle = config.getString("dialog.title", guiName);
        String rawMsg   = config.getString("dialog.message", "");
        rawTitle = rawTitle.replace("%player%", player.getName());
        rawMsg   = rawMsg.replace("%player%", player.getName());
        if (hasPapi) {
            rawTitle = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, rawTitle);
            rawMsg   = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, rawMsg);
        }

        List<DialogBody> bodyList = new ArrayList<>();
        if (!rawMsg.isEmpty() && !rawMsg.equalsIgnoreCase("None")) {
            bodyList.add(DialogBody.plainMessage(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(rawMsg)));
        }

        final Dialog[] dialogHolder = new Dialog[1];

        int columns = Math.max(1, config.getInt("dialog.columns", 2));
        int buttonWidth = Math.max(40, (408 - (columns - 1) * 4) / columns);

        List<ActionButton> actionButtons = new ArrayList<>();
        int totalPages = config.getInt("dialog.page_count", 1);

        for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
            String sectionKey = pageNum == 1 ? "items" : "dialog.page." + pageNum + ".items";
            ConfigurationSection section = config.getConfigurationSection(sectionKey);
            if (section == null) continue;

            List<Integer> slots = new ArrayList<>();
            for (String key : section.getKeys(false)) {
                try { slots.add(Integer.parseInt(key)); } catch (NumberFormatException ignored) {}
            }
            Collections.sort(slots);

            for (int slot : slots) {
                String navType = config.getString(sectionKey + "." + slot + ".dialog_nav", "");
                if (!navType.isEmpty()) continue;
                ItemStack item = config.getItemStack(sectionKey + "." + slot + ".item");
                if (item == null) continue;
                ItemMeta meta = item.getItemMeta();

                String labelText;
                if (meta != null && meta.displayName() != null) {
                    labelText = LegacyComponentSerializer.legacyAmpersand().serialize(meta.displayName());
                } else {
                    labelText = item.getType().name().replace("_", " ");
                }
                String finalLabel = labelText.replace("%player%", player.getName());
                if (hasPapi) finalLabel = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, finalLabel);

                boolean closeOnClick = config.getBoolean(sectionKey + "." + slot + ".close_on_click", true);

                final int capturedSlot = slot;
                final String capturedSectionKey = sectionKey;
                DialogActionCallback callback = (view, audience) -> {
                    FileConfiguration freshConfig = YamlConfiguration.loadConfiguration(guiFile);
                    runConfiguredItemActionsFromSection(player, capturedSlot, capturedSectionKey, freshConfig);

                    if (closeOnClick) {
                        audience.closeDialog();
                    } else if (dialogHolder[0] != null) {
                        Bukkit.getScheduler().runTask(this, () -> audience.showDialog(dialogHolder[0]));
                    }
                };

                actionButtons.add(ActionButton.create(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(finalLabel),
                        null,
                        buttonWidth,
                        DialogAction.customClick(callback, ClickCallback.Options.builder()
                                .uses(ClickCallback.UNLIMITED_USES)
                                .build())
                ));
            }
        }

        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);

        DialogBase base = DialogBase.builder(titleComp)
                .body(bodyList)
                .build();

        DialogType type;
        if (actionButtons.isEmpty()) {
            type = DialogType.notice();
        } else {
            type = DialogType.multiAction(actionButtons).columns(columns).build();
        }

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        dialogHolder[0] = dialog;

        player.showDialog(dialog);
    }

    private void loadCustomCommandsFromFiles() {
        File[] files = guiDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            String guiName = file.getName().replace(".yml", "");
            List<String> cmds = config.getStringList("commands");
            for (String cmd : cmds) {
                if (cmd != null && !cmd.isEmpty()) {
                    registerCoreServerCommand(cmd.toLowerCase(), guiName);
                }
            }
            String legacyCmd = config.getString("command");
            if (legacyCmd != null && !legacyCmd.isEmpty() && !cmds.contains(legacyCmd)) {
                registerCoreServerCommand(legacyCmd.toLowerCase(), guiName);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        if (activeEditingGui.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        if (activeEditingGui.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        InventoryCloseEvent.Reason reason = event.getReason();
        boolean playerInitiated = reason == InventoryCloseEvent.Reason.PLAYER;

        if (!playerInitiated) return;

        if (activeEditingGui.containsKey(uuid)) {
            if (navigatingSubMenu.contains(uuid)) return;

            int state = editState.getOrDefault(uuid, 0);
            if (state != 0) {
                return;
            }

            if (insideVaultMenu.contains(uuid)) {
                Bukkit.getScheduler().runTask(this, () -> openAddonsMenu(player, targetEditSlot.getOrDefault(uuid, 0)));
                return;
            }
            if (insideAddonsMenu.contains(uuid) || insidePropertiesMenu.contains(uuid)
                    || insideEnchantSelector.contains(uuid) || insideAttributeSelector.contains(uuid)
                    || insideRemovalMenu.contains(uuid) || insideSoundMenu.contains(uuid)
                    || insideCommandsMenu.contains(uuid) || insideBossbarMenu.contains(uuid)) {
                Integer lastSlot = targetEditSlot.get(uuid);
                if (lastSlot != null) {
                    Bukkit.getScheduler().runTask(this, () -> openItemPropertyMenu(player, lastSlot));
                    return;
                }
            }
            if (insideGuiTypeMenu.contains(uuid)) {
                insideGuiTypeMenu.remove(uuid);
                Bukkit.getScheduler().runTask(this, () -> openWorkspace(player));
                return;
            }
            if (insideDialogMenu.contains(uuid)) {
                insideDialogMenu.remove(uuid);
                Bukkit.getScheduler().runTask(this, () -> openWorkspace(player));
                return;
            }
            if (insideBindCommandMenu.contains(uuid)) {
                insideBindCommandMenu.remove(uuid);
                Bukkit.getScheduler().runTask(this, () -> openWorkspace(player));
                return;
            }
            saveCurrentLayout(player, event.getInventory(), false);
            Bukkit.getScheduler().runTask(this, () -> cleanSession(player));
            return;
        }

        if (processingWarpShift.contains(uuid)) return;

        String openGui = currentOpenGuiName.get(uuid);
        if (openGui != null) {
            currentOpenGuiName.remove(uuid);
            playerGuiHistory.remove(uuid);
        }
    }

    private void cleanSession(Player player) {
        clearTimeout(player.getUniqueId());
        org.bukkit.scheduler.BukkitTask pending = pendingAutoSave.remove(player.getUniqueId());
        if (pending != null) pending.cancel();
        activeEditingGui.remove(player.getUniqueId());
        activeGuiRows.remove(player.getUniqueId());
        activeGuiTitle.remove(player.getUniqueId());
        activeEditingPage.remove(player.getUniqueId());
        editState.remove(player.getUniqueId());
        targetEditSlot.remove(player.getUniqueId());
        selectedEnchantment.remove(player.getUniqueId());
        selectedAttribute.remove(player.getUniqueId());
        navigatingSubMenu.remove(player.getUniqueId());
        itemBeingEdited.remove(player.getUniqueId());
        playerGuiHistory.remove(player.getUniqueId());
        currentOpenGuiName.remove(player.getUniqueId());
        processingWarpShift.remove(player.getUniqueId());
        insidePropertiesMenu.remove(player.getUniqueId());
        insideEnchantSelector.remove(player.getUniqueId());
        insideAttributeSelector.remove(player.getUniqueId());
        insideRemovalMenu.remove(player.getUniqueId());
        insideAddonsMenu.remove(player.getUniqueId());
        insideVaultMenu.remove(player.getUniqueId());
        insideSoundMenu.remove(player.getUniqueId());
        insideCommandsMenu.remove(player.getUniqueId());
        insideBindCommandMenu.remove(player.getUniqueId());
        insideBossbarMenu.remove(player.getUniqueId());
        insideGuiTypeMenu.remove(player.getUniqueId());
        insideDialogMenu.remove(player.getUniqueId());
        previousGameMode.remove(player.getUniqueId());

        Inventory pInv = player.getInventory();
        for (int i = 9; i < 36; i++) pInv.setItem(i, null);
    }

    private void openAddonsMenu(Player player, int slot) {
        insideAddonsMenu.add(player.getUniqueId());
        insideVaultMenu.remove(player.getUniqueId());
        Inventory menu = Bukkit.createInventory(null, 9, Component.text("Plugin Addons"));
        menu.setItem(0, createActionButton(Material.CHEST, "§6§l[ Vault Settings ]", "§7Manage costs."));
        menu.setItem(8, createActionButton(Material.ARROW, "§7← Back", ""));
        navigatingSubMenu.add(player.getUniqueId());
        player.openInventory(menu);
        Bukkit.getScheduler().runTaskLater(this, () -> navigatingSubMenu.remove(player.getUniqueId()), 1L);
    }

    private void openVaultMenu(Player player, int slot) {
        insideVaultMenu.add(player.getUniqueId());
        insideAddonsMenu.remove(player.getUniqueId());
        String guiName = activeEditingGui.get(player.getUniqueId());

        if (guiName == null) {
            player.sendMessage(Component.text("Error: GUI name is missing!", NamedTextColor.RED));
            return;
        }

        File guiFile = new File(guiDirectory, guiName + ".yml");

        if (!guiFile.exists()) {
            player.sendMessage(Component.text("Error: GUI file not found!", NamedTextColor.RED));
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(guiFile);
        double currentCost = config.getDouble("items." + slot + ".click_cost", 0.0);
        boolean deductEnabled = config.getBoolean("items." + slot + ".deduct_cost", true);
        double giveAmount = config.getDouble("items." + slot + ".give_money", 0.0);
        boolean giveEnabled = config.getBoolean("items." + slot + ".give_money_enabled", false);

        Inventory menu = Bukkit.createInventory(null, 18, Component.text("Vault Cost Settings"));

        menu.setItem(0, createActionButton(Material.PAPER, "§e§l[ Set Cost ]", "§7Current Price: §a$" + currentCost + "\n§7Click to type a new cost amount."));
        menu.setItem(4, createActionButton(
                deductEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                deductEnabled ? "§a§l[ Deduct Money: ON ]" : "§7§l[ Deduct Money: OFF ]",
                "§7Click to toggle whether money is taken\n§7from the player when they click this item."
        ));

        menu.setItem(9, createActionButton(Material.GOLD_INGOT, "§6§l[ Set Give Amount ]", "§7Current: §a$" + giveAmount + "\n§7Click to type an amount to give on click."));
        menu.setItem(13, createActionButton(
                giveEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                giveEnabled ? "§a§l[ Give Money: ON ]" : "§7§l[ Give Money: OFF ]",
                "§7Click to toggle whether money is given\n§7to the player when they click this item."
        ));
        menu.setItem(17, createActionButton(Material.ARROW, "§7← Back", ""));
        navigatingSubMenu.add(player.getUniqueId());
        player.openInventory(menu);
        Bukkit.getScheduler().runTaskLater(this, () -> navigatingSubMenu.remove(player.getUniqueId()), 1L);
    }
}