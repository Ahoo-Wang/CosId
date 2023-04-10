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

package me.ahoo.cosid.machine;

import me.ahoo.cosid.CosIdException;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * LocalMachine State Storage.
 *
 * @author ahoo wang
 */
@Slf4j
public class LocalMachineStateStorage implements MachineStateStorage {
    public static final String DEFAULT_STATE_LOCATION_PATH = "./cosid-machine-state/";
    public final String stateLocation;
    
    public LocalMachineStateStorage(String stateLocation) {
        this.stateLocation = stateLocation;
    }
    
    public LocalMachineStateStorage() {
        this(DEFAULT_STATE_LOCATION_PATH);
    }
    
    @Nonnull
    @Override
    public MachineState get(String namespace, InstanceId instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkNotNull(instanceId, "instanceId can not be null!");
        
        File stateFile = getStateFile(namespace, instanceId);
        if (log.isDebugEnabled()) {
            log.debug("Get from stateLocation : [{}].", stateFile.getAbsolutePath());
        }
        
        if (!stateFile.exists()) {
            if (log.isInfoEnabled()) {
                log.info("Get from stateLocation : [{}] not found.", stateFile.getAbsolutePath());
            }
            return MachineState.NOT_FOUND;
        }
        String stateString;
        try {
            stateString = Files.asCharSource(stateFile, Charsets.UTF_8).readFirstLine();
        } catch (IOException e) {
            throw new CosIdException(e);
        }
        if (Strings.isNullOrEmpty(stateString)) {
            if (log.isWarnEnabled()) {
                log.warn("Get from stateLocation : [{}] state data is empty.", stateFile.getAbsolutePath());
            }
            return MachineState.NOT_FOUND;
        }
        if (log.isDebugEnabled()) {
            log.debug("Get state data : [{}].", stateString);
        }
        return MachineState.of(stateString);
    }
    
    private File getStateFile(String namespace, InstanceId instanceId) {
        File stateDirectory = new File(stateLocation);
        if (!stateDirectory.exists()) {
            boolean ignored = stateDirectory.mkdirs();
        }
        String fileName = namespace + "__" + instanceId.getInstanceId();
        String encodedName = BaseEncoding.base64().encode(fileName.getBytes(Charsets.UTF_8));
        String statePath = stateLocation + encodedName;
        return new File(statePath);
    }
    
    @Override
    public void set(String namespace, int machineId, InstanceId instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(machineId >= 0, "machineId:[%s] must be greater than or equal to 0!", machineId);
        Preconditions.checkNotNull(instanceId, "instanceId can not be null!");
        
        File stateFile = getStateFile(namespace, instanceId);
        if (log.isDebugEnabled()) {
            log.debug("Set machineId:[{}] to stateLocation : [{}].", machineId, stateFile.getAbsolutePath());
        }
        
        String stateString = MachineState.of(machineId, System.currentTimeMillis()).toStateString();
        if (!stateFile.exists()) {
            try {
                boolean ignored = stateFile.createNewFile();
            } catch (IOException e) {
                throw new CosIdException(e);
            }
        }
        
        try (FileOutputStream fileOutputStream = new FileOutputStream(stateFile, false)) {
            fileOutputStream.write(stateString.getBytes(Charsets.UTF_8));
            fileOutputStream.flush();
        } catch (IOException e) {
            throw new CosIdException(e.getMessage(), e);
        }
    }
    
    @Override
    public void remove(String namespace, InstanceId instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkNotNull(instanceId, "instanceId can not be null!");
        
        File stateFile = getStateFile(namespace, instanceId);
        if (log.isInfoEnabled()) {
            log.info("Remove stateLocation : [{}].", stateFile.getAbsolutePath());
        }
        if (stateFile.exists()) {
            boolean isDeleted = stateFile.delete();
            if (!isDeleted) {
                log.warn("Remove and delete instance :[{}] stateFile in namespace[{}] not successful! FilePath:[{}]", instanceId, namespace, stateFile.getAbsolutePath());
            }
        }
    }
    
    @Override
    public void clear(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        
        if (log.isInfoEnabled()) {
            log.info("Clear namespace : [{}].", namespace);
        }
        File[] stateFiles = getStateFilesOf(namespace);
        if (stateFiles == null) {
            return;
        }
        for (File stateFile : stateFiles) {
            if (log.isInfoEnabled()) {
                log.info("Clear stateLocation : [{}].", stateFile.getAbsolutePath());
            }
            boolean ignored = stateFile.delete();
        }
    }
    
    private File[] getStateFilesOf(String namespace) {
        File stateDirectory = new File(stateLocation);
        
        if (!stateDirectory.exists()) {
            return new File[0];
        }
        return stateDirectory.listFiles(((dir, name) -> name.startsWith(namespace)));
    }
    
    @Override
    public int size(String namespace) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        
        return getStateFilesOf(namespace).length;
    }
    
    @Override
    public boolean exists(String namespace, InstanceId instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkNotNull(instanceId, "instanceId can not be null!");
        
        File stateFile = getStateFile(namespace, instanceId);
        return stateFile.exists();
    }
}
