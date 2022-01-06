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

import {SidebarConfigArray} from 'vuepress/config';

export function getGuideSidebar(groupA, groupB): SidebarConfigArray {
    const sidebar: SidebarConfigArray = [
        {
            title: groupA,
            collapsable: false,
            children: [
                '',
                'getting-started',
                'segment-chain'
            ]
        },
        {
            title: groupB,
            collapsable: false,
            children: [
                'cosid-jdbc',
                'cosid-redis',
                'cosid-zookeeper',
                'cosid-jackson',
                'cosid-mybatis',
                'cosid-shardingsphere'
            ]
        },
        {
            title: "API",
            collapsable: false,
            children: [
                'api/',
                'api/id-converter',
                'api/provider',
                'api/snowflake',
                'api/segment',
                'api/cosid-annotation',
                'api/sharding'
            ]
        },
        {
            title: '常见问题',
            collapsable: false,
            children: [
                "faq"
            ]
        },
        {
            title: '性能评测',
            collapsable: false,
            children: [
                "perf-test"
            ]
        }
    ]

    return sidebar
}

export function getConfigSidebar(groupA): SidebarConfigArray {
    const sidebar: SidebarConfigArray = [
        {
            title: groupA,
            collapsable: false,
            children: [
                '',
                'snowflake',
                'segment',
                'zookeeper',
                'shardingsphere'
            ]
        }
    ]
    return sidebar
}
