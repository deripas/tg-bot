package com.github.deripas.tgbot.app.config;

import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandInfo;
import com.github.deripas.tgbot.app.commands.HelpCommand;
import com.github.deripas.tgbot.app.commands.LoginCommand;
import com.github.deripas.tgbot.app.commands.PingCommand;
import com.github.deripas.tgbot.app.commands.UserCommand;
import com.github.deripas.tgbot.app.TgBotProperties;
import com.github.deripas.tgbot.app.commands.CommandDispatcher;
import com.github.deripas.tgbot.app.telegram.TelegramUserService;
import com.github.deripas.tgbot.app.users.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.transaction.support.TransactionOperations;

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
    LoginCommand loginCommand(OneTimeTokenService tokens, TgBotProperties properties) {
        return new LoginCommand(tokens, properties.publicUrl().replaceAll("/+$", ""));
    }

    @Bean
    UserCommand userCommand(UserService users, TelegramUserService telegramUsers, TransactionOperations transactions) {
        return new UserCommand(users, telegramUsers, transactions);
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
        return new CommandDispatcher(commands);
    }
}
