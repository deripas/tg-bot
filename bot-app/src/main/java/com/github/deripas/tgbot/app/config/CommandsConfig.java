package com.github.deripas.tgbot.app.config;

import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandInfo;
import com.github.deripas.tgbot.app.commands.HelpCommand;
import com.github.deripas.tgbot.app.commands.LoginCommand;
import com.github.deripas.tgbot.app.commands.PingCommand;
import com.github.deripas.tgbot.app.commands.UserCommand;
import com.github.deripas.tgbot.app.props.BotProperties;
import com.github.deripas.tgbot.app.services.CommandDispatcher;
import com.github.deripas.tgbot.app.services.CommandDispatcherImpl;
import com.github.deripas.tgbot.app.telegram.TelegramUsers;
import com.github.deripas.tgbot.app.users.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ott.OneTimeTokenService;

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

    @Bean
    LoginCommand loginCommand(OneTimeTokenService tokens, BotProperties properties) {
        return new LoginCommand(tokens, properties.publicUrl().replaceAll("/+$", ""));
    }

    @Bean
    UserCommand userCommand(UserRepository users, TelegramUsers telegramUsers) {
        return new UserCommand(users, telegramUsers);
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
    CommandDispatcher commandDispatcher(List<Command> commands) {
        return new CommandDispatcherImpl(commands);
    }
}
