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

/**
 * The store handed out a segment max id below the last one plus the step: the range would overlap ids already issued.
 *
 * <p>An {@link IllegalStateException}, as the max id check always threw.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public class IdSegmentRollbackException extends IllegalStateException {
    private final String namespacedName;
    private final long nextMaxId;
    private final long nextMinMaxId;
    
    public IdSegmentRollbackException(String namespacedName, long nextMaxId, long nextMinMaxId) {
        // Same message as the check it replaces.
        super("nextMaxId:[" + nextMaxId + "] must be greater than nextMinMaxId:[" + nextMinMaxId + "]!");
        this.namespacedName = namespacedName;
        this.nextMaxId = nextMaxId;
        this.nextMinMaxId = nextMinMaxId;
    }
    
    /**
     * Raised for a rollback another party detected and described (e.g. the proxy server); the max ids are unknown ({@code -1}).
     */
    public IdSegmentRollbackException(String namespacedName, String message, Throwable cause) {
        super(message, cause);
        this.namespacedName = namespacedName;
        this.nextMaxId = -1;
        this.nextMinMaxId = -1;
    }
    
    public String getNamespacedName() {
        return namespacedName;
    }
    
    public long getNextMaxId() {
        return nextMaxId;
    }
    
    public long getNextMinMaxId() {
        return nextMinMaxId;
    }
}
