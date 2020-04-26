package cn.zenliu.helidon.bootstrap;

import io.helidon.config.Config;
import io.helidon.webserver.WebServer;

public interface Plugin {
    String getName();
    boolean isBeforeStartServer();
    void initialize(Config config, WebServer server);

    void onConfig(Config config);

    void doOnFinalize();
}
