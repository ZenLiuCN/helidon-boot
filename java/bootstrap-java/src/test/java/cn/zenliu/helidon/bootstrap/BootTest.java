package cn.zenliu.helidon.bootstrap;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import javax.sql.DataSource;
import java.io.IOException;

import static cn.zenliu.helidon.bootstrap.Boot.*;


class BootTest {
    public static void main(String[] args) throws IOException {
        //config logger for helidon
        loggerConfiguration(m -> m.getClass().getResourceAsStream("/logback.xml"));
        //register route service
        routes(
                makePair("/api", EchoApi::new)
        );
        //config plugins
        plugin(
                new HikariPlugin()
        );
        //extend routing
        extending(b -> {
            b.register(JsonSupport.create());
            b.register(HealthSupport.create());
            b.register(MetricsSupport.create());
        });
        //start server
        start();
    }

    static class HikariPlugin implements Plugin {
        public static final String PluginName = "HikariPlugin";
        private static volatile HikariDataSource ds = null;

        public static DataSource getDatasource() {
            return ds;
        }

        @Override
        public String getName() {
            return PluginName;
        }

        @Override
        public boolean isBeforeStartServer() {
            return true;
        }

        @Override
        public void initialize(Config config, WebServer server) {
            HikariConfig conf = new HikariConfig();
            String jdbc = config.get("hikari.uri").asString().get();
            if (jdbc == null) {
                throw new IllegalStateException("hikari not config");
            }
            conf.setJdbcUrl(jdbc);
            ds = new HikariDataSource(conf);
        }

        @Override
        public void doOnFinalize() {
            if (ds != null && ds.isRunning()) ds.close();
        }
    }

    static class EchoApi implements Service {
        public EchoApi(Config config) {
        }

        @Override
        public void update(Routing.Rules rules) {
            rules.get("/echo", (rq, rs) -> rs.send("hello"));
        }
    }
}