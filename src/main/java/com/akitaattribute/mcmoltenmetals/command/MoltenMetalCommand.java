package com.akitaattribute.mcmoltenmetals.command;

import com.akitaattribute.mcmoltenmetals.compat.MekanismCompat;
import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class MoltenMetalCommand {
    private static final String FABRICATOR = "fabricator";

    private MoltenMetalCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("mcmoltenmetals")
                        .then(Commands.argument("entry", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(availableEntries(), builder))
                                .executes(context -> giveEntry(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "entry"))))
        );
    }

    private static int giveEntry(CommandSourceStack source, String entry) throws CommandSyntaxException {
        if (FABRICATOR.equalsIgnoreCase(entry)) {
            return giveFabricator(source);
        }
        return giveBucket(source, entry);
    }

    private static int giveFabricator(CommandSourceStack source) throws CommandSyntaxException {
        var fabricator = MekanismCompat.fabricatorItem();
        if (fabricator.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Molten Fabricator is unavailable because the optional Mekanism integration is not active."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = new ItemStack(fabricator.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        source.sendSuccess(() -> Component.literal("Gave Molten Fabricator"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int giveBucket(CommandSourceStack source, String metalName) throws CommandSyntaxException {
        var metal = MoltenMetalRegistry.find(metalName);
        if (metal.isEmpty()) {
            source.sendFailure(Component.literal("Unknown entry '" + metalName + "'. Available: "
                    + String.join(", ", availableEntries())));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        ItemStack bucket = new ItemStack(metal.get().bucket().get());
        if (!player.getInventory().add(bucket)) {
            player.drop(bucket, false);
        }

        source.sendSuccess(
                () -> Component.literal("Gave Molten " + metal.get().definition().displayName() + " Bucket"),
                false);
        return Command.SINGLE_SUCCESS;
    }

    private static List<String> availableEntries() {
        List<String> entries = new ArrayList<>(MoltenMetalRegistry.metalNames());
        if (MekanismCompat.isLoaded()) {
            entries.add(FABRICATOR);
        }
        return entries;
    }
}
