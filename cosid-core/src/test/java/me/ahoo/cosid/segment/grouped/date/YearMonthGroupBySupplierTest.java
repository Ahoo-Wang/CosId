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

import me.ahoo.cosid.segment.grouped.GroupedKey;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class YearMonthGroupBySupplierTest {

    @Test
    void get() {
        String pattern = "yyyy-MM";
        YearMonthGroupBySupplier supplier = new YearMonthGroupBySupplier(pattern);
        GroupedKey groupedKey = supplier.get();
        assertEquals(groupedKey.getKey(), YearMonth.now().format(DateTimeFormatter.ofPattern(pattern)));
    }

    @Test
    void getYyMm() {
        String pattern = "yyMM";
        YearMonthGroupBySupplier supplier = new YearMonthGroupBySupplier(pattern);
        GroupedKey groupedKey = supplier.get();
        assertEquals(groupedKey.getKey(), YearMonth.now().format(DateTimeFormatter.ofPattern(pattern)));
    }
}