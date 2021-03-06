/*
 *  Copyright (c) 2020.  Zen.Liu .
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *   @Project: helidon-bootstrap
 *   @Module: bootstrap
 *   @File: Boot.java
 *   @Author:  lcz20@163.com
 *   @LastModified:  2020-04-27 12:04:26
 */

package cn.zenliu.helidon.bootstrap;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import lombok.val;
import lombok.var;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.LogManager;
import java.util.stream.Collectors;


public final class Boot {
    public final static class Pair<K, V> {
        private final K key;
        private final V value;

        Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }


    }

    public static Pair<String, Function<Config, Service>> makePair(String path, Function<Config, Service> generator) {
        return new Pair<>(path, generator);
    }

    private static final InternalLogger logger = Slf4JLoggerFactory.getInstance(Boot.class);
    private static final Map<String, Function<Config, Service>> routeRegistry = new HashMap<>();

    @SafeVarargs
    public static void routes(Pair<String, Function<Config, Service>>... routes) throws IllegalStateException {
        if (Arrays.stream(routes).allMatch(k -> routeRegistry.containsKey(k.key))) {
            throw new IllegalStateException("some route is already" +
                " registered");
        }
        for (Pair<String, Function<Config, Service>> route : routes) {
            routeRegistry.put(route.key, route.value);
        }
    }

    private static Consumer<Routing.Builder> beforeRegisterRouting = null;

    public static void extending(@NotNull Consumer<Routing.Builder> builder) {
        beforeRegisterRouting = builder;
    }

    private static Map<String, Plugin> plugins = new HashMap<>();
    @Nullable
    private static Plugin serverCreator = null;

    /**
     * register of plugins
     *
     * @param pl initialization is reversed order
     */
    public static void plugin(Plugin... pl) {
        if (plugins.size() > 0) {
            logger.warn("plugins already registered");
            return;
        }
        Set<Plugin> sets = Arrays.stream(pl).collect(Collectors.toSet());
        var count = 0;
        for (val p : sets) {
            if (p.withServerCreate()) {
                serverCreator = p;
                count++;
            }
        }
        if (count > 1) {
            throw new IllegalArgumentException("only one server create plugin can be used, found " + count);
        }
        sets.forEach(e -> plugins.put(e.getName(), e));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends Plugin> T getPlugin(String name) {
        return (T) plugins.get(name);
    }

    @Nullable
    public static <T extends Plugin> T getPluginSafe(@NotNull String name, Class<T> cls) {
        final Plugin p = plugins.get(name);
        return cls.isInstance(p) ? cls.cast(p) : null;
    }

    private static Supplier<Config> configurator = null;

    public static void configuration(@NotNull Supplier<Config> conf) {
        configurator = conf;
    }

    public static void loggerConfiguration(@NotNull Function<LogManager, InputStream> act) throws IOException {
        LogManager m = LogManager.getLogManager();
        InputStream is = act.apply(m);
        m.readConfiguration(is);
    }

    private static Routing buildRouting(@NotNull Config config) {
        Routing.Builder b = Routing.builder();
        if (beforeRegisterRouting != null) beforeRegisterRouting.accept(b);
        routeRegistry
            .forEach((n, a) -> {
                Service srv = a.apply(config);
                b.register(n, srv);
                logger.info("[ " + srv.getClass().getCanonicalName() + " ] be registered on " + n);
            });
        routeRegistry.clear();
        return b.build();
    }

    private static WebServer server = null;

    @Nullable
    public static WebServer getServer() {
        return server;
    }

    private static void initializationOfPlugin(boolean beforeServerStart, Config cfg) {
        plugins.values()
            .stream()
            .filter(it -> it.isBeforeStartServer() == beforeServerStart)
            .forEach(p -> {
                try {
                    p.initialize(cfg, server);
                    logger.info("initialized of " + p.getName());
                } catch (Exception e) {
                    logger.error("error on initialize of " + p.getName(), e);
                }
            });
    }


    private static void finalizationOfPlugins() {
        plugins.values()
            .forEach(v -> {
                try {
                    v.doOnFinalize();
                } catch (Exception e) {
                    logger.error("error on finalization plugin " + v.getName(), e);
                }
            });
    }

    private static String generateSocketInfo() {
        return (server.configuration().ssl() == null ? "http" : "https") +
            ":/" +
            (server.configuration().bindAddress() == null ? "/0.0.0.0" : server.configuration().bindAddress()) +
            ":" + server.port() + "/";
    }

    public static void start() {
        if (server != null) return; //do nothing when server already started
        final Config cfg;
        if (configurator != null) {
            cfg = configurator.get();
        } else {
            cfg = Config.create();
        }
        //sort
        plugins = plugins.values()
            .stream()
            .sorted((e1, e2) -> {
                Boolean state = e2.isBeforeType(e1.getType(), e1.getName());
                return state == null ? 0 : state ? -1 : 1;
            }).collect(Collectors.toMap(
                Plugin::getName,
                e -> e,
                (oldValue, newValue) -> oldValue,
                TreeMap::new
            ));
        // call cfg for all plugins
        plugins
            .values()
            .forEach(e -> e.onConfig(cfg));
        // create server
        val serverConfigBuilder = ServerConfiguration
            .builder(cfg.get("server"));


        val route = buildRouting(cfg);

        if (serverCreator != null) {
            server = serverCreator.createCustomerServer(cfg, serverConfigBuilder.build(), route);
        } else server = WebServer.create(serverConfigBuilder.build(), route);

        // initialize all plugin with before start server
        initializationOfPlugin(true, cfg);
        //check if server is started by some plugin
        if (server.isRunning()) {
            logger.warn("some plugin started current server,escape normal start procedure");
            initializationOfPlugin(false, cfg);
            logger.info("server is up! " + generateSocketInfo());
            server.whenShutdown().thenRun(() -> {
                finalizationOfPlugins();
                logger.info(" server [start by some plugin] is DOWN. Good bye!");
            });
            return;
        }
        //start server
        server.start()
            .thenAccept((ws) -> {
                initializationOfPlugin(false, cfg);
                logger.info("server is up! " + generateSocketInfo());
                ws.whenShutdown()
                    .thenRun(() -> {
                        finalizationOfPlugins();
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
