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
    '/zh/guide/': [
        {
            base: '/zh/guide/',
            text: '指南',
            collapsed: false,
            items: [
                {text: '简介', link: 'introduction'},
                {text: '快速上手', link: 'getting-started'},
                {text: 'SnowflakeId', link: 'snowflake'},
                {text: 'SegmentId', link: 'segment'},
                {text: 'SegmentChainId', link: 'segment-chain'},
                {text: 'CosIdGenerator', link: 'cosid-generator'},
                {text: 'IdConverter', link: 'id-converter'},
                {text: 'Id生成器容器', link: 'provider'},
                {text: '特定场景ID配置', link: 'specific-id'},
                {text: 'CosIdProxy', link: 'cosid-proxy'},
            ],
        }, {
            base: '/zh/guide/extensions/',
            text: '扩展',
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
                {text: '兼容性测试套件', link: 'cosid-test'},
            ],
        }, {
            base: '/zh/guide/faq/',
            text: 'FAQ',
            collapsed: false,
            items: [
                {text: '常见问题', link: 'faq'},
                {text: '性能评测', link: 'perf-test'},
                {text: 'CosId VS 美团 Leaf', link: 'Performance-CosId-Leaf'},
            ],
        }, {
            base: '/zh/guide/sharding/',
            text: '分片算法',
            collapsed: true,
            items: [
                {text: '取模分片算法', link: 'mod-cycle'},
                {text: '时间范围分片算法', link: 'interval-timeline'}
            ],
        },
        {
            text: '参考',
            collapsed: false,
            items: [
                {text: '配置', link: '/zh/reference/config/basic'},
                {text: '谁在使用 CosId', link: '/zh/reference/showcase/who-is-using'}
            ]
        }
    ],
    '/zh/reference/':
        [
            {
                text: '参考',
                items: [
                    {
                        text: '配置',
                        base: '/zh/reference/config/',
                        collapsed: false,
                        items: [
                            {text: '基础配置', link: 'basic'},
                            {text: '工作进程号', link: 'machine'},
                            {text: 'Snowflake', link: 'snowflake'},
                            {text: 'Segment', link: 'segment'},
                            {text: 'CosIdGenerator', link: 'cosid-generator'},
                            {text: 'Zookeeper', link: 'zookeeper'},
                            {text: 'ShardingSphere', link: 'shardingsphere'},
                        ],
                    }, {
                        text: '展示',
                        base: '/zh/reference/showcase/',
                        collapsed: false,
                        items: [
                            {text: '谁在使用 CosId', link: 'who-is-using'}
                        ]
                    }, {
                        text: '博客',
                        base: '/zh/reference/blog/',
                        collapsed: false,
                        items: [
                            {text: '分布式ID生成器（CosId）设计与实现', link: 'Distributed-ID'},
                            {text: '分布式ID(CosId)之号段链模式性能(1.2亿/s)解析', link: 'SegmentChainId'},
                            {text: 'ShardingSphere 集成 CosId 实战', link: 'ShardingSphere-Integration-CosId'}
                        ],
                    }
                ]
            }
        ]
}