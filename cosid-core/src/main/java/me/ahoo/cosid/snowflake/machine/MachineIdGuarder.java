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

package me.ahoo.cosid.snowflake.machine;

import com.google.common.annotations.Beta;

/**
 * MachineId Guarder .
 *
 * @author ahoo wang
 */
@Beta
public interface MachineIdGuarder {
    MachineIdGuarder NONE = new MachineIdGuarder.None();
    
    void register(String namespace, InstanceId instanceId);
    
    void unregister(String namespace, InstanceId instanceId);
    
    void start();
    
    void stop();
    
    boolean isRunning();
    
    class None implements MachineIdGuarder {
        
        @Override
        public void register(String namespace, InstanceId instanceId) {
        
        }
        
        @Override
        public void unregister(String namespace, InstanceId instanceId) {
        
        }
        
        @Override
        public void start() {
        
        }
        
        @Override
        public void stop() {
        
        }
        
        @Override
        public boolean isRunning() {
            return false;
        }
    }
}
