// [OUTLINE START]
// Package: dev.davimf.basebot.modules
// 
// Class: ModuleRegistry
// 
// Constructors:
//   - `Constructor` : `public ModuleRegistry(CommandManager commandManager, ComponentRouter componentRouter)`
// 
// Methods:
//   - `Method` : `public ModuleRegistry command(SlashCommand command)`
//   - `Method` : `public ModuleRegistry component(ComponentHandler handler)`
//   - `Method` : `public ModuleRegistry listener(Object jdaListener)`
//   - `Method` : `public List<Object> eventListeners()`
// 
// Fields:
//   - `Field` : `private final CommandManager commandManager`
//   - `Field` : `private final ComponentRouter componentRouter`
//   - `Field` : `private final List<Object> eventListeners`
// [OUTLINE END]



package dev.davimf.basebot.modules;

import dev.davimf.basebot.core.command.CommandManager;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentRouter;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects everything a {@link BotModule} contributes: slash commands, component
 * handlers and raw JDA event listeners. The application drains these into the
 * {@link CommandManager}, {@link ComponentRouter} and the JDA builder respectively.
 */
public final class ModuleRegistry {

    private final CommandManager commandManager;
    private final ComponentRouter componentRouter;
    private final List<Object> eventListeners = new ArrayList<>();

    public ModuleRegistry(CommandManager commandManager, ComponentRouter componentRouter) {
        this.commandManager = commandManager;
        this.componentRouter = componentRouter;
    }

    public ModuleRegistry command(SlashCommand command) {
        commandManager.register(command);
        return this;
    }

    public ModuleRegistry component(ComponentHandler handler) {
        componentRouter.register(handler);
        return this;
    }

    /** Registers a raw JDA {@code ListenerAdapter} (e.g. logging, role-event listeners). */
    public ModuleRegistry listener(Object jdaListener) {
        eventListeners.add(jdaListener);
        return this;
    }

    public List<Object> eventListeners() {
        return eventListeners;
    }
}
