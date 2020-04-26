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
 *   @File: Bus.kt
 *   @Author:  lcz20@163.com
 *   @LastModified:  2020-04-26 22:53:11
 */

package cn.zenliu.helidon.bootstrap

import io.helidon.config.Config
import io.helidon.webserver.WebServer
import reactor.core.Disposable
import reactor.core.publisher.*
import java.lang.ref.SoftReference
import java.util.*
import kotlin.reflect.KClass

typealias ProcessorConfigurator = (FluxProcessor<Bus.Event, Bus.Event>) -> FluxProcessor<Bus.Event, Bus.Event>?
typealias SinkConfigurator = (FluxSink<Bus.Event>) -> FluxSink<Bus.Event>?
typealias FluxConfigurator = (Flux<Bus.Event>) -> Flux<Bus.Event>?
typealias OnErrorConsumer = (Throwable, Any) -> Unit

interface Bus : Plugin {
    interface Event

    fun configuration(
            processorConf: ProcessorConfigurator,
            sinkConf: SinkConfigurator,
            fluxConf: FluxConfigurator,
            onErrorConsumer: OnErrorConsumer
    )

    fun <E : Event> subscribe(eventType: KClass<E>, consumer: (E) -> Unit): Long
    fun <E : Event> unsubscribe(identity: Long)
    fun publish(e: Event)

    companion object FluxBusImpl : Bus {
        private const val NAME = "FluxBusPlugin"
        override val name: String = NAME
        override val beforeStartServer: Boolean = true

        private var procConfigurator: ProcessorConfigurator? = { it }
        private var sinkConfigurator: SinkConfigurator? = { it }
        private var fluxConfigurator: FluxConfigurator? = { it }
        private var onErrorConsumer: OnErrorConsumer? = { _, _ -> Unit }
        override fun configuration(processorConf: ProcessorConfigurator,
                                   sinkConf: SinkConfigurator,
                                   fluxConf: FluxConfigurator,
                                   onErrorConsumer: OnErrorConsumer) {
            procConfigurator = processorConf
            sinkConfigurator = sinkConf
            fluxConfigurator = fluxConf
            this.onErrorConsumer = onErrorConsumer
        }

        private lateinit var sink: FluxSink<Event>
        private lateinit var flux: Flux<Event>
        override fun initialize(config: Config, server: WebServer) {
            if (procConfigurator == null || sinkConfigurator == null || fluxConfigurator == null) return
            val maxBuffer = config["bus.maxBuffer"].asInt().orElse(10)
            val proc: FluxProcessor<Event, Event> =
                    when (config["bus.processor"].asString().orElse("emitter")) {
                        "replay" -> {
                            val replyCount = config["bus.replayCount"].asInt().orElse(1)
                            ReplayProcessor.create(replyCount)
                        }
                        "direct" -> DirectProcessor.create()
                        "unicast" -> UnicastProcessor.create()
                        else -> EmitterProcessor.create()
                    }
            val fProc = procConfigurator!!.invoke(proc) ?: proc
            sink = fProc.sink(FluxSink.OverflowStrategy.BUFFER).let {
                sinkConfigurator?.invoke(it) ?: it
            }


            flux = (fProc.let { fluxConfigurator?.invoke(it) } ?: fProc)
                    .onBackpressureBuffer(maxBuffer)
                    .onErrorContinue(onErrorConsumer)
                    .share()
            procConfigurator = null
            sinkConfigurator = null
            fluxConfigurator = null
        }

        override fun doOnFinalize() {
            //nothing to do
        }

        private val registry = mutableMapOf<Long, SoftReference<Disposable>>()
        override fun <E : Event> subscribe(eventType: KClass<E>, consumer: (E) -> Unit): Long {
            purify()
            val code = consumer.hashCode() + System.currentTimeMillis()
            if (registry.containsKey(code)) return -1
            val disposable = flux.filter { eventType.isInstance(it) }.share().subscribe {
                @Suppress("UNCHECKED_CAST")
                consumer.invoke(it as E)
            }
            registry[code] = SoftReference(disposable)
            return code
        }

        @Synchronized
        private fun purify() {
            registry
                    .filterNot { it.value.get() == null }
                    .keys
                    .forEach {
                        registry.remove(it)
                    }
        }

        override fun <E : Event> unsubscribe(identity: Long) {
            registry[identity]?.get()?.takeIf { !it.isDisposed }?.dispose()
            registry.remove(identity)
        }

        override fun publish(e: Event) {
            sink.next(e)
        }

        val spi by lazy {
            ServiceLoader.load(Bus::class.java).iterator()
                    .takeIf { it.hasNext() }
                    ?.next()
        }
    }
}