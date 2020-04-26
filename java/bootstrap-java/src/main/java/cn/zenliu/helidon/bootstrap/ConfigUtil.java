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
 *   @Module: bootstrap-java
 *   @File: ConfigUtil.java
 *   @Author:  lcz20@163.com
 *   @LastModified:  2020-04-26 16:27:48
 */

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
