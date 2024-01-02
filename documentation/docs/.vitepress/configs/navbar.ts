import {DefaultTheme} from "vitepress/types/default-theme";

export const navbar: DefaultTheme.NavItem[] = [
    {
        text: '指南',
        link: '/guide/getting-started',
        activeMatch: '^/guide/'
    },
    {
        text: '配置',
        link: '/reference/config/basic',
        activeMatch: '^/reference/config/'
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
    },
    {
        text: `Gitee`,
        link: `https://gitee.com/AhooWang/CosId`
    }
]