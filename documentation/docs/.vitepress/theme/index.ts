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

import DefaultTheme from 'vitepress/theme'
import {onMounted, watch, nextTick} from 'vue'
import {type Theme, inBrowser, useData, useRoute, withBase} from 'vitepress'
import mediumZoom from 'medium-zoom'
import './global.css'
import {redirectOf} from './redirects'
import CopyOrDownloadAsMarkdownButtons
    from 'vitepress-plugin-llms/vitepress-components/CopyOrDownloadAsMarkdownButtons.vue'

export default {
    extends: DefaultTheme,
    enhanceApp({app}) {
        app.component('CopyOrDownloadAsMarkdownButtons', CopyOrDownloadAsMarkdownButtons)
    },
    setup() {
        const route = useRoute()
        const {site} = useData()
        const redirectIfMoved = () => {
            if (!inBrowser) {
                return
            }
            const base = site.value.base
            const path = route.path.startsWith(base) ? `/${route.path.slice(base.length)}` : route.path
            const target = redirectOf(path)
            if (target) {
                window.location.replace(withBase(target) + window.location.hash)
            }
        }
        const initZoom = () => {
            mediumZoom('.main img', {background: 'var(--vp-c-bg)'})
        }
        const fixMermaidInlineStyles = () => {
            let attempts = 0
            const fix = setInterval(() => {
                document.querySelectorAll('.mermaid svg [style]').forEach(el => {
                    const s = (el as HTMLElement).style
                    if (s.fill && !s.fill.includes('#1e3a5f')) {
                        s.fill = '#1e3a5f'
                    }
                    if (s.stroke && !s.stroke.includes('#4a9eed')) {
                        s.stroke = '#4a9eed'
                    }
                    if (s.color) {
                        s.color = '#e0e0e0'
                    }
                })
                if (++attempts >= 20) {
                    clearInterval(fix)
                }
            }, 500)
        }
        const initMermaidZoom = () => {
            document.querySelectorAll('.mermaid').forEach(el => {
                const target = el as HTMLElement
                if (target.dataset.mermaidZoom) {
                    return
                }
                target.dataset.mermaidZoom = 'true'
                target.addEventListener('click', () => {
                    const modal = document.createElement('div')
                    modal.className = 'mermaid-zoom-modal'
                    modal.innerHTML = target.outerHTML
                    modal.addEventListener('click', () => modal.remove())
                    document.body.appendChild(modal)
                })
            })
        }
        const initMermaid = () => {
            fixMermaidInlineStyles()
            initMermaidZoom()
        }
        onMounted(() => {
            redirectIfMoved()
            initZoom()
            initMermaid()
        })
        watch(
            () => route.path,
            () => nextTick(() => {
                redirectIfMoved()
                initZoom()
                initMermaid()
            })
        )
    },
}satisfies Theme