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

package me.ahoo.cosid.segment.grouped.date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.segment.grouped.GroupedKey;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.time.format.DateTimeFormatter;

class YearGroupBySupplierTest {

    @Test
    void year() {
        String pattern = "yyyy";
        YearGroupBySupplier supplier = new YearGroupBySupplier(pattern);
        GroupedKey groupedKey = supplier.get();
        assertThat(groupedKey.getKey(), equalTo(Year.now().format(DateTimeFormatter.ofPattern(pattern))));
    }

    @Test
    void yearTwoPattern() {
        String pattern = "yy";
        YearGroupBySupplier supplier = new YearGroupBySupplier(pattern);
        GroupedKey groupedKey = supplier.get();
        assertThat(groupedKey.getKey(), equalTo(Year.now().format(DateTimeFormatter.ofPattern(pattern))));
    }
}