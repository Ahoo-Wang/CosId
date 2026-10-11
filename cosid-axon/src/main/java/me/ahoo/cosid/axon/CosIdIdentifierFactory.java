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

import java.util.concurrent.atomic.AtomicReference;

/**
 * CosId Identifier Factory .
 *
 * <p>Axon discovers {@link IdentifierFactory} through {@link java.util.ServiceLoader} and keeps it in a JVM-wide
 * static, so the no-arg instance cannot receive a provider by injection. It resolves generators from the provider
 * registered with {@link #bind(IdGeneratorProvider)} (the Spring Boot starter binds the application context's provider),
 * falling back to {@link DefaultIdGeneratorProvider#INSTANCE} when none is bound.
 *
 * @author ahoo wang
 */
public class CosIdIdentifierFactory extends IdentifierFactory {
    public static final String ID_KEY = "cosid.axon";

    private static final AtomicReference<IdGeneratorProvider> BOUND_PROVIDER = new AtomicReference<>();

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
     * Binds the provider used by factories created without an explicit provider.
     *
     * @param idGeneratorProvider the provider to bind
     */
    public static void bind(IdGeneratorProvider idGeneratorProvider) {
        BOUND_PROVIDER.set(idGeneratorProvider);
    }

    /**
     * Unbinds the provider, only if it is still the bound one.
     *
     * @param idGeneratorProvider the provider previously passed to {@link #bind(IdGeneratorProvider)}
     * @return {@code true} if it was unbound
     */
    public static boolean unbind(IdGeneratorProvider idGeneratorProvider) {
        return BOUND_PROVIDER.compareAndSet(idGeneratorProvider, null);
    }

    private IdGeneratorProvider currentProvider() {
        if (idGeneratorProvider != null) {
            return idGeneratorProvider;
        }
        IdGeneratorProvider bound = BOUND_PROVIDER.get();
        return bound != null ? bound : DefaultIdGeneratorProvider.INSTANCE;
    }

    @Override
    public String generateIdentifier() {
        return currentProvider().get(generatorName)
            .orElseThrow(() -> new NotFoundIdGeneratorException(generatorName))
            .generateAsString();
    }
}
