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
 *   @Module: hikari-java
 *   @File: HikariPlugin.java
 *   @Author:  lcz20@163.com
 *   @LastModified:  2020-04-26 22:17:04
 */

package cn.zenliu.helidon.plugin;

import cn.zenliu.helidon.bootstrap.ConfigUtil;
import cn.zenliu.helidon.bootstrap.Plugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;

import java.util.Iterator;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.function.Function;

public interface HikariPlugin extends Plugin {
    HikariDataSource getDatasource() throws IllegalStateException;

    HikariPlugin configuration(Function<HikariConfig, HikariDataSource> conf);


   final class HikariPluginImpl implements HikariPlugin {
       private static final String NAME = "HikariPlugin";

       private HikariPluginImpl() {
       }

       @Override
       public String getName() {
           return NAME;
       }

        @Override
        public boolean isBeforeStartServer() {
            return true;
        }

        private static Function<HikariConfig, HikariDataSource> configurator = e -> null;

        @Override
        public HikariDataSource getDatasource() throws IllegalStateException {
            if (configurator != null) throw new IllegalStateException("HikariPlugin not install");
            return ds;
        }

        public HikariPlugin configuration(Function<HikariConfig, HikariDataSource> conf) {
            configurator = conf;
            return this;
        }

        private static HikariDataSource ds;

        @Override
        public void initialize(Config config, WebServer server) {
            if (configurator == null) return;
            Properties prop = ConfigUtil.toProperties(config.get("hikari").detach())
                    .orElseThrow(() -> new IllegalArgumentException("invalid configuration of hikari"));
            final HikariConfig conf = new HikariConfig(prop);
            HikariDataSource ds = configurator.apply(conf);
            if (ds == null) ds = new HikariDataSource(conf);
        }

        @Override
        public void onConfig(Config config) {
            //do nothing
        }

        @Override
        public void doOnFinalize() {
            if (ds != null) ds.close();
        }

       private static final class Holder {
           private static final HikariPlugin instance = new HikariPluginImpl();
           private static volatile HikariPlugin spi;

           static {
               if (spi == null) {
                   Iterator<HikariPlugin> it = ServiceLoader.load(HikariPlugin.class).iterator();
                   if (it.hasNext()) {
                       spi = it.next();
                   }
               }
            }
        }

    }

    static HikariPlugin getInstance() {
        return HikariPluginImpl.Holder.instance;
    }

    /**
     * new SPI Loader helper
     *
     * @return HikariPlugin or null
     */
    static HikariPlugin getSPIInstance() {
        return HikariPluginImpl.Holder.spi;
    }
}
