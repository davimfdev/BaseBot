// [OUTLINE START]
// Package: dev.davimf.basebot.core.component
// 
// Class: Panels
// 
// Constructors:
//   - `Constructor` : `private Panels()`
// 
// Methods:
//   - `Method` : `public static Container container(int accentColor, ContainerChildComponent... children)`
//   - `Method` : `public static TextDisplay text(String markdown)`
//   - `Method` : `public static Separator divider()`
// 
// Fields:
//   - `Field` : `public static final int BLURPLE`
// [OUTLINE END]



package dev.davimf.basebot.core.component;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

import java.util.List;

/**
 * Helpers for building Components V2 "Container" panels — the modern replacement for
 * embeds. A {@link Container} renders like an accented embed but can hold interactive
 * components (buttons, select menus) directly inside it, so every panel keeps its
 * controls within the box for a cohesive, professional look.
 *
 * <p>Send a container with {@code event.replyComponents(container).useComponentsV2()...}
 * (or {@code editComponents(...)} when updating an existing V2 message).
 */
public final class Panels {

    /** Default accent colour (Discord blurple). */
    public static final int BLURPLE = 0x5865F2;

    private Panels() {}

    /** A container with the given accent colour and child components. */
    public static Container container(int accentColor, ContainerChildComponent... children) {
        return Container.of(List.of(children)).withAccentColor(accentColor);
    }

    /** Markdown text block inside a container. */
    public static TextDisplay text(String markdown) {
        return TextDisplay.of(markdown);
    }

    /** A thin divider line between sections of a container. */
    public static Separator divider() {
        return Separator.createDivider(Separator.Spacing.SMALL);
    }
}
