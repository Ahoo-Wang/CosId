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

package me.ahoo.cosid;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

class CosIdTest {

    @Test
    void constantsShouldExposeConfigurationPrefixContract() {
        assertThat(CosId.COSID, equalTo("cosid"));
        assertThat(CosId.COSID_PREFIX, equalTo("cosid."));
    }

    @Test
    void epochDateShouldMatchDocumentedUtcEpoch() {
        assertThat(CosId.COSID_EPOCH_DATE, equalTo(LocalDateTime.of(2019, 12, 24, 16, 0)));
        assertThat(CosId.COSID_EPOCH_DATE.toEpochSecond(ZoneOffset.UTC), equalTo(CosId.COSID_EPOCH_SECOND));
        assertThat(CosId.COSID_EPOCH_DATE.toInstant(ZoneOffset.UTC).toEpochMilli(), equalTo(CosId.COSID_EPOCH));
    }
}
