import {DefaultTheme} from "vitepress/types/default-theme";

export const navbar: DefaultTheme.NavItem[] = [
    {
        text: 'Guide',
        link: '/guide/getting-started',
        activeMatch: '^/guide/'
    },
    {
        text: 'Reference',
        activeMatch: '^/reference/',
        items: [
            {
                text: 'Configuration',
                link: '/reference/config/basic',
            },
            {text: 'Who is using CosId', link: '/reference/showcase/who-is-using'},
            {text: 'Blog', link: '/reference/blog/ShardingSphere-Integration-CosId'},
        ]
    },
    {
        text: 'JavaDoc',
        link: `/javadoc/index.html`,
        target: '_blank'
    },
    {
        text: "Resources",
        items: [
            {
                text: 'Open Source Projects - Microservice Governance',
                items: [
                    {
                        text: 'Wow - Modern reactive CQRS architecture microservice development framework based on DDD & EventSourcing',
                        link: 'https://github.com/Ahoo-Wang/Wow'
                    },
                    {
                        text: 'CoSky - High-performance, low-cost microservice governance platform',
                        link: 'https://github.com/Ahoo-Wang/CoSky'
                    },
                    {
                        text: 'CoSec - Multi-tenant reactive security framework based on RBAC and policies',
                        link: 'https://github.com/Ahoo-Wang/CoSec'
                    },
                    {
                        text: 'CoCache - Distributed consistent secondary cache framework',
                        link: 'https://github.com/Ahoo-Wang/CoCache'
                    },
                    {
                        text: 'Simba - Easy-to-use, flexible distributed lock service',
                        link: 'https://github.com/Ahoo-Wang/Simba'
                    }
                ]
            }
        ]
    },
    {
        text: `Changelog`,
        link: `https://github.com/Ahoo-Wang/CosId/releases`
    }
]