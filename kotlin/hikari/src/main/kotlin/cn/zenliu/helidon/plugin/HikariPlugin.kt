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
 *   @File: HikariPlugin.kt
 *   @Author:  lcz20@163.com
 *   @LastModified:  2020-04-26 16:27:48
 */

package cn.zenliu.helidon.plugin

import cn.zenliu.helidon.bootstrap.Plugin
import cn.zenliu.helidon.bootstrap.toProperties
import cn.zenliu.helidon.plugin.HikariPlugin.Companion
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.helidon.config.Config
import io.helidon.webserver.WebServer

typealias configurator = Companion.(HikariConfig) -> HikariDataSource?

interface HikariPlugin : Plugin {
	val datasource: HikariDataSource
	fun configuration(conf: configurator): HikariPlugin

	companion object : HikariPlugin {
		private const val NAME = "HikariPlugin"
		override val name: String = NAME
		override val beforeStartServer: Boolean = true
		private lateinit var ds: HikariDataSource
		override val datasource: HikariDataSource by lazy { ds }
		private var configurator: configurator? = { null }
		override fun configuration(conf: configurator): HikariPlugin {
			configurator = conf
			return this
		}

		override fun initialize(config: Config, server: WebServer) {
			if (configurator == null) return
			val prop = config
					.get("hikari")
					.takeIf { it.exists() }
					?.detach()
					?.toProperties()
					?: throw IllegalArgumentException(" invalid configuration of hikari ")
			val conf = HikariConfig(prop)
			ds = configurator?.invoke(this, conf) ?: HikariDataSource(conf)
			configurator = null

		}

		override fun doOnFinalize() {
			if (configurator == null) ds.close()
		}
	}

}