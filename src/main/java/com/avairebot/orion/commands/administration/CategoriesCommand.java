package com.avairebot.orion.commands.administration;

import com.avairebot.orion.Orion;
import com.avairebot.orion.chat.MessageType;
import com.avairebot.orion.commands.Category;
import com.avairebot.orion.commands.CategoryHandler;
import com.avairebot.orion.commands.CommandPriority;
import com.avairebot.orion.contracts.commands.Command;
import com.avairebot.orion.database.controllers.GuildController;
import com.avairebot.orion.database.transformers.ChannelTransformer;
import com.avairebot.orion.database.transformers.GuildTransformer;
import com.avairebot.orion.factories.MessageFactory;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CategoriesCommand extends Command {

    private static final String ONLINE = "<:online:324986081378435072>";
    private static final String DISABLED = "<:away:324986135346675712>";
    private static final String DISABLE_GLOBALLY = "<:dnd:324986174806425610>";

    public CategoriesCommand(Orion orion) {
        super(orion, false);
    }

    @Override
    public String getName() {
        return "Categories Command";
    }

    @Override
    public String getDescription() {
        return "Shows status of all command categories in the current or mentioned channel, both for globally and per-channel.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Collections.singletonList("`:command [channel]` - Displays the status of the command categories in the mentioned channel, or the current channel if no channel was mentioned.");
    }

    @Override
    public String getExampleUsage() {
        return null;
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("categories", "cats");
    }

    @Override
    public CommandPriority getCommandPriority() {
        return CommandPriority.LOW;
    }

    @Override
    public boolean onCommand(Message message, String[] args) {
        TextChannel channel = message.getTextChannel();
        if (!message.getMentionedChannels().isEmpty()) {
            channel = message.getMentionedChannels().get(0);
        }

        String status = String.join("   ",
            ONLINE + " Enabled",
            DISABLED + " Disabled in Channel",
            DISABLE_GLOBALLY + " Disabled Globally"
        );

        GuildTransformer guildTransformer = GuildController.fetchGuild(orion, message.getGuild());
        ChannelTransformer transformer = guildTransformer.getChannel(channel.getId());

        List<String> items = new ArrayList<>();
        for (Category category : CategoryHandler.getValues()) {
            if (!transformer.isCategoryEnabledGlobally(category)) {
                items.add(DISABLE_GLOBALLY + category.getName());
                continue;
            }

            if (!transformer.isCategoryEnabled(category)) {
                items.add(DISABLED + category.getName());
                continue;
            }

            items.add(ONLINE + category.getName());
        }

        message.getChannel().sendMessage(MessageFactory.createEmbeddedBuilder()
            .setColor(MessageType.INFO.getColor())
            .setTitle("Command Category Status for #" + channel.getName())
            .setDescription(status + "\n\n" + String.join("\n", items))
            .build()
        ).queue();

        return true;
    }
}