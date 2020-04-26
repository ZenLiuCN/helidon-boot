package cn.zenliu.helidon.bootstrap;

import io.helidon.config.Config;

import java.util.Optional;
import java.util.Properties;

public final class ConfigUtil {
    private ConfigUtil() {
    }

    /**
     * convert Config node to Properties
     *
     * @param receiver target config node
     * @return Optional Properties
     */
    public static Optional<Properties> toProperties(Config receiver) {
        return receiver.asMap().asOptional().map(e -> {
            Properties prop = new Properties();
            e.forEach(prop::setProperty);
            return prop;
        });
    }
}
