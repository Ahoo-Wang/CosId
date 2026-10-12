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
        base: '/zh/guide/',
        text: '开始',
        collapsed: false,
        items: [
            {text: '为什么需要 CosId', link: 'introduction'},
            {text: '快速上手', link: 'getting-started'},
            {text: '常用 ID 配方', link: 'recipes'},
        ],
    }, {
        base: '/zh/guide/concepts/',
        text: '核心概念',
        collapsed: false,
        items: [
            {text: 'ID 生成器', link: 'id-generator'},
            {text: 'SnowflakeId', link: 'snowflake'},
            {text: 'SegmentId', link: 'segment'},
            {text: 'SegmentChainId', link: 'segment-chain'},
            {text: 'CosIdGenerator', link: 'cosid-generator'},
            {text: 'ID 转换器', link: 'id-converter'},
            {text: 'ID 生成器容器', link: 'provider'},
        ],
    }, {
        base: '/zh/guide/backends/',
        text: '后端',
        collapsed: false,
        items: [
            {text: 'Redis', link: 'redis'},
            {text: 'JDBC', link: 'jdbc'},
            {text: 'MongoDB', link: 'mongo'},
            {text: 'ZooKeeper', link: 'zookeeper'},
            {text: 'CosId Proxy', link: 'proxy'},
            {text: '兼容性测试套件', link: 'test-suite'},
        ],
    }, {
        base: '/zh/guide/integrations/',
        text: '集成',
        collapsed: false,
        items: [
            {text: 'Spring Boot Starter', link: 'spring-boot-starter'},
            {text: '@CosId 注解', link: 'annotation'},
            {text: 'MyBatis', link: 'mybatis'},
            {text: 'Spring Data JDBC', link: 'spring-data-jdbc'},
            {text: 'Jackson', link: 'jackson'},
            {text: 'ShardingSphere', link: 'shardingsphere'},
            {text: '取模分片算法', link: 'sharding/mod-cycle'},
            {text: '时间范围分片算法', link: 'sharding/interval-timeline'},
            {text: 'Activiti', link: 'activiti'},
            {text: 'Flowable', link: 'flowable'},
            {text: 'Axon', link: 'axon'},
        ],
    }, {
        text: '配置',
        collapsed: false,
        items: [
            {text: '配置参考', link: '/zh/reference/config/basic'},
        ],
    }, {
        base: '/zh/guide/',
        text: '运维',
        collapsed: false,
        items: [
            {text: '最佳实践', link: 'best-practices'},
            {text: '常见问题', link: 'operations/faq'},
        ],
    }, {
        base: '/zh/guide/migration/',
        text: '迁移',
        collapsed: false,
        items: [
            {text: '迁移到 4.0', link: '4.0'},
        ],
    },
]

const reference: DefaultTheme.SidebarItem[] = [
    {
        base: '/zh/reference/config/',
        text: '配置',
        collapsed: false,
        items: [
            {text: '基础配置', link: 'basic'},
            {text: '机器号', link: 'machine'},
            {text: 'SnowflakeId', link: 'snowflake'},
            {text: 'SegmentId', link: 'segment'},
            {text: 'CosIdGenerator', link: 'cosid-generator'},
            {text: 'ZooKeeper', link: 'zookeeper'},
            {text: 'ShardingSphere', link: 'shardingsphere'},
        ],
    }, {
        base: '/zh/reference/performance/',
        text: '性能',
        collapsed: false,
        items: [
            {text: 'JMH 基准测试', link: 'benchmark'},
            {text: '分布式 ID 方案对比', link: 'comparison'},
            {text: 'CosId VS Leaf', link: 'cosid-vs-leaf'},
        ],
    }, {
        base: '/zh/reference/',
        text: '社区',
        collapsed: false,
        items: [
            {text: '谁在使用 CosId', link: 'showcase/who-is-using'},
            {text: 'ShardingSphere 集成 CosId 实战', link: 'blog/ShardingSphere-Integration-CosId'},
        ],
    },
]

export const sidebar: DefaultTheme.Sidebar = {
    '/zh/guide/': guide,
    '/zh/reference/': reference,
}
