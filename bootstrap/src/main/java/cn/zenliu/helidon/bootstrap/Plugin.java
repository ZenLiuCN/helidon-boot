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
 *   @File: Plugin.java
 *   @Author:  lcz20@163.com
 *   @LastModified:  2020-04-27 13:35:30
 */

package cn.zenliu.helidon.bootstrap;

import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Plugin {
    enum PluginType {
        BY_NAME,
        EVENT,
        CACHE,
        DATASOURCE
    }

    @NotNull String getName();

    @Nullable Boolean isBeforeType(@NotNull PluginType type, String name);

    @NotNull PluginType getType();

    boolean isBeforeStartServer();

    void initialize(Config config, WebServer server);

    void onConfig(Config config);

    void doOnFinalize();
}
