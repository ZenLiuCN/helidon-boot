import cn.zenliu.helidon.plugin.BouncyCastleTlsPlugin;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

import java.io.IOException;

import static cn.zenliu.helidon.bootstrap.Boot.*;


public class Starter {
    public static void main(String[] args) throws IOException {
        //config logger for helidon
        loggerConfiguration(m -> m.getClass().getResourceAsStream("/logback.xml"));
        //register route service
        routes(
            makePair("/api", EchoApi::new)
        );
        //config plugins
        plugin(
            BouncyCastleTlsPlugin.getInstance()
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

    static class EchoApi implements Service {
        public EchoApi(Config config) {
        }

        @Override
        public void update(Routing.Rules rules) {
            rules.get("/echo", (rq, rs) -> rs.send("hello"));
        }
    }
}
