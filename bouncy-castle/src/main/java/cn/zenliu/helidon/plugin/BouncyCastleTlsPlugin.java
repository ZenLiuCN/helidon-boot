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
 *   @Module: helidon-boot
 *   @File: HikariPlugin.java
 *   @Author:  lcz20@163.com
 *   @LastModified:  2020-04-27 13:35:30
 */

package cn.zenliu.helidon.plugin;


import cn.zenliu.helidon.bootstrap.Plugin;
import io.helidon.config.Config;
import io.helidon.webserver.NettyTLSWebServer;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.jetbrains.annotations.NotNull;

import java.security.Security;
import java.util.Iterator;
import java.util.ServiceLoader;


public interface BouncyCastleTlsPlugin extends Plugin {

    @Slf4j
    final class BouncyCastleTlsPluginImpl implements BouncyCastleTlsPlugin {


        private static final String NAME = "BouncyCastleTlsPlugin";

        private BouncyCastleTlsPluginImpl() {
        }

        //region Impl of plugin
        @Override
        public @NotNull String getName() {
            return NAME;
        }

        @Override
        public Boolean isBeforeType(@NotNull PluginType type, String name) {
            return null;
        }

        @Override
        public @NotNull PluginType getType() {
            return PluginType.DATASOURCE;
        }

        @Override
        public boolean withServerCreate() {
            return true;
        }

        @Override
        public boolean isBeforeStartServer() {
            return true;
        }

        @Override
        public WebServer createCustomerServer(Config config, ServerConfiguration configuration, Routing defaultRouting) {
            return NettyTLSWebServer.build(
                config.get("server"),
                configuration,
                defaultRouting
            );
        }

        @Override
        public void initialize(Config config, WebServer server) {

        }

        @Override
        public void onConfig(Config config) {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.insertProviderAt(new BouncyCastleProvider(), 1);
            }
            if (Security.getProvider(BouncyCastleJsseProvider.PROVIDER_NAME) == null) {
                Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);
            }
            Security.setProperty("ssl.KeyManagerFactory.algorithm", "PKIX ");
            Security.setProperty("ssl.TrustManagerFactory.algorithm", "PKIX ");

        }

        @Override
        public void doOnFinalize() {

        }
        //endregion

        private static final class Holder {
            private static final BouncyCastleTlsPlugin instance = new BouncyCastleTlsPluginImpl();
            private static volatile BouncyCastleTlsPlugin spi;

            static {
                if (spi == null) {
                    Iterator<BouncyCastleTlsPlugin> it = ServiceLoader.load(BouncyCastleTlsPlugin.class).iterator();
                    if (it.hasNext()) {
                        spi = it.next();
                    }
                }
            }
        }

    }

    static BouncyCastleTlsPlugin getInstance() {
        return BouncyCastleTlsPluginImpl.Holder.instance;
    }

    /**
     * new SPI Loader helper
     *
     * @return HikariPlugin or null
     */
    static BouncyCastleTlsPlugin getSPIInstance() {
        return BouncyCastleTlsPluginImpl.Holder.spi;
    }
}
