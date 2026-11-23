package io.github.thebusybiscuit.slimefun4.core.services.github;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.google.gson.JsonElement;

class GitHubConnectorShutdownTest {
    @Test
    void downloadReleasesWorkerWithoutCallbacksWhenSlimefunIsDisabled() {
        AtomicBoolean called = new AtomicBoolean();
        GitHubConnector connector = new GitHubConnector(Mockito.mock(GitHubService.class), "Slimefun/Slimefun4") {
            @Override
            public String getFileName() {
                return "shutdown-test";
            }

            @Override
            public String getEndpoint() {
                return "";
            }

            @Override
            public Map<String, Object> getParameters() {
                return Map.of();
            }

            @Override
            public void onSuccess(JsonElement response) {
                called.set(true);
            }

            @Override
            public void onFailure() {
                called.set(true);
            }
        };

        long start = System.nanoTime();
        connector.download();
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2));
        assertFalse(called.get());
    }
}
