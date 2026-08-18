package com.akitaattribute.mcmoltenmetals.command;

import com.akitaattribute.mcmoltenmetals.registry.MoltenMetalRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class MoltenMetalCommand {
    private MoltenMetalCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("mcmoltenmetals")
                        .then(Commands.argument("metal", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(MoltenMetalRegistry.metalNames(), builder))
                                .executes(context -> giveBucket(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "metal"))))
        );
    }

    private static int giveBucket(CommandSourceStack source, String metalName) throws CommandSyntaxException {
        var metal = MoltenMetalRegistry.find(metalName);
        if (metal.isEmpty()) {
            source.sendFailure(Component.literal("Unknown metal '" + metalName + "'. Available: "
                    + String.join(", ", MoltenMetalRegistry.metalNames())));
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
}
