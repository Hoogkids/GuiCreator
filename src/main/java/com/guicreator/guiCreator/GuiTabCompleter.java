/*
 * Copyright (c) 2026 [Hoogkids]
 * Licensed under the Creative Commons Attribution 4.0 International License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/
 */

package com.guicreator.guiCreator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GuiTabCompleter implements TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("create");
            subCommands.add("edit");
            subCommands.add("delete");
            subCommands.add("list");
            subCommands.add("help");

            String currentArg = args[0].toLowerCase();

            for (String sub : subCommands) {
                if (sub.startsWith(currentArg)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        return completions;
    }
}