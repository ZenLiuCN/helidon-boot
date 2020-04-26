package cn.zenliu.helidon.plugin

import cn.zenliu.helidon.bootstrap.Plugin
import cn.zenliu.helidon.bootstrap.toProperties
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.helidon.config.Config
import io.helidon.webserver.WebServer

class HikariPlugin {
	companion object : Plugin {
		const val NAME = "HikariPlugin"
		override val name: String = NAME
		override val beforeStartServer: Boolean = true
		private lateinit var ds: HikariDataSource
		val datasource by lazy { ds }
		private var configurator: (Companion.(HikariConfig) -> HikariDataSource?)? = { null }

		fun configuration(conf: Companion.(HikariConfig) -> HikariDataSource?) {
			configurator = conf
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