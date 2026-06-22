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
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * File-based machine state storage.
 *
 * <p>Stores machine state in local files, using base64-encoded
 * filenames for namespace/instance encoding.
 *
 * @author ahoo wang
 */
@Slf4j
public class LocalMachineStateStorage implements MachineStateStorage {
    private static final String STATE_FILE_DELIMITER = "__";
    private static final String STATE_FILE_VERSION = "v2";
    private static final String STATE_FILE_COMPONENT_DELIMITER = ".";
    /**
     * Default state location in user home directory.
     */
    public static final String DEFAULT_STATE_LOCATION_PATH = Paths.get(System.getProperty("user.home"), ".cosid-machine-state").toString();
    /**
     * The state location path.
     */
    public final String stateLocation;

    /**
     * Creates storage with specified location.
     *
     * @param stateLocation the directory path for state files
     */
    public LocalMachineStateStorage(String stateLocation) {
        this.stateLocation = stateLocation;
    }

    /**
     * Creates storage with default location.
     */
    public LocalMachineStateStorage() {
        this(DEFAULT_STATE_LOCATION_PATH);
    }

    @Override
    public @NonNull MachineState get(String namespace, InstanceId instanceId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkNotNull(instanceId, "instanceId can not be null!");

        File stateFile = getExistingStateFile(namespace, instanceId);
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
        File stateDirectory = getStateDirectory();
        String fileName = STATE_FILE_VERSION
            + STATE_FILE_COMPONENT_DELIMITER + encodeComponent(namespace)
            + STATE_FILE_COMPONENT_DELIMITER + encodeComponent(instanceId.getInstanceId())
            + STATE_FILE_COMPONENT_DELIMITER + instanceId.isStable();
        return new File(Paths.get(stateDirectory.getAbsolutePath(), fileName).toString());
    }

    private File getExistingStateFile(String namespace, InstanceId instanceId) {
        File stateFile = getStateFile(namespace, instanceId);
        if (stateFile.exists()) {
            return stateFile;
        }
        return getLegacyStateFile(namespace, instanceId);
    }

    private File getLegacyStateFile(String namespace, InstanceId instanceId) {
        File stateDirectory = getStateDirectory();
        String fileName = namespace + STATE_FILE_DELIMITER + instanceId.getInstanceId();
        return new File(Paths.get(stateDirectory.getAbsolutePath(), encode(fileName)).toString());
    }

    private File getStateDirectory() {
        File stateDirectory = new File(stateLocation);
        if (!stateDirectory.exists()) {
            stateDirectory.mkdirs();
        }
        return stateDirectory;
    }

    private String encode(String text) {
        return BaseEncoding.base64().encode(text.getBytes(Charsets.UTF_8));
    }

    private String decode(String text) {
        return new String(BaseEncoding.base64().decode(text), Charsets.UTF_8);
    }

    private String encodeComponent(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(Charsets.UTF_8));
    }

    private String decodeComponent(String text) {
        return new String(Base64.getUrlDecoder().decode(text), Charsets.UTF_8);
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
        File legacyStateFile = getLegacyStateFile(namespace, instanceId);
        if (legacyStateFile.exists()) {
            legacyStateFile.delete();
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
        File legacyStateFile = getLegacyStateFile(namespace, instanceId);
        if (legacyStateFile.exists()) {
            legacyStateFile.delete();
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
        return stateDirectory.listFiles(((dir, name) -> {
            String actualNamespace = decodeNamespace(name);
            return namespace.equals(actualNamespace);
        }));
    }

    private String decodeNamespace(String fileName) {
        String v2Namespace = decodeV2Namespace(fileName);
        if (v2Namespace != null) {
            return v2Namespace;
        }
        try {
            String decodedName = decode(fileName);
            int delimiterIndex = decodedName.lastIndexOf(STATE_FILE_DELIMITER);
            if (delimiterIndex < 0) {
                return null;
            }
            return decodedName.substring(0, delimiterIndex);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String decodeV2Namespace(String fileName) {
        String prefix = STATE_FILE_VERSION + STATE_FILE_COMPONENT_DELIMITER;
        if (!fileName.startsWith(prefix)) {
            return null;
        }
        String[] components = fileName.split("\\" + STATE_FILE_COMPONENT_DELIMITER, 4);
        if (components.length != 4) {
            return null;
        }
        try {
            return decodeComponent(components[1]);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

        return getStateFile(namespace, instanceId).exists() || getLegacyStateFile(namespace, instanceId).exists();
    }
}
