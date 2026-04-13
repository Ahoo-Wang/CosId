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
    ['link', {rel: 'preconnect', href: 'https://www.googletagmanager.com'}],
    ['meta', {
        name: 'keywords',
        content: 'CosId,分布式ID生成器,ID生成器,分布式ID,唯一ID,自增ID,ID,雪花算法,Snowflake,SnowflakeId,UUID,Sequence,Leaf,UidGenerator'
    }],
    ['meta', {
        name: 'description',
        content: 'CosId | 通用、灵活、高性能的分布式ID生成器 | Universal, flexible, high-performance distributed ID generator.'
    }],
    // Open Graph / Social Sharing
    ['meta', {property: 'og:type', content: 'website'}],
    ['meta', {property: 'og:site_name', content: 'CosId'}],
    ['meta', {property: 'og:title', content: 'CosId - Universal Distributed ID Generator'}],
    ['meta', {property: 'og:description', content: '通用、灵活、高性能的分布式ID生成器 | Universal, flexible, high-performance distributed ID generator'}],
    ['meta', {property: 'og:image', content: 'https://cosid.ahoo.me/logo.png'}],
    // Twitter Card
    ['meta', {name: 'twitter:card', content: 'summary_large_image'}],
    ['meta', {name: 'twitter:title', content: 'CosId - Universal Distributed ID Generator'}],
    ['meta', {name: 'twitter:description', content: '通用、灵活、高性能的分布式ID生成器 | Universal, flexible, high-performance distributed ID generator'}],
    ['meta', {name: 'twitter:image', content: 'https://cosid.ahoo.me/logo.png'}],
    // Cache control
    ['meta', {'http-equiv': 'cache-control', content: 'no-cache, no-store, must-revalidate'}],
    ['meta', {'http-equiv': 'pragma', content: 'no-cache'}],
    ['meta', {'http-equiv': 'expires', content: '0'}],
    ['meta', {name: 'application-name', content: 'CosId'}],
    ['meta', {name: 'theme-color', content: '#5f67ee'}],
    // Google Analytics
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