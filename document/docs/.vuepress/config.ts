/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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
import {defineConfig} from "vuepress/config";
import {
    NavItemsZH, SidebarZH
} from './config/index'

const GM_ID = 'G-SP6EEGK56L'

export default defineConfig(ctx => ({
    head: [
        ['link', {rel: 'icon', href: `/favicon.ico`}],
        [
            'script',
            {},
            `
    (function (global, doc, tag, src, script, m) {
      script = doc.createElement(tag)
      m = doc.getElementsByTagName(tag)[0]
      script.async = 1
      script.src = src
      m.parentNode.insertBefore(script, m)
      global.dataLayer = global.dataLayer || []
      if (!global.gtag) {
        global.gtag = function gtag () {
          global.dataLayer.push(arguments)
        }
        global.gtag('js', new Date())
        global.gtag('config', '${GM_ID}')
      }
    })(window, document, 'script', 'https://www.googletagmanager.com/gtag/js?id=${GM_ID}')
`
        ]
    ],
    title: 'CosId',
    description: '通用、灵活、高性能分布式 ID 生成器',
    themeConfig: {
        repo: "Ahoo-Wang/CosId",
        editLinks: true,
        docsDir: "document/docs",
        docsBranch: 'main',
        lastUpdated: '上次更新',
        nav: NavItemsZH,
        sidebar: SidebarZH
    },
    plugins: [
        ['@vuepress/back-to-top', true],
        ['@vuepress/medium-zoom', true],
        // TODO
        // [
        //     '@vuepress/pwa',
        //     {
        //         serviceWorker: true,
        //         updatePopup: true
        //     }
        // ],
        [
            'vuepress-plugin-container',
            {
                type: 'vue',
                before: '<pre class="vue-container"><code>',
                after: '</code></pre>'
            }
        ],
        [
            'vuepress-plugin-container',
            {
                type: 'upgrade',
                before: info => `<UpgradePath title="${info}">`,
                after: '</UpgradePath>'
            }
        ],
        ['vuepress-plugin-flowchart']
    ],
    extraWatchFiles: ['.vuepress/config/**'],
    evergreen: !ctx.isProd
}));
