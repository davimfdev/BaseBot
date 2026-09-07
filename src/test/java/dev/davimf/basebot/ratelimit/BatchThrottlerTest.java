package dev.davimf.basebot.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchThrottlerTest {

    @Test
    void emitsItemsInOrderedBatches() throws Exception {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        try {
            BatchThrottler throttler = new BatchThrottler(exec, 2, 10, TimeUnit.MILLISECONDS);
            List<List<Integer>> batches = Collections.synchronizedList(new ArrayList<>());
            throttler.run(List.of(1, 2, 3, 4, 5), b -> batches.add(new ArrayList<>(b)))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(3, batches.size());
            assertEquals(List.of(1, 2), batches.get(0));
            assertEquals(List.of(3, 4), batches.get(1));
            assertEquals(List.of(5), batches.get(2));
        } finally {
            exec.shutdownNow();
        }
    }
}
