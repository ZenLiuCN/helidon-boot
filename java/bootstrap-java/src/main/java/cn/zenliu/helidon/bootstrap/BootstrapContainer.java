package cn.zenliu.helidon.bootstrap;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import reactor.util.annotation.NonNull;
import reactor.util.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.LogManager;
import java.util.stream.Collectors;


public final class BootstrapContainer {
    static class Pair<K, V> {
        private final K key;
        private final V value;

        Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }


    }

    static Pair<String, Function<Config, Service>> makePair(String path, Function<Config, Service> generator) {
        return new Pair<>(path, generator);
    }

    private static final InternalLogger logger = Slf4JLoggerFactory.getInstance("cn.zenliu.helidon.bootstrap" +
            ".BootstrapContainer");
    private static final Map<String, Function<Config, Service>> routeRegistry = new HashMap<>();

    static void routes(Pair<String, Function<Config, Service>>... routes) throws IllegalStateException {
        if (Arrays.stream(routes).allMatch(k -> routeRegistry.containsKey(k.key))) {
            throw new IllegalStateException("some route is already" +
                    " registered");
        }
        for (Pair<String, Function<Config, Service>> route : routes) {
            routeRegistry.put(route.key, route.value);
        }
    }

    private static Consumer<Routing.Builder> beforeRegisterRouting = null;

    void extending(Consumer<Routing.Builder> builder) {
        beforeRegisterRouting = builder;
    }

    private static final Map<String, Plugin> plugins = new HashMap<>();

    static void plugin(Plugin... pl) {
        if (plugins.size() == 0) {
            logger.warn("plugins already registered");
            return;
        }
        Set<Plugin> sets = Arrays.stream(pl).collect(Collectors.toSet());
        sets.forEach(e -> plugins.put(e.getName(), e));
    }

    @Nullable
    static <T extends Plugin> T getPlugin(String name) {
        return (T) plugins.get(name);
    }

    private static Supplier<Config> configurator = null;

    static void configuration(@NonNull Supplier<Config> conf) {
        configurator = conf;
    }

    static void loggerConfiguration(Function<LogManager, InputStream> act) throws IOException {
        LogManager m = LogManager.getLogManager();
        InputStream is = act.apply(m);
        m.readConfiguration(is);
    }

    private static Routing buildRouting(Config config) {
        Routing.Builder b = Routing.builder();
        if (beforeRegisterRouting != null) beforeRegisterRouting.accept(b);
        routeRegistry.forEach((n, a) -> {
            Service srv = a.apply(config);
            b.register(n, srv);
            logger.info("register api service [" + srv.getClass().getCanonicalName() + "] on " + n);
        });
        routeRegistry.clear();
        return b.build();
    }

    private static WebServer server = null;

    @Nullable
    static WebServer getServer() {
        return server;
    }

    static void start() {
        if (server != null) return;
        final Config cfg;
        if (configurator != null) {
            cfg = configurator.get();
        } else {
            cfg = Config.create();
        }
        server = WebServer
                .create(ServerConfiguration.create(cfg.get("server")), buildRouting(cfg));
        plugins.values().stream()
                .filter(Plugin::isBeforeStartServer)
                .forEach(p -> p.initialize(cfg, server));
        if (server.isRunning()) {
            logger.warn("some plugin started current server,escape normal start procedure");
            server.whenShutdown().thenRun(() -> {
                plugins.forEach((k, v) -> {
                    try {
                        v.doOnFinalize();
                    } catch (Exception e) {
                        logger.error("error on finalization plugin $name", e);
                    }
                });
                logger.info(" server [which start by some plugin] is DOWN. Good bye!");
            });
            return;
        }

        server.start()
                .thenAccept((ws) -> {
                    logger.info("WEB server is up! http://0.0.0.0:" + ws.port() + "/");
                    plugins.values().stream()
                            .filter(it -> !it.isBeforeStartServer())
                            .forEach(u -> u.initialize(cfg, server));
                    ws.whenShutdown()
                            .thenRun(() -> {
                                plugins.forEach((k, v) -> {
                                    try {
                                        v.doOnFinalize();
                                    } catch (Throwable it) {
                                        logger.error("error on finalization plugin $name", it);
                                    }
                                });
                                logger.info(" server is DOWN. Good bye!");
                            });
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });
    }

}
