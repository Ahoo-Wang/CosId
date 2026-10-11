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

import org.axonframework.common.IdentifierFactory;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

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
public class CosIdIdentifierFactory extends IdentifierFactory {
    public static final String ID_KEY = "cosid.axon";

    private static final Deque<IdGeneratorProvider> BOUND_PROVIDERS = new ConcurrentLinkedDeque<>();

    private final String generatorName;
    private final IdGeneratorProvider idGeneratorProvider;

    /**
     * Creates a factory that resolves from the bound provider, or {@link DefaultIdGeneratorProvider#INSTANCE}.
     */
    public CosIdIdentifierFactory() {
        this(System.getProperty(ID_KEY, IdGeneratorProvider.SHARE), null);
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
        this.generatorName = generatorName;
        this.idGeneratorProvider = idGeneratorProvider;
    }

    /**
     * Binds a provider for factories created without an explicit provider; the most recent binding wins.
     *
     * @param idGeneratorProvider the provider to bind
     */
    public static void bind(IdGeneratorProvider idGeneratorProvider) {
        BOUND_PROVIDERS.addFirst(idGeneratorProvider);
    }

    /**
     * Removes one binding of the provider, leaving bindings made by others in place.
     *
     * @param idGeneratorProvider the provider previously passed to {@link #bind(IdGeneratorProvider)}
     * @return {@code true} if a binding was removed
     */
    public static boolean unbind(IdGeneratorProvider idGeneratorProvider) {
        return BOUND_PROVIDERS.removeFirstOccurrence(idGeneratorProvider);
    }

    private IdGeneratorProvider currentProvider() {
        if (idGeneratorProvider != null) {
            return idGeneratorProvider;
        }
        IdGeneratorProvider bound = BOUND_PROVIDERS.peekFirst();
        return bound != null ? bound : DefaultIdGeneratorProvider.INSTANCE;
    }

    @Override
    public String generateIdentifier() {
        return currentProvider().get(generatorName)
            .orElseThrow(() -> new NotFoundIdGeneratorException(generatorName))
            .generateAsString();
    }
}
