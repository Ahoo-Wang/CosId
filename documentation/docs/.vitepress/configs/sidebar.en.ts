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
            {text: 'ID Generators', link: 'id-generator'},
            {text: 'SnowflakeId', link: 'snowflake'},
            {text: 'Machine ID', link: 'machine-id'},
            {text: 'SegmentId / SegmentChainId', link: 'segment'},
            {text: 'CosIdGenerator', link: 'cosid-generator'},
            {text: 'ID Converters', link: 'id-converter'},
            {text: 'ID Generator Provider', link: 'provider'},
        ],
    }, {
        base: '/guide/backends/',
        text: 'Backends',
        collapsed: false,
        items: [
            {text: 'Choosing a Backend', link: 'index'},
            {text: 'Redis', link: 'redis'},
            {text: 'JDBC', link: 'jdbc'},
            {text: 'MongoDB', link: 'mongo'},
            {text: 'ZooKeeper', link: 'zookeeper'},
            {text: 'CosId Proxy', link: 'proxy'},
            {text: 'Compatibility Test Suite', link: 'test-suite'},
        ],
    }, {
        base: '/guide/integrations/',
        text: 'Integrations',
        collapsed: false,
        items: [
            {text: 'Spring Boot Starter', link: 'spring-boot-starter'},
            {text: '@CosId Annotation', link: 'annotation'},
            {text: 'MyBatis', link: 'mybatis'},
            {text: 'Spring Data JDBC', link: 'spring-data-jdbc'},
            {text: 'Jackson', link: 'jackson'},
            {text: 'ShardingSphere', link: 'shardingsphere'},
            {text: 'Modulo Sharding', link: 'sharding/mod-cycle'},
            {text: 'Interval Sharding', link: 'sharding/interval-timeline'},
            {text: 'Activiti', link: 'activiti'},
            {text: 'Flowable', link: 'flowable'},
            {text: 'Axon', link: 'axon'},
        ],
    }, {
        text: 'Configuration',
        collapsed: false,
        items: [
            {text: 'Configuration Reference', link: '/reference/config/basic'},
        ],
    }, {
        base: '/guide/',
        text: 'Operations',
        collapsed: false,
        items: [
            {text: 'Best Practices', link: 'best-practices'},
            {text: 'FAQ', link: 'operations/faq'},
        ],
    }, {
        base: '/guide/migration/',
        text: 'Migration',
        collapsed: false,
        items: [
            {text: 'Migrating to 4.0', link: '4.0'},
        ],
    },
]

const reference: DefaultTheme.SidebarItem[] = [
    {
        base: '/reference/config/',
        text: 'Configuration',
        collapsed: false,
        items: [
            {text: 'Basics', link: 'basic'},
            {text: 'Machine ID', link: 'machine'},
            {text: 'SnowflakeId', link: 'snowflake'},
            {text: 'SegmentId', link: 'segment'},
            {text: 'CosIdGenerator', link: 'cosid-generator'},
            {text: 'ZooKeeper', link: 'zookeeper'},
            {text: 'Proxy', link: 'proxy'},
            {text: 'ShardingSphere', link: 'shardingsphere'},
        ],
    }, {
        base: '/reference/performance/',
        text: 'Performance',
        collapsed: false,
        items: [
            {text: 'JMH Benchmark', link: 'benchmark'},
            {text: 'Comparison of ID Schemes', link: 'comparison'},
            {text: 'CosId VS Leaf', link: 'cosid-vs-leaf'},
        ],
    }, {
        base: '/reference/',
        text: 'Community',
        collapsed: false,
        items: [
            {text: 'Who is using CosId', link: 'showcase/who-is-using'},
            {text: 'ShardingSphere Integration CosId', link: 'blog/ShardingSphere-Integration-CosId'},
        ],
    },
]

export const sidebar: DefaultTheme.Sidebar = {
    '/guide/': guide,
    '/reference/': reference,
}
