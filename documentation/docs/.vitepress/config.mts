import {defineConfig} from 'vitepress'
import {SITE_BASE} from "./configs/SITE_BASE";
import {head} from "./configs/head";
import {navbar} from "./configs/navbar";
import {sidebar} from "./configs/sidebar";

let hostname = 'https://cosid.ahoo.me/';
if (SITE_BASE == '/wow/') {
    hostname = 'https://ahoowang.gitee.io/cosid/'
}

// https://vitepress.dev/reference/site-config
export default defineConfig({
    lang: 'zh-CN',
    title: "CosId",
    description: "通用、灵活、高性能的分布式ID生成器",
    ignoreDeadLinks: 'localhostLinks',
    head: head,
    base: SITE_BASE,
    sitemap: {
        hostname: hostname
    },
    appearance: 'dark',
    themeConfig: {
        logo: '/logo.png',
        siteTitle: '分布式ID生成器 | CosId',
        editLink: {
            pattern: 'https://github.com/Ahoo-Wang/CosId/edit/main/documentation/docs/:path'
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
        nav: navbar,
        sidebar: sidebar,
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
})
