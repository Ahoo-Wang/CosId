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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.IntIdGenerator;
import me.ahoo.cosid.accessor.CosId;
import me.ahoo.cosid.accessor.IdFiller;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fills the {@link CosId} field of an entity (07-runtime-and-spring.md §3). The accessor of a class is resolved on its
 * first fill and cached per class: the annotated field (on the field, or on a type naming it) is looked up along the
 * superclasses, its type resolved through generic superclasses, and read and written through its getter and setter if
 * it has them, else directly. A class has at most one id; a class without one is left as it is.
 */
public final class AnnotatedIdFiller implements IdFiller {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType LONG_GETTER = MethodType.methodType(long.class, Object.class);
    private static final MethodType LONG_SETTER = MethodType.methodType(void.class, Object.class, long.class);
    private static final MethodType INT_GETTER = MethodType.methodType(int.class, Object.class);
    private static final MethodType INT_SETTER = MethodType.methodType(void.class, Object.class, int.class);
    private static final MethodType OBJECT_GETTER = MethodType.methodType(Object.class, Object.class);
    private static final MethodType OBJECT_SETTER = MethodType.methodType(void.class, Object.class, Object.class);
    private static final Accessor NONE = entity -> {
    };

    private final Function<String, GeneratorEntry> generators;
    private final ClassValue<Accessor> accessors = new ClassValue<>() {
        @Override
        protected Accessor computeValue(Class<?> type) {
            return resolve(type);
        }
    };

    /**
     * A filler of a runtime's generators.
     *
     * @param generators the generator of a name; throws for an unknown or disabled one
     */
    public AnnotatedIdFiller(Function<String, GeneratorEntry> generators) {
        this.generators = Objects.requireNonNull(generators, "generators");
    }

    @Override
    public void fill(Object entity) {
        Accessor accessor = accessors.get(entity.getClass());
        try {
            accessor.fill(entity);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new CosIdException("Failed to fill the id of " + entity.getClass().getName(), e);
        }
    }

    @FunctionalInterface
    private interface Accessor {
        void fill(Object entity) throws Throwable;
    }

    private record LongAccessor(MethodHandle getter, MethodHandle setter, IdGenerator ids) implements Accessor {
        @Override
        public void fill(Object entity) throws Throwable {
            if ((long) getter.invokeExact(entity) <= 0) {
                setter.invokeExact(entity, ids.generate());
            }
        }
    }

    private record IntAccessor(MethodHandle getter, MethodHandle setter, IntIdGenerator ids) implements Accessor {
        @Override
        public void fill(Object entity) throws Throwable {
            if ((int) getter.invokeExact(entity) <= 0) {
                setter.invokeExact(entity, ids.generate());
            }
        }
    }

    /** {@code Long}, {@code Integer} and {@code String} fields. */
    private record ObjectAccessor(MethodHandle getter, MethodHandle setter, Supplier<Object> next) implements Accessor {
        @Override
        public void fill(Object entity) throws Throwable {
            if (isUnset(getter.invokeExact(entity))) {
                setter.invokeExact(entity, next.get());
            }
        }

        private static boolean isUnset(Object value) {
            return switch (value) {
                case null -> true;
                case Long id -> id <= 0;
                case Integer id -> id <= 0;
                case String id -> id.isEmpty();
                default -> false;
            };
        }
    }

    private record Target(Field field, String generator) {
    }

    private Accessor resolve(Class<?> type) {
        Target target = null;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            CosId onType = c.getDeclaredAnnotation(CosId.class);
            if (onType != null) {
                Field field = findField(c, onType.field());
                if (field == null) {
                    throw new IllegalStateException("@CosId on " + c.getName() + " names field [" + onType.field() + "], which it does not have");
                }
                target = merge(type, target, new Target(field, onType.value()));
            }
            for (Field field : c.getDeclaredFields()) {
                CosId onField = field.getDeclaredAnnotation(CosId.class);
                if (onField != null) {
                    target = merge(type, target, new Target(field, onField.value()));
                }
            }
        }
        return target == null ? NONE : accessor(type, target);
    }

    private static Target merge(Class<?> type, Target found, Target next) {
        if (Modifier.isStatic(next.field().getModifiers())) {
            throw new IllegalStateException("@CosId field " + describe(next.field()) + " must not be static");
        }
        if (found == null || found.equals(next)) {
            return next;
        }
        throw new IllegalStateException(type.getName() + " has more than one @CosId: " + describe(found.field()) + " ["
            + found.generator() + "] and " + describe(next.field()) + " [" + next.generator() + "]");
    }

    private static String describe(Field field) {
        return field.getDeclaringClass().getName() + '.' + field.getName();
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    return field;
                }
            }
        }
        return null;
    }

    private Accessor accessor(Class<?> type, Target target) {
        Field field = target.field();
        Class<?> idType = resolveType(type, field);
        GeneratorEntry entry = generators.apply(target.generator());
        if (idType == long.class) {
            return new LongAccessor(getter(type, field, idType, LONG_GETTER), setter(type, field, idType, LONG_SETTER), entry.ids());
        }
        if (idType == int.class) {
            entry.ids();
            return new IntAccessor(getter(type, field, idType, INT_GETTER), setter(type, field, idType, INT_SETTER),
                entry.as(IntIdGenerator.class));
        }
        Supplier<Object> next;
        if (idType == Long.class) {
            IdGenerator ids = entry.ids();
            next = ids::generate;
        } else if (idType == Integer.class) {
            entry.ids();
            IntIdGenerator ids = entry.as(IntIdGenerator.class);
            next = ids::generate;
        } else if (idType == String.class) {
            Supplier<String> text = entry.text();
            next = text::get;
        } else {
            throw new IllegalStateException("@CosId field " + describe(field) + " is a " + idType.getName()
                + ": only long, Long, int, Integer and String are supported");
        }
        return new ObjectAccessor(getter(type, field, idType, OBJECT_GETTER), setter(type, field, idType, OBJECT_SETTER), next);
    }

    /** The field's type as seen from {@code type}: a type variable is resolved through the generic superclasses. */
    static Class<?> resolveType(Class<?> type, Field field) {
        Type generic = field.getGenericType();
        if (generic instanceof Class<?> plain) {
            return plain;
        }
        if (generic instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        if (generic instanceof TypeVariable<?> variable) {
            Map<TypeVariable<?>, Type> bindings = new HashMap<>();
            for (Class<?> c = type; c != null && c != field.getDeclaringClass(); c = c.getSuperclass()) {
                if (c.getGenericSuperclass() instanceof ParameterizedType superType) {
                    TypeVariable<?>[] parameters = ((Class<?>) superType.getRawType()).getTypeParameters();
                    Type[] arguments = superType.getActualTypeArguments();
                    for (int i = 0; i < parameters.length; i++) {
                        Type argument = arguments[i];
                        bindings.put(parameters[i], argument instanceof TypeVariable<?> outer ? bindings.getOrDefault(outer, outer) : argument);
                    }
                }
            }
            Type bound = bindings.get(variable);
            if (bound instanceof Class<?> resolved) {
                return resolved;
            }
            if (bound instanceof ParameterizedType parameterized) {
                return (Class<?>) parameterized.getRawType();
            }
        }
        throw new IllegalStateException("The type of @CosId field " + describe(field) + " (" + generic.getTypeName()
            + ") does not resolve to a class from " + type.getName());
    }

    private static MethodHandle getter(Class<?> type, Field field, Class<?> idType, MethodType shape) {
        Method method = findMethod(type, "get" + capitalize(field.getName()), idType, false);
        try {
            MethodHandle handle = method != null ? LOOKUP.unreflect(method) : LOOKUP.unreflectGetter(accessible(field));
            return handle.asType(shape);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read @CosId field " + describe(field), e);
        }
    }

    private static MethodHandle setter(Class<?> type, Field field, Class<?> idType, MethodType shape) {
        Method method = findMethod(type, "set" + capitalize(field.getName()), idType, true);
        try {
            if (method == null && Modifier.isFinal(field.getModifiers())) {
                throw new IllegalStateException("@CosId field " + describe(field) + " is final and has no setter");
            }
            MethodHandle handle = method != null ? LOOKUP.unreflect(method) : LOOKUP.unreflectSetter(accessible(field));
            return handle.asType(shape);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write @CosId field " + describe(field), e);
        }
    }

    private static Field accessible(Field field) {
        if (!field.trySetAccessible()) {
            throw new IllegalStateException("@CosId field " + describe(field) + " is not accessible: open its package to me.ahoo.cosid.core");
        }
        return field;
    }

    /**
     * The most derived accessible getter ({@code setter == false}) or setter of the id: a getter returns the id type or a
     * supertype of it (a generic getter), a setter takes one of them.
     */
    private static Method findMethod(Class<?> type, String name, Class<?> idType, boolean setter) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.isBridge() || method.isSynthetic() || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                boolean matches = setter
                    ? method.getParameterCount() == 1 && accepts(method.getParameterTypes()[0], idType)
                    : method.getParameterCount() == 0 && accepts(method.getReturnType(), idType);
                if (matches && method.trySetAccessible()) {
                    return method;
                }
            }
        }
        return null;
    }

    private static boolean accepts(Class<?> declared, Class<?> idType) {
        return declared == idType || !idType.isPrimitive() && declared.isAssignableFrom(idType);
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
