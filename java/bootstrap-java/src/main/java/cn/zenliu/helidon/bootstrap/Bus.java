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
 *   @File: Bus.java
 *   @Author:  lcz20@163.com
 *   @LastModified:  2020-04-26 22:17:04
 */

package cn.zenliu.helidon.bootstrap;

import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import reactor.core.Disposable;
import reactor.core.publisher.*;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public interface Bus extends Plugin {
    /**
     * if use this to hook generate bus instance must call before boot start
     *
     * @param processorConf    function to hook on inner processor
     * @param sinkConf         function to hook on inner sink
     * @param fluxConf         function to hook on inner flux
     * @param onErrorProcessor function to handle error on some error happens
     * @return self
     */
    Bus configuration(
            Function<FluxProcessor<Event, Event>, FluxProcessor<Event, Event>> processorConf,
            Function<FluxSink<Event>, FluxSink<Event>> sinkConf,
            Function<Flux<Event>, Flux<Event>> fluxConf,
            BiConsumer<Throwable, Object> onErrorProcessor
    );

    /**
     * @param eventClz target event
     * @param consumer function
     * @param <E>      subtype of event
     * @return an Disposable
     */
    <E extends Event> Disposable subscribe(Class<E> eventClz, Consumer<E> consumer);

    void publish(Event event);

    interface Event {
    }

    final class FluxBusImpl implements Bus {
        private static final String NAME = "EventBusPlugin";

        private static Flux<Event> flux;
        private static FluxSink<Event> sink;
        private static Function<FluxProcessor<Event, Event>, FluxProcessor<Event, Event>>
                processorConfigurator = it -> it;
        private static Function<Flux<Event>, Flux<Event>>
                fluxConfigurator = it -> it;
        private static Function<FluxSink<Event>, FluxSink<Event>>
                sinkConfigurator = it -> it;
        private static BiConsumer<Throwable, Object> onError = (t, e) -> {
        };

        @Override
        public Bus configuration(
                Function<FluxProcessor<Event, Event>, FluxProcessor<Event, Event>> processorConf,
                Function<FluxSink<Event>, FluxSink<Event>> sinkConf,
                Function<Flux<Event>, Flux<Event>> fluxConf,
                BiConsumer<Throwable, Object> onErrorProcessor
        ) {
            processorConfigurator = processorConf;
            sinkConfigurator = sinkConf;
            fluxConfigurator = fluxConf;
            onError = onErrorProcessor;
            return this;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public boolean isBeforeStartServer() {
            return true;
        }

        @Override
        public void initialize(Config config, WebServer server) {
            if (processorConfigurator == null || sinkConfigurator == null || fluxConfigurator == null)
                return;

            int maxBuffer = config.get("bus.maxBuffer").asInt().orElse(10);
            String type = config.get("bus.processor").asString().orElse("emitter");
            FluxProcessor<Event, Event> proc;
            switch (type) {
                case "replay":
                    int replyCount = config.get("bus.replayCount").asInt().orElse(1);
                    proc = ReplayProcessor.create(replyCount);
                    break;
                case "direct":
                    proc = DirectProcessor.create();
                    break;
                case "unicast":
                    proc = UnicastProcessor.create();
                    break;
                default:
                    proc = EmitterProcessor.create();
            }
            FluxProcessor<Event, Event> fProc = processorConfigurator.apply(proc);
            if (fProc == null) {
                fProc = proc;
            }
            FluxSink<Event> fSink = fProc.sink(FluxSink.OverflowStrategy.BUFFER);

            fSink = sinkConfigurator.apply(fSink);
            if (fSink == null) {
                fSink = fProc.sink(FluxSink.OverflowStrategy.BUFFER);
            }
            sink = fSink;
            Flux<Event> fFlux = fProc;
            fFlux = fluxConfigurator.apply(fFlux);
            if (fFlux == null) {
                fFlux = fProc;
            }
            flux = fFlux.onBackpressureBuffer(maxBuffer).share().onErrorContinue(onError);
            processorConfigurator = null;
            sinkConfigurator = null;
            fluxConfigurator = null;
        }

        @Override
        public void onConfig(Config config) {
            //do nothing
        }

        @Override
        public void doOnFinalize() {
        }

        @SuppressWarnings("unchecked")
        @Override
        public <E extends Event> Disposable subscribe(Class<E> eventClz, Consumer<E> consumer) {
            //does this share needed?
            return flux
                    .filter(eventClz::isInstance)
                    .share()
                    .subscribe(c -> consumer.accept((E) c));
        }

        @Override
        public void publish(Event event) {
            sink.next(event);
        }

        private final static class Holder {
            private static final Bus instance = new FluxBusImpl();
            private static volatile Bus spi;

            static {
                if (spi == null) {
                    Iterator<Bus> it = ServiceLoader.load(Bus.class).iterator();
                    if (it.hasNext()) {
                        spi = it.next();
                    }
                }
            }
        }
    }

    static Bus getInstance() {
        return FluxBusImpl.Holder.instance;
    }

    static Bus getSPIInstance() {
        return FluxBusImpl.Holder.spi;
    }
}
