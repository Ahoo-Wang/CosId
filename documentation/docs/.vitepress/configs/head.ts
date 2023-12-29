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

import {SITE_BASE} from "./SITE_BASE";
import {HeadConfig} from "vitepress";

export const head: HeadConfig[] = [
    ['link', {rel: 'icon', href: `${SITE_BASE}favicon.ico`}],
    ['meta', {
        name: 'keywords',
        content: '通用, 灵活, 高性能的分布式ID生成器'
    }],
    ['meta', {'http-equiv': 'cache-control', content: 'no-cache, no-store, must-revalidate'}],
    ['meta', {'http-equiv': 'pragma', content: 'no-cache'}],
    ['meta', {'http-equiv': 'expires', content: '0'}],
    ['link', {rel: 'manifest', href: `${SITE_BASE}manifest.webmanifest`}],
    ['meta', {name: 'application-name', content: 'CosId'}],
    ['meta', {name: 'theme-color', content: '#5f67ee'}],
    [
        'script',
        {async: '', src: 'https://www.googletagmanager.com/gtag/js?id=G-SP6EEGK56L'}
    ],
    [
        'script',
        {},
        `window.dataLayer = window.dataLayer || [];
      function gtag(){dataLayer.push(arguments);}
      gtag('js', new Date());
      gtag('config', 'G-SP6EEGK56L');`
    ]
]