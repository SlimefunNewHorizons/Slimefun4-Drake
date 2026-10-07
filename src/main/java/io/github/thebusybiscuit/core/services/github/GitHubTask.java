package io.github.thebusybiscuit.slimefun4.core.services.github;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;

import dev.drake.dough.skins.PlayerSkin;
import dev.drake.dough.skins.UUIDLookup;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * This {@link GitHubTask} represents a {@link Runnable} that is run every X minutes.
 * It retrieves every {@link Contributor} of this project from GitHub.
 * 
 * @author TheBusyBiscuit
 * 
 * @see GitHubService
 * @see Contributor
 *
 */
class GitHubTask implements Runnable {

    private static final int MAX_REQUESTS_PER_MINUTE = 16;
    private final GitHubService gitHubService;
    private final AtomicBoolean isRetrying = new AtomicBoolean(false);
    private int consecutive429Errors = 0;

    GitHubTask(@Nonnull GitHubService github) {
        gitHubService = github;
    }

    @Override
    public void run() {

        if (Bukkit.isPrimaryThread()) {
            Slimefun.logger().log(Level.SEVERE, "The contributors task may never run on the main Thread!");
            return;
        }

        connectAndCache();
        grabTextures();
    }

    private void connectAndCache() {
        gitHubService.getConnectors().forEach(GitHubConnector::download);
    }

    /**
     * This method will pull the skin textures for every {@link Contributor} and store
     * the {@link UUID} and received skin inside a local cache {@link File}.
     */
    private void grabTextures() {
        /**
         * Store all queried usernames to prevent 429 responses for pinging
         * the same URL twice in one run.
         */
        Map<String, String> skins = new HashMap<>();
        int requests = 0;

        for (Contributor contributor : gitHubService.getContributors().values()) {
            int newRequests = requestTexture(contributor, skins);
            requests += newRequests;

            if (newRequests < 0 || requests >= MAX_REQUESTS_PER_MINUTE) {
                break;
            }
        }

        if (requests >= MAX_REQUESTS_PER_MINUTE && Slimefun.instance() != null && Slimefun.instance().isEnabled()) {
            // Slow down API requests and wait a minute after more than x requests were made
            Bukkit.getScheduler().runTaskLaterAsynchronously(Slimefun.instance(), this::grabTextures, 2 * 60 * 20L);
        }

        for (GitHubConnector connector : gitHubService.getConnectors()) {
            if (connector instanceof ContributionsConnector contributionsConnector && !contributionsConnector.hasFinished()) {
                return;
            }
        }

        /**
         * We only wanna save this if all Connectors finished already.
         * This will run multiple times but thats okay, this way we get as much
         * data as possible stored.
         */
        gitHubService.saveCache();
    }

    private int requestTexture(@Nonnull Contributor contributor, @Nonnull Map<String, String> skins) {
        if (!contributor.hasTexture()) {
            try {
                if (skins.containsKey(contributor.getMinecraftName())) {
                    contributor.setTexture(skins.get(contributor.getMinecraftName()));
                } else {
                    contributor.setTexture(pullTexture(contributor, skins));
                    consecutive429Errors = 0;
                    return contributor.getUniqueId().isPresent() ? 1 : 2;
                }
            } catch (IllegalArgumentException x) {
                // There cannot be a texture found because it is not a valid MC username
                contributor.setTexture(null);
            } catch (InterruptedException x) {
                Slimefun.logger().log(Level.WARNING, "The contributors thread was interrupted!");
                Thread.currentThread().interrupt();
            } catch (Exception x) {
                String msg = x.getMessage();

                if (msg != null && msg.contains("429")) {
                    int streak = ++consecutive429Errors;
                    // Backoff exponencial: 5m, 10m, 20m, 40m, hasta máximo 60m
                    int delayMinutes = Math.min(60, 5 * (1 << Math.min(4, streak - 1)));

                    if (streak == 1) {
                        Slimefun.logger().log(Level.WARNING, "Attempted to refresh skin cache, got this response: {0}: {1}", new Object[] { x.getClass().getSimpleName(), x.getMessage() });
                        Slimefun.logger().log(Level.WARNING, "Mojang API rate-limited this connection (HTTP 429). Retrying with exponential backoff up to 60m.");
                    } else {
                        Slimefun.logger().log(Level.FINE, "Skin cache refresh still rate-limited (HTTP 429, streak {0}). Next retry in {1}m.", new Object[] { streak, delayMinutes });
                    }

                    if (Slimefun.instance() != null && Slimefun.instance().isEnabled() && isRetrying.compareAndSet(false, true)) {
                        Bukkit.getScheduler().runTaskLaterAsynchronously(Slimefun.instance(), () -> {
                            try {
                                grabTextures();
                            } finally {
                                isRetrying.set(false);
                            }
                        }, delayMinutes * 60 * 20L);
                    }
                } else {
                    Slimefun.logger().log(Level.WARNING, "Attempted to refresh skin cache, got this response: {0}: {1}", new Object[] { x.getClass().getSimpleName(), x.getMessage() });
                }

                return -1;
            }
        }

        return 0;
    }

    private @Nullable String pullTexture(@Nonnull Contributor contributor, @Nonnull Map<String, String> skins) throws InterruptedException, ExecutionException, TimeoutException {
        Optional<UUID> uuid = contributor.getUniqueId();

        if (!uuid.isPresent()) {
            CompletableFuture<UUID> future = UUIDLookup.getUuidFromUsername(Slimefun.instance(), contributor.getMinecraftName());

            // Fixes #3241 - Do not wait for more than 30 seconds
            uuid = Optional.ofNullable(future.get(30, TimeUnit.SECONDS));
            uuid.ifPresent(contributor::setUniqueId);
        }

        if (uuid.isPresent()) {
            CompletableFuture<PlayerSkin> future = PlayerSkin.fromPlayerUUID(Slimefun.instance(), uuid.get());
            Optional<String> skin = Optional.of(future.get().getProfile().getBase64Texture());
            skins.put(contributor.getMinecraftName(), skin.orElse(""));
            return skin.orElse(null);
        } else {
            return null;
        }
    }

}
