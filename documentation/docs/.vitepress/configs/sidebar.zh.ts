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
            {text: '布局与编解码', link: 'layout-and-codecs'},
            {text: '机器号槽位租约', link: 'slot-lease'},
            {text: '号段与周期号段', link: 'segment'},
            {text: '运行时', link: 'runtime'},
        ],
    }, {
        base: '/zh/guide/backends/',
        text: '后端',
        collapsed: false,
        items: [
            {text: '选择后端', link: 'index'},
            {text: 'JDBC', link: 'jdbc'},
            {text: 'Redis', link: 'redis'},
            {text: 'MongoDB', link: 'mongo'},
            {text: 'ZooKeeper', link: 'zookeeper'},
            {text: '存储 TCK', link: 'test-suite'},
        ],
    }, {
        base: '/zh/guide/',
        text: '远程服务',
        collapsed: false,
        items: [
            {text: 'cosid-server', link: 'server'},
        ],
    }, {
        base: '/zh/guide/integrations/',
        text: '集成',
        collapsed: false,
        items: [
            {text: 'Spring Boot Starter', link: 'spring-boot-starter'},
            {text: '@CosId 字段填充', link: 'annotation'},
            {text: 'MyBatis', link: 'mybatis'},
            {text: 'Spring Data JDBC', link: 'spring-data-jdbc'},
            {text: 'Jackson', link: 'jackson'},
            {text: 'Activiti', link: 'activiti'},
            {text: 'Flowable', link: 'flowable'},
            {text: 'Axon', link: 'axon'},
            {text: '分片算法', link: 'sharding'},
        ],
    }, {
        text: '配置',
        collapsed: false,
        items: [
            {text: '配置参考', link: '/zh/reference/config/basic'},
        ],
    }, {
        base: '/zh/guide/operations/',
        text: '运维',
        collapsed: false,
        items: [
            {text: '生产环境清单', link: 'production'},
            {text: 'Kubernetes', link: 'kubernetes'},
            {text: '指标与健康检查', link: 'monitoring'},
            {text: '故障排查', link: 'troubleshooting'},
            {text: '常见问题', link: 'faq'},
        ],
    }, {
        base: '/zh/guide/migration/',
        text: '迁移',
        collapsed: false,
        items: [
            {text: '4.x → 5.0', link: 'from-4x'},
        ],
    },
]

const reference: DefaultTheme.SidebarItem[] = [
    {
        base: '/zh/reference/config/',
        text: '配置',
        collapsed: false,
        items: [
            {text: '运行时、存储与机器号', link: 'basic'},
            {text: '生成器与编解码', link: 'generators'},
            {text: 'cosid-server', link: 'server'},
        ],
    }, {
        base: '/zh/reference/performance/',
        text: '性能',
        collapsed: false,
        items: [
            {text: '基准测试', link: 'benchmark'},
        ],
    }, {
        base: '/zh/reference/',
        text: '社区',
        collapsed: false,
        items: [
            {text: '谁在使用 CosId', link: 'showcase/who-is-using'},
            {text: 'ShardingSphere 集成 CosId 实战(4.x)', link: 'blog/ShardingSphere-Integration-CosId'},
        ],
    },
]

export const sidebar: DefaultTheme.Sidebar = {
    '/zh/guide/': guide,
    '/zh/reference/': reference,
}
