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

package me.ahoo.cosid.segment;

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.StringIdGeneratorDecorator;
import me.ahoo.cosid.converter.GroupedPrefixIdConverter;
import me.ahoo.cosid.segment.grouped.GroupedId;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import org.jspecify.annotations.NonNull;

/**
 * String-based SegmentId wrapper.
 *
 * <p>Wraps a SegmentId with a string converter for generating
 * string-based IDs while maintaining segment functionality.
 *
 * @author ahoo wang
 */
public class StringSegmentId extends StringIdGeneratorDecorator implements SegmentId {
    private final SegmentId actualSegmentId;
    private final boolean groupAware;

    /**
     * Creates a new StringSegmentId.
     *
     * @param actual        the underlying segment ID
     * @param idConverter the converter for string generation
     */
    public StringSegmentId(SegmentId actual, IdConverter idConverter) {
        super(actual, idConverter);
        this.actualSegmentId = actual;
        this.groupAware = isGroupAware(idConverter);
    }
    
    /**
     * Whether the converter, or a converter it decorates, uses the group of the id.
     */
    static boolean isGroupAware(IdConverter idConverter) {
        IdConverter current = idConverter;
        while (current != null) {
            if (current instanceof GroupedPrefixIdConverter) {
                return true;
            }
            current = current instanceof Decorator<?> decorator && decorator.getActual() instanceof IdConverter actual ? actual : null;
        }
        return false;
    }
    
    /**
     * Converts with the group of the id when the converter uses it, so a grouped prefix comes from the segment the id
     * came from.
     */
    @Override
    public @NonNull String generateAsString() {
        if (groupAware) {
            return idConverter.asString(actualSegmentId.generateGrouped());
        }
        return super.generateAsString();
    }

    @Override
    public IdSegment current() {
        return actualSegmentId.current();
    }
    
    @Override
    public @NonNull GroupedId generateGrouped() {
        return actualSegmentId.generateGrouped();
    }

    @Override
    public IdGeneratorStat stat() {
        return super.stat();
    }
}
