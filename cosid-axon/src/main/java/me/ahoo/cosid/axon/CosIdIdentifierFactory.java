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

package me.ahoo.cosid.axon;

import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.provider.NotFoundIdGeneratorException;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.common.IdentifierFactory;
import org.jspecify.annotations.Nullable;

import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CosId Identifier Factory .
 *
 * <p>Axon discovers {@link IdentifierFactory} through {@link java.util.ServiceLoader} and keeps it in a JVM-wide
 * static, so the no-arg instance cannot receive a provider by injection. It resolves generators from the provider
 * most recently registered with {@link #bind(IdGeneratorProvider)} and not yet unbound (the Spring Boot starter binds
 * the application context's provider), falling back to {@link DefaultIdGeneratorProvider#INSTANCE} when none is bound.
 * Closing one context only unbinds its own provider, so other live contexts keep theirs.
 *
 * @author ahoo wang
 */
@Slf4j
public class CosIdIdentifierFactory extends IdentifierFactory {
    public static final String ID_KEY = "cosid.axon";

    private static final Deque<Binding> BOUND_PROVIDERS = new ConcurrentLinkedDeque<>();
    private static final AtomicBoolean MULTIPLE_PROVIDERS_WARNED = new AtomicBoolean();
    private static final AtomicBoolean MULTIPLE_NAMES_WARNED = new AtomicBoolean();

    private final String generatorName;
    private final IdGeneratorProvider idGeneratorProvider;
    /**
     * Whether a binding's generator name takes the place of {@link #generatorName}, which then is the fallback.
     */
    private final boolean boundName;

    /**
     * Creates a factory that resolves from the bound provider, or {@link DefaultIdGeneratorProvider#INSTANCE}: the
     * generator named by the binding, else by the {@link #ID_KEY} system property, else {@link IdGeneratorProvider#SHARE}.
     */
    public CosIdIdentifierFactory() {
        this(System.getProperty(ID_KEY, IdGeneratorProvider.SHARE), null, true);
    }

    /**
     * Creates a factory that always resolves from the given provider.
     *
     * @param idGeneratorProvider the provider to resolve the generator from
     */
    public CosIdIdentifierFactory(IdGeneratorProvider idGeneratorProvider) {
        this(System.getProperty(ID_KEY, IdGeneratorProvider.SHARE), idGeneratorProvider);
    }

    /**
     * Creates a factory that resolves the named generator from the given provider.
     *
     * @param generatorName       the generator name
     * @param idGeneratorProvider the provider to resolve from, or {@code null} to use the bound provider
     */
    public CosIdIdentifierFactory(String generatorName, IdGeneratorProvider idGeneratorProvider) {
        this(generatorName, idGeneratorProvider, false);
    }
    
    private CosIdIdentifierFactory(String generatorName, IdGeneratorProvider idGeneratorProvider, boolean boundName) {
        this.generatorName = generatorName;
        this.idGeneratorProvider = idGeneratorProvider;
        this.boundName = boundName;
    }
    
    /**
     * A binding made by {@link #bind(IdGeneratorProvider, String)}: a provider and the generator name it was bound with,
     * if any. Pass it to {@link #unbind(Binding)} to remove exactly this binding, even when other bindings share its
     * provider (without {@code cosid.provider.isolated}, every Spring context binds the same provider).
     *
     * @since 4.1.0
     */
    public static final class Binding {
        private final IdGeneratorProvider provider;
        private final @Nullable String generatorName;

        private Binding(IdGeneratorProvider provider, @Nullable String generatorName) {
            this.provider = provider;
            this.generatorName = generatorName;
        }

        public IdGeneratorProvider provider() {
            return provider;
        }

        public @Nullable String generatorName() {
            return generatorName;
        }
    }

    /**
     * Binds a provider for factories created without an explicit provider; the most recent binding wins.
     *
     * @param idGeneratorProvider the provider to bind
     */
    public static void bind(IdGeneratorProvider idGeneratorProvider) {
        bind(idGeneratorProvider, null);
    }

    /**
     * Binds a provider and the generator that factories created without arguments use, in place of the {@link #ID_KEY}
     * system property; the most recent binding wins.
     *
     * @param idGeneratorProvider the provider to bind
     * @param generatorName       the generator name, {@code null} for the system property's
     * @return the binding, to pass to {@link #unbind(Binding)}
     * @since 4.1.0
     */
    public static Binding bind(IdGeneratorProvider idGeneratorProvider, @Nullable String generatorName) {
        Binding binding = new Binding(idGeneratorProvider, generatorName);
        BOUND_PROVIDERS.addFirst(binding);
        long distinct = BOUND_PROVIDERS.stream().map(Binding::provider).distinct().count();
        if (distinct > 1 && MULTIPLE_PROVIDERS_WARNED.compareAndSet(false, true) && log.isWarnEnabled()) {
            log.warn("{} different IdGeneratorProviders are bound to CosIdIdentifierFactory (for example, several Spring contexts): "
                + "factories created without a provider use the most recent one. Create the factory with "
                + "new CosIdIdentifierFactory(provider) to choose.", distinct);
        }
        long names = BOUND_PROVIDERS.stream().filter(bound -> bound.provider() == idGeneratorProvider)
            .map(bound -> Objects.toString(bound.generatorName(), "")).distinct().count();
        if (names > 1 && MULTIPLE_NAMES_WARNED.compareAndSet(false, true) && log.isWarnEnabled()) {
            log.warn("The same IdGeneratorProvider is bound to CosIdIdentifierFactory with {} different generator names "
                + "(for example, several Spring contexts without cosid.provider.isolated): factories created without "
                + "arguments use the most recent one. Create the factory with new CosIdIdentifierFactory(name, provider) "
                + "to choose.", names);
        }
        return binding;
    }

    /**
     * Lets the multiple-providers and multiple-names warnings, each logged once per process, be logged again. For tests.
     */
    static void resetMultipleProvidersWarning() {
        MULTIPLE_PROVIDERS_WARNED.set(false);
        MULTIPLE_NAMES_WARNED.set(false);
    }

    /**
     * Removes the most recent binding of the provider, leaving bindings of other providers in place. When several
     * bindings share the provider, use {@link #unbind(Binding)} to remove a specific one.
     *
     * @param idGeneratorProvider the provider previously passed to {@link #bind(IdGeneratorProvider)}
     * @return {@code true} if a binding was removed
     */
    public static boolean unbind(IdGeneratorProvider idGeneratorProvider) {
        for (Binding binding : BOUND_PROVIDERS) {
            if (binding.provider() == idGeneratorProvider && BOUND_PROVIDERS.removeFirstOccurrence(binding)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes exactly this binding, leaving all others in place, including those of the same provider.
     *
     * @param binding the binding returned by {@link #bind(IdGeneratorProvider, String)}
     * @return {@code true} if the binding was removed, {@code false} if it was already removed
     * @since 4.1.0
     */
    public static boolean unbind(Binding binding) {
        // Binding has identity equality, so this removes this instance only.
        return BOUND_PROVIDERS.removeFirstOccurrence(binding);
    }

    @Override
    public String generateIdentifier() {
        IdGeneratorProvider provider = idGeneratorProvider;
        String name = generatorName;
        if (provider == null) {
            Binding bound = BOUND_PROVIDERS.peekFirst();
            provider = bound != null ? bound.provider() : DefaultIdGeneratorProvider.INSTANCE;
            if (boundName && bound != null && bound.generatorName() != null) {
                name = bound.generatorName();
            }
        }
        String resolvedName = name;
        return provider.get(resolvedName)
            .orElseThrow(() -> new NotFoundIdGeneratorException(resolvedName))
            .generateAsString();
    }
}
