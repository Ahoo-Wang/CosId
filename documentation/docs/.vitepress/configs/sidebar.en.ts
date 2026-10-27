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

import {DefaultTheme} from "vitepress/types/default-theme";

const guide: DefaultTheme.SidebarItem[] = [
    {
        base: '/guide/',
        text: 'Start Here',
        collapsed: false,
        items: [
            {text: 'Why CosId', link: 'introduction'},
            {text: 'Getting Started', link: 'getting-started'},
            {text: 'ID Recipes', link: 'recipes'},
        ],
    }, {
        base: '/guide/concepts/',
        text: 'Core Concepts',
        collapsed: false,
        items: [
            {text: 'Layouts and Codecs', link: 'layout-and-codecs'},
            {text: 'Machine Slot Lease', link: 'slot-lease'},
            {text: 'Segments and Periodic Segments', link: 'segment'},
            {text: 'The Runtime', link: 'runtime'},
        ],
    }, {
        base: '/guide/backends/',
        text: 'Backends',
        collapsed: false,
        items: [
            {text: 'Choosing a Backend', link: 'index'},
            {text: 'JDBC', link: 'jdbc'},
            {text: 'Redis', link: 'redis'},
            {text: 'MongoDB', link: 'mongo'},
            {text: 'ZooKeeper', link: 'zookeeper'},
            {text: 'Store TCK', link: 'test-suite'},
        ],
    }, {
        base: '/guide/',
        text: 'Remote Server',
        collapsed: false,
        items: [
            {text: 'cosid-server', link: 'server'},
        ],
    }, {
        base: '/guide/integrations/',
        text: 'Integrations',
        collapsed: false,
        items: [
            {text: 'Spring Boot Starter', link: 'spring-boot-starter'},
            {text: '@CosId Field Filling', link: 'annotation'},
            {text: 'MyBatis', link: 'mybatis'},
            {text: 'Spring Data JDBC', link: 'spring-data-jdbc'},
            {text: 'Jackson', link: 'jackson'},
            {text: 'Activiti', link: 'activiti'},
            {text: 'Flowable', link: 'flowable'},
            {text: 'Axon', link: 'axon'},
            {text: 'Sharding Algorithms', link: 'sharding'},
        ],
    }, {
        text: 'Configuration',
        collapsed: false,
        items: [
            {text: 'Configuration Reference', link: '/reference/config/basic'},
        ],
    }, {
        base: '/guide/operations/',
        text: 'Operations',
        collapsed: false,
        items: [
            {text: 'Production Checklist', link: 'production'},
            {text: 'Metrics and Health', link: 'monitoring'},
            {text: 'Troubleshooting', link: 'troubleshooting'},
            {text: 'FAQ', link: 'faq'},
        ],
    }, {
        base: '/guide/migration/',
        text: 'Migration',
        collapsed: false,
        items: [
            {text: '4.x → 5.0', link: 'from-4x'},
        ],
    },
]

const reference: DefaultTheme.SidebarItem[] = [
    {
        base: '/reference/config/',
        text: 'Configuration',
        collapsed: false,
        items: [
            {text: 'Runtime, Stores, Machine', link: 'basic'},
            {text: 'Generators and Codecs', link: 'generators'},
            {text: 'cosid-server', link: 'server'},
        ],
    }, {
        base: '/reference/performance/',
        text: 'Performance',
        collapsed: false,
        items: [
            {text: 'Benchmarks', link: 'benchmark'},
        ],
    }, {
        base: '/reference/',
        text: 'Community',
        collapsed: false,
        items: [
            {text: 'Who is using CosId', link: 'showcase/who-is-using'},
            {text: 'ShardingSphere Integration CosId (4.x)', link: 'blog/ShardingSphere-Integration-CosId'},
        ],
    },
]

export const sidebar: DefaultTheme.Sidebar = {
    '/guide/': guide,
    '/reference/': reference,
}
