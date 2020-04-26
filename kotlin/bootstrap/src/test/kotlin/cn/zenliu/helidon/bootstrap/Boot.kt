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
 *   @File: Boot.kt
 *   @Author:  lcz20@163.com
 *   @LastModified:  2020-04-26 16:27:48
 */

package cn.zenliu.helidon.bootstrap

import cn.zenliu.helidon.bootstrap.Boot.bootstrap
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.helidon.config.Config
import io.helidon.health.HealthSupport
import io.helidon.media.jsonp.server.JsonSupport
import io.helidon.metrics.MetricsSupport
import io.helidon.webserver.Handler
import io.helidon.webserver.Routing.Rules
import io.helidon.webserver.Service
import io.helidon.webserver.WebServer
import javax.sql.DataSource

fun main() {
	bootstrap {
		//configure logger from stream of configuration
		loggerConfiguration {
			this::class.java.getResourceAsStream("/logback.xml")
		}
		//register web service
		routes(
				"/api" binding { EchoApi(it) }
		)
		//install plugins
		plugin(
				HikariPlugin
		)
		//extend register of support
		extending {
			register(JsonSupport.create())
			register(MetricsSupport.create())
			register(HealthSupport.create())
		}
	}
	//we check hikari status
	Boot.pluginOf<HikariPlugin>(HikariPlugin.name)!!.apply { println(this.dataSource) }

}

object HikariPlugin : Plugin {
	override val name: String = "HikariCP"
	override val beforeStartServer = true
	private lateinit var ds: HikariDataSource
	val dataSource: DataSource by lazy { ds }
	override fun initialize(config: Config, server: WebServer) {
		ds = HikariDataSource(HikariConfig().apply {
			jdbcUrl = config["hikari.uri"].asString().get() ?: throw IllegalStateException("hikari not config")
		})
	}

	override fun doOnFinalize() {
		ds.close()
	}
}

class EchoApi(config: Config) : Service {
	override fun update(r: Rules) {
		r.get("/echo", Handler { _, rs ->
			rs.send("hello")
		})
	}
}