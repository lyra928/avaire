package com.avairebot.orion;

import com.avairebot.orion.cache.CacheManager;
import com.avairebot.orion.commands.CommandHandler;
import com.avairebot.orion.commands.fun.*;
import com.avairebot.orion.commands.music.*;
import com.avairebot.orion.commands.system.EvalCommand;
import com.avairebot.orion.commands.system.SetStatusCommand;
import com.avairebot.orion.commands.utility.*;
import com.avairebot.orion.config.ConfigurationLoader;
import com.avairebot.orion.config.MainConfiguration;
import com.avairebot.orion.contracts.handlers.EventHandler;
import com.avairebot.orion.database.DatabaseManager;
import com.avairebot.orion.database.migrate.migrations.*;
import com.avairebot.orion.handlers.EventTypes;
import com.avairebot.orion.logger.Logger;
import com.avairebot.orion.scheduler.*;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.requests.SessionReconnectQueue;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

public class Orion {

    public final MainConfiguration config;
    public final Logger logger;
    public final CacheManager cache;
    public final DatabaseManager database;

    private JDA jda;

    public Orion() throws IOException, SQLException {
        this.logger = new Logger(this);
        this.cache = new CacheManager(this);

        ConfigurationLoader configLoader = new ConfigurationLoader();
        this.config = (MainConfiguration) configLoader.load("config.json", MainConfiguration.class);
        if (this.config == null) {
            this.logger.error("Something went wrong while trying to load the configuration, exiting program...");
            System.exit(0);
        }

        database = new DatabaseManager(this);
        database.getMigrations().register(
                new CreateGuildTableMigration(),
                new CreateGuildTypeTableMigration(),
                new CreateBlacklistTableMigration(),
                new CreatePlayerExperiencesTableMigration(),
                new CreateFeedbackTableMigration(),
                new CreateMusicPlaylistsTableMigration(),
                new CreateStatisticsTableMigration(),
                new CreateShardsTableMigration()
        );
        database.getMigrations().up();

        try {
            jda = prepareJDA().buildBlocking();
        } catch (LoginException | RateLimitedException | InterruptedException ex) {
            this.logger.error("Something went wrong while trying to connect to Discord, exiting program...");
            this.logger.exception(ex);
            System.exit(0);
        }

        this.registerCommands();
        this.registerJobs();
    }

    public JDA getJDA() {
        return jda;
    }

    private void registerCommands() {
        // Fun
        CommandHandler.register(new ChuckNorrisCommand(this));
        CommandHandler.register(new CoinflipCommand(this));
        CommandHandler.register(new GfycatCommand(this));
        CommandHandler.register(new RipCommand(this));
        CommandHandler.register(new RollCommand(this));

        // Music
        CommandHandler.register(new ClearQueueCommand(this));
        CommandHandler.register(new MoveHereCommand(this));
        CommandHandler.register(new PauseCommand(this));
        CommandHandler.register(new PlayCommand(this));
        CommandHandler.register(new ResumeCommand(this));
        CommandHandler.register(new SkipCommand(this));
        CommandHandler.register(new SongCommand(this));
        CommandHandler.register(new VolumeCommand(this));

        // System
        CommandHandler.register(new EvalCommand(this));
        CommandHandler.register(new SetStatusCommand(this));

        // Utility
        CommandHandler.register(new HelpCommand(this));
        CommandHandler.register(new PingCommand(this));
        CommandHandler.register(new InviteCommand(this));
        CommandHandler.register(new SourceCommand(this));
        CommandHandler.register(new StatsCommand(this));
    }

    private void registerJobs() {
        ScheduleHandler.registerJob(new ChangeGameJob(this));
        ScheduleHandler.registerJob(new GarbageCollectorJob(this));
        ScheduleHandler.registerJob(new UpdateAudioPlayedTimeJob(this));
        ScheduleHandler.registerJob(new ResetRespectStatisticsJob(this));
    }

    private JDABuilder prepareJDA() {
        JDABuilder builder = new JDABuilder(AccountType.BOT).setToken(this.config.botAuth().getToken());

        Class[] eventArguments = new Class[1];
        eventArguments[0] = Orion.class;

        for (EventTypes event : EventTypes.values()) {
            try {
                Object instance = event.getInstance().getDeclaredConstructor(eventArguments).newInstance(this);

                if (instance instanceof EventHandler) {
                    builder.addEventListener(instance);
                }
            } catch (InstantiationException | NoSuchMethodException | InvocationTargetException ex) {
                this.logger.error("Invalid listener adapter object parsed, failed to create a new instance!");
                this.logger.exception(ex);
            } catch (IllegalAccessException ex) {
                this.logger.error("An attempt was made to register a event listener called " + event + " but it failed somewhere!");
                this.logger.exception(ex);
            }
        }

        return builder.setReconnectQueue(new SessionReconnectQueue()).setAutoReconnect(true);
    }
}
