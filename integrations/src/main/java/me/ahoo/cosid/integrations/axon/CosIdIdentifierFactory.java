/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package me.ahoo.cosid.integrations.axon;

import me.ahoo.cosid.integrations.TextIds;
import me.ahoo.cosid.runtime.CosIdRuntime;

import org.axonframework.common.IdentifierFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Axon's {@link IdentifierFactory} on a runtime generator (07-runtime-and-spring.md §5).
 *
 * <p><b>The one static state of CosId.</b> Axon loads its identifier factory through {@link java.util.ServiceLoader}
 * (this module's {@code META-INF/services} entry) into a JVM-wide static, so the factory it creates cannot be given a
 * runtime. It uses the runtime {@linkplain #bind(CosIdRuntime, String) bound} most recently and not yet unbound instead:
 * the Spring Boot auto-configuration binds the context's runtime once it is started and unbinds it before it is closed;
 * other code binds with {@code try (var binding = CosIdIdentifierFactory.bind(runtime, null)) { ... }}. Closing one of
 * several runtimes unbinds only its own binding. With nothing bound, the factory falls back to random UUIDs, Axon's own
 * default, and logs a warning once: an application that has this module for another integration keeps Axon's
 * identifiers.
 *
 * <p>The generator is the one a binding names, else the one of the {@link #ID_KEY} system property, else
 * {@code default}. A factory created with a runtime ignores the bindings.
 */
public class CosIdIdentifierFactory extends IdentifierFactory {
    /** The system property of the generator name. */
    public static final String ID_KEY = "cosid.axon";
    /** The generator when neither a binding nor the system property names one. */
    public static final String DEFAULT_GENERATOR = "default";

    private static final Logger log = LoggerFactory.getLogger(CosIdIdentifierFactory.class);
    private static final Deque<Binding> BINDINGS = new ConcurrentLinkedDeque<>();
    private static final AtomicBoolean UNBOUND_WARNED = new AtomicBoolean();
    private static final AtomicBoolean MULTIPLE_WARNED = new AtomicBoolean();

    private final TextIds ids;

    /** The factory {@link java.util.ServiceLoader} creates: the ids of the bound runtime. */
    public CosIdIdentifierFactory() {
        this.ids = null;
    }

    /**
     * A factory of one runtime, regardless of the bindings.
     *
     * @param runtime   the runtime
     * @param generator the generator name; blank or {@code null} for the {@link #ID_KEY} system property's
     */
    public CosIdIdentifierFactory(CosIdRuntime runtime, String generator) {
        this.ids = TextIds.of(runtime, generatorName(generator));
    }

    private static String generatorName(String generator) {
        return generator != null && !generator.isBlank() ? generator : System.getProperty(ID_KEY, DEFAULT_GENERATOR);
    }

    /**
     * Binds a runtime for the factory {@link java.util.ServiceLoader} creates; the most recent binding wins.
     *
     * @param runtime   the runtime
     * @param generator the generator name; blank or {@code null} for the {@link #ID_KEY} system property's
     * @return the binding; close it to unbind
     */
    public static Binding bind(CosIdRuntime runtime, String generator) {
        Binding binding = new Binding(TextIds.of(Objects.requireNonNull(runtime, "runtime"), generatorName(generator)), runtime);
        BINDINGS.addFirst(binding);
        if (BINDINGS.stream().map(Binding::runtime).distinct().count() > 1 && MULTIPLE_WARNED.compareAndSet(false, true)) {
            log.warn("Several CosIdRuntimes are bound to CosIdIdentifierFactory (for example, several Spring contexts): Axon's "
                + "identifiers come from the most recent one. Set an own IdentifierFactory to choose.");
        }
        return binding;
    }

    /**
     * Removes exactly this binding, leaving the others in place.
     *
     * @param binding the binding
     * @return {@code true} if it was bound, {@code false} if it was already unbound
     */
    public static boolean unbind(Binding binding) {
        // Binding has identity equality: this removes this one only.
        return BINDINGS.removeFirstOccurrence(binding);
    }

    /**
     * The binding in use.
     *
     * @return the most recent binding, or {@code null} without one
     */
    static Binding current() {
        return BINDINGS.peekFirst();
    }

    @Override
    public String generateIdentifier() {
        if (ids != null) {
            return ids.get();
        }
        Binding bound = BINDINGS.peekFirst();
        if (bound == null) {
            if (UNBOUND_WARNED.compareAndSet(false, true)) {
                log.warn("No CosIdRuntime is bound to CosIdIdentifierFactory: Axon's identifiers are random UUIDs. "
                    + "Bind one with CosIdIdentifierFactory.bind(runtime, generator), as the Spring Boot auto-configuration does.");
            }
            return UUID.randomUUID().toString();
        }
        return bound.ids.get();
    }

    /** A runtime bound by {@link #bind(CosIdRuntime, String)}; closing it unbinds it. */
    public static final class Binding implements AutoCloseable {
        private final TextIds ids;
        private final CosIdRuntime runtime;

        private Binding(TextIds ids, CosIdRuntime runtime) {
            this.ids = ids;
            this.runtime = runtime;
        }

        /**
         * The runtime.
         *
         * @return the runtime
         */
        public CosIdRuntime runtime() {
            return runtime;
        }

        /**
         * The generator name.
         *
         * @return the name
         */
        public String generator() {
            return ids.generator();
        }

        @Override
        public void close() {
            unbind(this);
        }
    }
}
