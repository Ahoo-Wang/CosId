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

package me.ahoo.cosid.internal;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import me.ahoo.cosid.CosId;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code cosid-core} does not depend on Guava.
 */
class NoGuavaArchTest {

    @Test
    void coreDoesNotUseGuava() throws URISyntaxException {
        Path coreClassesLocation = Paths.get(CosId.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        JavaClasses coreClasses = new ClassFileImporter().importPath(coreClassesLocation);

        noClasses().should().dependOnClassesThat().resideInAPackage("com.google.common..")
            .because("cosid-core does not depend on Guava")
            .check(coreClasses);
    }
}
