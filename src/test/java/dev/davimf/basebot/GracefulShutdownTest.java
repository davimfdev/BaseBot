package dev.davimf.basebot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GracefulShutdownTest {

    @Test
    void closesComponentsInSafeOrderOnlyOnce() {
        List<String> order = new ArrayList<>();
        GracefulShutdown shutdown = new GracefulShutdown(
                () -> order.add("scheduler"),
                () -> order.add("jda"),
                () -> order.add("database"));

        shutdown.run();
        shutdown.run();

        assertEquals(List.of("scheduler", "jda", "database"), order);
    }
}
