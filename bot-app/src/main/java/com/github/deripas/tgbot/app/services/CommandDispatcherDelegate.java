package com.github.deripas.tgbot.app.services;

import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.CommandInfo;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Base class for {@link CommandDispatcher} decorators: forwards all calls to the wrapped dispatcher.
 * Subclasses override only the methods they change.
 */
@RequiredArgsConstructor
public abstract class CommandDispatcherDelegate implements CommandDispatcher {

    private final CommandDispatcher delegate;

    @Override
    public List<CommandInfo> getCommands() {
        return delegate.getCommands();
    }

    @Override
    public void handle(String command, CommandContext context) throws Exception {
        delegate.handle(command, context);
    }
}
