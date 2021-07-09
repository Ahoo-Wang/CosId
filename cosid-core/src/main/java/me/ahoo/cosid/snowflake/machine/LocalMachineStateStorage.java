/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.CosIdException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author ahoo wang
 */
@Slf4j
public class LocalMachineStateStorage implements MachineStateStorage {
    public static final String DEFAULT_STATE_LOCATION_PATH = "./cosid-machine-state/";
    public static final String STATE_DELIMITER = "|";
    public final String stateLocation;

    public LocalMachineStateStorage(String stateLocation) {
        this.stateLocation = stateLocation;
    }

    public LocalMachineStateStorage() {
        this(DEFAULT_STATE_LOCATION_PATH);
    }

    @Override
    public MachineState get(String namespace, InstanceId instanceId) {
        File stateFile = getStateFile(namespace, instanceId);
        if (log.isInfoEnabled()) {
            log.info("get - read from stateLocation : [{}].", stateFile.getAbsolutePath());
        }

        if (!stateFile.exists()) {
            if (log.isInfoEnabled()) {
                log.info("get - read from stateLocation : [{}] not found.", stateFile.getAbsolutePath());
            }
            return MachineState.NOT_FOUND;
        }
        String stateLine = null;
        try {
            stateLine = Files.asCharSource(stateFile, Charsets.UTF_8).readFirstLine();
        } catch (IOException e) {
            throw new CosIdException(e);
        }
        if (Strings.isNullOrEmpty(stateLine)) {
            if (log.isWarnEnabled()) {
                log.warn("get - read from stateLocation : [{}] state data is empty.", stateFile.getAbsolutePath());
            }
            return MachineState.NOT_FOUND;
        }
        if (log.isInfoEnabled()) {
            log.info("get - state data : [{}].", stateLine);
        }

        List<String> stateSplits = Splitter.on(STATE_DELIMITER).omitEmptyStrings().splitToList(stateLine);
        if (stateSplits.size() != 2) {
            throw new IllegalArgumentException(Strings.lenientFormat("Machine status data:[{%s}] format error.", stateLine));
        }
        int machineId = Integer.parseInt(stateSplits.get(0));
        long lastStamp = Long.parseLong(stateSplits.get(1));
        return MachineState.of(machineId, lastStamp);
    }

    private File getStateFile(String namespace, InstanceId instanceId) {
        File stateDirectory = new File(stateLocation);
        if (!stateDirectory.exists()) {
            stateDirectory.mkdirs();
        }
        String statePath = stateLocation + namespace + "__" + instanceId.getInstanceId();
        return new File(statePath);
    }

    @Override
    public void set(String namespace, int machineId, InstanceId instanceId) {
        File stateFile = getStateFile(namespace, instanceId);
        if (log.isInfoEnabled()) {
            log.info("set - write machineId:[{}] to stateLocation : [{}].", machineId, stateFile.getAbsolutePath());
        }

        String stateLine = Strings.lenientFormat("%s%s%s", machineId, STATE_DELIMITER, System.currentTimeMillis());
        if (!stateFile.exists()) {
            try {
                stateFile.createNewFile();
            } catch (IOException e) {
                throw new CosIdException(e);
            }
        }

        try (FileOutputStream fileOutputStream = new FileOutputStream(stateFile, false)) {
            fileOutputStream.write(stateLine.getBytes(Charsets.UTF_8));
            fileOutputStream.flush();
        } catch (FileNotFoundException e) {
            throw new CosIdException(e);
        } catch (IOException e) {
            throw new CosIdException(e);
        }
    }

    @Override
    public void remove(String namespace, InstanceId instanceId) {
        File stateFile = getStateFile(namespace, instanceId);
        if (log.isInfoEnabled()) {
            log.info("remove - stateLocation : [{}].", stateFile.getAbsolutePath());
        }
        if (stateFile.exists()) {
            stateFile.delete();
        }
    }

    @Override
    public void clear(String namespace) {
        if (log.isInfoEnabled()) {
            log.info("clear - namespace : [{}].", namespace);
        }
        File[] stateFiles = getStateFilesOf(namespace);
        if (stateFiles == null) return;
        for (File stateFile : stateFiles) {
            if (log.isInfoEnabled()) {
                log.info("clear - stateLocation : [{}].", stateFile.getAbsolutePath());
            }
            stateFile.delete();
        }
    }

    private File[] getStateFilesOf(String namespace) {
        File stateDirectory = new File(stateLocation);

        if (!stateDirectory.exists()) {
            return new File[0];
        }
        File[] stateFiles = stateDirectory.listFiles(((dir, name) -> {
            return name.startsWith(namespace);
        }));
        return stateFiles;
    }

    @Override
    public int size(String namespace) {
        return getStateFilesOf(namespace).length;
    }


    @Override
    public boolean exists(String namespace, InstanceId instanceId) {
        File stateFile = getStateFile(namespace, instanceId);
        return stateFile.exists();
    }
}
