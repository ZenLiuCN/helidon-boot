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
 *   @Module: hikari
 *   @File: HikariPluginTest.kt
 *   @Author:  lcz20@163.com
 *   @LastModified:  2020-04-26 16:27:48
 */

package cn.zenliu.helidon.plugin


import cn.zenliu.helidon.bootstrap.toProperties
import io.helidon.config.Config
import io.helidon.config.ConfigSources.classpath
import org.junit.jupiter.api.Test

internal class HikariPluginTest {
	@Test
	fun testProperties() {
		val cfg = Config
				.create(classpath("application.conf").mediaType("application/hocon"))
				.get("hikari").detach().toProperties()

		println(cfg)
	}
}