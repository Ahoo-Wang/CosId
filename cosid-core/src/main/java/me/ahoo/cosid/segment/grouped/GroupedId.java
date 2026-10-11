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

package me.ahoo.cosid.segment.grouped;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * An id together with the group of the segment it came from.
 *
 * <p>Returned by {@link me.ahoo.cosid.segment.SegmentId#generateGrouped()} and passed to
 * {@link me.ahoo.cosid.IdConverter#asString(GroupedId)}: the group travels with the id.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public final class GroupedId {
    private final GroupedKey group;
    private final long value;
    
    public GroupedId(@NonNull GroupedKey group, long value) {
        this.group = Preconditions.checkNotNull(group, "group can not be null!");
        this.value = value;
    }
    
    public static GroupedId of(@NonNull GroupedKey group, long value) {
        return new GroupedId(group, value);
    }
    
    /**
     * The group of the segment the id came from, {@link GroupedKey#NEVER} when it is not grouped.
     */
    public @NonNull GroupedKey getGroup() {
        return group;
    }
    
    public long getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GroupedId)) {
            return false;
        }
        GroupedId that = (GroupedId) o;
        return value == that.value && group.equals(that.group);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(group, value);
    }
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("group", group)
            .add("value", value)
            .toString();
    }
}
