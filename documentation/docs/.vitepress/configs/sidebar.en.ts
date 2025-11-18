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

export const sidebar: DefaultTheme.Sidebar = {
    '/guide/': [
        {
            base: '/guide/',
            text: 'Guide',
            collapsed: false,
            items: [
                {text: 'Introduction', link: 'introduction'},
                {text: 'Getting Started', link: 'getting-started'},
                {text: 'SnowflakeId', link: 'snowflake'},
                {text: 'SegmentId', link: 'segment'},
                {text: 'SegmentChainId', link: 'segment-chain'},
                {text: 'CosIdGenerator', link: 'cosid-generator'},
                {text: 'IdConverter', link: 'id-converter'},
                {text: 'Id Generator Container', link: 'provider'},
                {text: 'Specific Scenario ID Configuration', link: 'specific-id'},
                {text: 'CosIdProxy', link: 'cosid-proxy'},
            ],
        }, {
            base: '/guide/extensions/',
            text: 'Extensions',
            collapsed: false,
            items: [
                {text: 'Redis', link: 'cosid-redis'},
                {text: 'Jdbc', link: 'cosid-jdbc'},
                {text: 'MongoDB', link: 'cosid-mongo'},
                {text: 'Zookeeper', link: 'cosid-zookeeper'},
                {text: 'MyBatis', link: 'cosid-mybatis'},
                {text: 'Jackson', link: 'cosid-jackson'},
                {text: 'Spring-Data-Jdbc', link: 'cosid-spring-data-jdbc'},
                {text: 'Spring-Boot-Starter', link: 'cosid-spring-boot-starter'},
                {text: 'Activiti', link: 'cosid-activiti'},
                {text: 'Flowable', link: 'cosid-flowable'},
                {text: 'Axon', link: 'cosid-axon'},
                {text: 'ShardingSphere', link: 'cosid-shardingsphere'},
            ],
        }, {
            base: '/guide/faq/',
            text: 'FAQ',
            collapsed: false,
            items: [
                {text: 'Performance Comparison with Leaf', link: 'Performance-CosId-Leaf'},
                {text: 'FAQ', link: 'faq'},
                {text: 'Performance Test', link: 'perf-test'},
                {text: 'Performance Comparison', link: 'perf-vs'},
            ],
        }, {
            base: '/guide/',
            text: 'Best Practices',
            collapsed: false,
            items: [
                {text: 'Best Practices', link: 'best-practices'},
            ],
        }
    ],
    '/reference/': [
        {
            base: '/reference/config/',
            text: 'Configuration',
            collapsed: false,
            items: [
                {text: 'Basic Configuration', link: 'basic'},
                {text: 'CosIdGenerator Configuration', link: 'cosid-generator'},
                {text: 'Machine Configuration', link: 'machine'},
                {text: 'Segment Configuration', link: 'segment'},
                {text: 'Snowflake Configuration', link: 'snowflake'},
                {text: 'Zookeeper Configuration', link: 'zookeeper'},
                {text: 'ShardingSphere Configuration', link: 'shardingsphere'},
            ],
        }, {
            base: '/reference/',
            text: 'Showcase',
            collapsed: false,
            items: [
                {text: 'Who is using CosId', link: 'showcase/who-is-using'},
            ],
        }, {
            base: '/reference/blog/',
            text: 'Blog',
            collapsed: false,
            items: [
                {text: 'Distributed ID Generator Design and Implementation', link: 'Distributed-ID'},
                {text: 'SegmentChainId Performance Analysis (120M/s)', link: 'SegmentChainId'},
                {text: 'ShardingSphere Integration CosId', link: 'ShardingSphere-Integration-CosId'},
            ],
        }
    ]
}