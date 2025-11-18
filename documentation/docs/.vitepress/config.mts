import {defineConfig} from 'vitepress'
import {SITE_BASE} from "./configs/SITE_BASE";
import {head} from "./configs/head";
import {navbar as navbarEn} from "./configs/navbar.en";
import {navbar as navbarZh} from "./configs/navbar.zh";
import {sidebar as sidebarEn} from "./configs/sidebar.en";
import {sidebar as sidebarZh} from "./configs/sidebar.zh";
import {withMermaid} from "vitepress-plugin-mermaid";

let hostname = 'https://cosid.ahoo.me/';
if (SITE_BASE == '/wow/') {
    hostname = 'https://ahoowang.gitee.io/cosid/'
}

// https://vitepress.dev/reference/site-config
let userConfig = defineConfig({
    ignoreDeadLinks: 'localhostLinks',
    head: head,
    base: SITE_BASE,
    rewrites: {
        'en/:rest*': ':rest*'
    },
    sitemap: {
        hostname: hostname,
        transformItems: (items) => {
            items.push({
                url: `${hostname}javadoc/index.html`,
                changefreq: 'weekly',
                priority: 0.8
            })
            return items
        }
    },
    appearance: 'dark',
    locales: {
        root: {
            label: 'English',
            lang: 'en-US',
            title: "CosId",
            description: "Universal, flexible, high-performance distributed ID generator",
            themeConfig: {
                logo: '/logo.png',
                siteTitle: 'CosId',
                editLink: {
                    pattern: 'https://github.com/Ahoo-Wang/CosId/edit/main/documentation/docs/:path'
                },
                lastUpdated: {
                    text: 'Last updated'
                },
                outline: {
                    label: 'On this page',
                    level: [2, 3]
                },
                aside: true,
                search: {provider: 'local',},
                // https://vitepress.dev/reference/default-theme-config
                nav: navbarEn,
                sidebar: sidebarEn,
                socialLinks: [
                    {icon: 'github', link: 'https://github.com/Ahoo-Wang/CosId'}
                ],
                externalLinkIcon: true,
                footer: {
                    message: 'Released under the Apache 2.0 License.',
                    copyright: 'Copyright © 2022-present <a href="https://github.com/Ahoo-Wang" target="_blank">Ahoo Wang</a>'
                },
                notFound: {
                    title: 'Page Not Found',
                    quote: 'The page you are looking for does not exist.',
                    linkText: 'Go back home'
                }
            }
        },
        zh: {
            label: '中文',
            lang: 'zh-CN',
            title: "CosId",
            description: "通用、灵活、高性能的分布式ID生成器",
            themeConfig: {
                logo: '/logo.png',
                siteTitle: 'CosId',
                editLink: {
                    pattern: 'https://github.com/Ahoo-Wang/CosId/edit/main/documentation/docs/zh/:path'
                },
                lastUpdated: {
                    text: '上次更新'
                },
                outline: {
                    label: '本页目录',
                    level: [2, 3]
                },
                aside: true,
                search: {provider: 'local',},
                // https://vitepress.dev/reference/default-theme-config
                nav: navbarZh,
                sidebar: sidebarZh,
                socialLinks: [
                    {icon: 'github', link: 'https://github.com/Ahoo-Wang/CosId'}
                ],
                externalLinkIcon: true,
                footer: {
                    message: 'Released under the Apache 2.0 License.',
                    copyright: 'Copyright © 2022-present <a href="https://github.com/Ahoo-Wang" target="_blank">Ahoo Wang</a>'
                },
                notFound: {
                    title: '页面未找到',
                    quote: '你访问的页面不存在。',
                    linkText: '返回首页'
                }
            }
        }
    }
})

export default withMermaid(userConfig)