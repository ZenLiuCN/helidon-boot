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