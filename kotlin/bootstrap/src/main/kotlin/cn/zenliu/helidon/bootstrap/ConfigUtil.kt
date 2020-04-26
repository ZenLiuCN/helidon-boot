@file:JvmName("ConfigUtil")

package cn.zenliu.helidon.bootstrap

import io.helidon.config.Config
import java.util.*

fun Config.toProperties(): Properties? = asMap()
		.takeIf { it.isPresent }
		?.get()
		?.let {
			Properties().apply {
				it.forEach { (t, u) ->
					setProperty(t, u)
				}
			}
		}