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

package me.ahoo.cosid.spring.redis;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import me.ahoo.cosid.CosIdException;

import java.io.IOException;
import java.net.URL;

/**
 * @author ahoo wang
 */
public final class Scripts {

    public static String getScript(String resourceName) {
        URL resourceUrl = Resources.getResource(resourceName);
        try {
            return Resources.toString(resourceUrl, Charsets.UTF_8);
        } catch (IOException e) {
            throw new CosIdException(e.getMessage(), e);
        }
    }
}
