package cn.zenliu.helidon.bootstrap

import io.helidon.config.Config
import io.helidon.webserver.Routing
import io.helidon.webserver.ServerConfiguration
import io.helidon.webserver.Service
import io.helidon.webserver.WebServer
import io.netty.util.internal.logging.Slf4JLoggerFactory
import java.io.InputStream
import java.util.logging.LogManager


object Boot {
	@JvmStatic
	private val logger = Slf4JLoggerFactory.getInstance(this::class.java)


	@JvmStatic
	private val routeRegistry: MutableMap<String, (Config) -> Service> = mutableMapOf()

	@JvmStatic
	fun routes(vararg routes: Pair<String, (Config) -> Service>) {
		val newRoutes = routes.toMap()
		if (routeRegistry.keys.any { newRoutes.keys.contains(it) }) throw IllegalStateException("some route is already" +
				" registered")
		routeRegistry.putAll(newRoutes)
	}

	@JvmStatic
	infix fun String.binding(gen: (Config) -> Service) = this to gen

	@JvmStatic
	private var beforeRegisterRouting: (Routing.Builder.() -> Unit)? = null

	@JvmStatic
	fun extending(builder: Routing.Builder.() -> Unit) {
		beforeRegisterRouting = builder
	}

	@JvmStatic
	private val plugins = mutableMapOf<String, Plugin>()

	@Suppress("UNCHECKED_CAST")
	fun <T : Plugin> pluginOf(name: String): T? = plugins[name] as? T

	/**
	 * call only allow once
	 * @param pl Array<out Plugin>
	 * @return Unit
	 */
	@JvmStatic
	fun plugin(vararg pl: Plugin) {
		if (plugins.isNotEmpty()) {
			logger.warn("plugins already registered")
			return
		}
		plugins.putAll(pl.toSet().map { it.name to it })
	}

	@JvmStatic
	private var configurator: (Boot.() -> Config?)? = null

	@JvmStatic
	fun configuration(conf: Boot.() -> Config?) {
		configurator = conf
	}

	@JvmStatic
	fun loggerConfiguration(act: LogManager.() -> InputStream) {
		LogManager.getLogManager().apply {
			readConfiguration(act(this))
		}
	}

	@JvmStatic
	private fun buildRouting(config: Config) = Routing
			.builder()
			.apply {
				beforeRegisterRouting?.invoke(this)
				routeRegistry.forEach { (t, u) ->
					val service = u(config)
					register(t, service)
					logger.info("register api service [${service::class.qualifiedName}] on $t ")
				}
				routeRegistry.clear()//save memory
			}
			.build()

	@JvmStatic
	lateinit var server: WebServer
		private set

	@JvmStatic
	fun start() {
		if (this::server.isInitialized) return
		val conf = configurator?.invoke(this) ?: Config.create()

		server = WebServer
				.create(
						ServerConfiguration.create(conf["server"]),
						buildRouting(conf))

		plugins.forEach { (_, u) -> u.onConfig(conf) }

		plugins
				.filter { it.value.beforeStartServer }
				.forEach { (_, u) -> u.initialize(conf, server) }
		if (server.isRunning) {
			logger.warn("some plugin started current server,escape normal start procedure")
			server.whenShutdown().thenRun {
				plugins.forEach { (name, u) ->
					kotlin.runCatching {
						u.doOnFinalize()
					}.onFailure {
						logger.error("error on finalization plugin $name", it)
					}
				}
				logger.info(" server [which start by some plugin] is DOWN. Good bye!")
			}
			return
		}
		server.start()
				.thenAccept { ws: WebServer ->
					logger.info("WEB server is up! http://0.0.0.0:" + ws.port() + "/")
					plugins.filter { !it.value.beforeStartServer }.forEach { (_, u) -> u.initialize(conf, server) }
					ws.whenShutdown()
							.thenRun {
								plugins.forEach { (name, u) ->
									kotlin.runCatching {
										u.doOnFinalize()
									}.onFailure {
										logger.error("error on finalization plugin $name", it)
									}
								}
								logger.info(" server is DOWN. Good bye!")
							}
				}
				.exceptionally { t: Throwable ->
					System.err.println("Startup failed: " + t.message)
					t.printStackTrace(System.err)
					null
				}
	}

	inline fun bootstrap(act: Boot.() -> Unit) {
		act(this)
		start()
	}
}