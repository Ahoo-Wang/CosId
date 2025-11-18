import {DefaultTheme} from "vitepress/types/default-theme";

export const navbar: DefaultTheme.NavItem[] = [
    {
        text: '指南',
        link: '/zh/guide/getting-started',
        activeMatch: '^/zh/guide/'
    },
    {
        text: '参考',
        activeMatch: '^/zh/reference/',
        items: [
            {
                text: '配置',
                link: '/zh/reference/config/basic',
            },
            {text: '谁在使用 CosId', link: '/zh/reference/showcase/who-is-using'},
            {text: '博客', link: '/zh/reference/blog/ShardingSphere-Integration-CosId'},
        ]
    },
    {
        text: 'JavaDoc',
        link: `/javadoc/index.html`,
        target: '_blank'
    },
    {
        text: "资源",
        items: [
            {
                text: '开源项目 - 微服务治理',
                items: [
                    {
                        text: 'Wow - 基于 DDD & EventSourcing 的现代响应式 CQRS 架构微服务开发框架',
                        link: 'https://github.com/Ahoo-Wang/Wow'
                    },
                    {
                        text: 'CoSky - 高性能、低成本微服务治理平台',
                        link: 'https://github.com/Ahoo-Wang/CoSky'
                    },
                    {
                        text: 'CoSec - 基于 RBAC 和策略的多租户响应式安全框架',
                        link: 'https://github.com/Ahoo-Wang/CoSec'
                    },
                    {
                        text: 'CoCache - 分布式一致性二级缓存框架',
                        link: 'https://github.com/Ahoo-Wang/CoCache'
                    },
                    {
                        text: 'Simba - 易用、灵活的分布式锁服务',
                        link: 'https://github.com/Ahoo-Wang/Simba'
                    }
                ]
            }
        ]
    },
    {
        text: `更新日志`,
        link: `https://github.com/Ahoo-Wang/CosId/releases`
    }
]