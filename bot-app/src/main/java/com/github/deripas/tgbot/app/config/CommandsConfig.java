package com.github.deripas.tgbot.app.config;

import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandInfo;
import com.github.deripas.tgbot.app.commands.HelpCommand;
import com.github.deripas.tgbot.app.commands.PingCommand;
import com.github.deripas.tgbot.app.props.BotProperties;
import com.github.deripas.tgbot.app.services.CommandDispatcher;
import com.github.deripas.tgbot.app.services.CommandDispatcherAuth;
import com.github.deripas.tgbot.app.services.CommandDispatcherImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Stream;

/**
 * Built-in commands and the command dispatcher.
 */
@Configuration(proxyBeanMethods = false)
class CommandsConfig {

    @Bean
    PingCommand pingCommand() {
        return new PingCommand();
    }

    /**
     * Spring excludes the bean being created from {@code commands}, so {@code /help} is appended explicitly.
     */
    @Bean
    HelpCommand helpCommand(List<Command> commands) {
        final List<CommandInfo> infos = Stream.concat(
                commands.stream().map(Command::info),
                Stream.of(HelpCommand.INFO)
        ).toList();
        return new HelpCommand(infos);
    }

    @Bean
    CommandDispatcher commandDispatcher(BotProperties properties, List<Command> commands) {
        final CommandDispatcher dispatcher = new CommandDispatcherImpl(commands);
        return new CommandDispatcherAuth(dispatcher, properties.allowedUsers());
    }
}
