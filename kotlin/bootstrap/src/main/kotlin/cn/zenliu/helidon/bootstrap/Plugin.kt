package cn.zenliu.helidon.bootstrap

import io.helidon.config.Config
import io.helidon.webserver.WebServer


interface Plugin {
	val name: String
	val beforeStartServer: Boolean
	fun initialize(config: Config, server: WebServer)
	fun doOnFinalize()
	open fun onConfig(config: Config) {
		//default do nothing
	}
}