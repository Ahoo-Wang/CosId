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


/**
 * Pages moved by the documentation restructure, old path to new path, without the locale prefix and `.html`.
 * Links to an old page land on the site's not-found route, which redirects them here.
 */
export const redirects: Record<string, string> = {
    '/guide/snowflake': '/guide/concepts/snowflake',
    '/guide/segment': '/guide/concepts/segment',
    '/guide/segment-chain': '/guide/concepts/segment',
    '/guide/concepts/segment-chain': '/guide/concepts/segment',
    '/guide/cosid-generator': '/guide/concepts/cosid-generator',
    '/guide/id-converter': '/guide/concepts/id-converter',
    '/guide/provider': '/guide/concepts/provider',
    '/guide/advanced/id-generator': '/guide/concepts/id-generator',
    '/guide/specific-id': '/guide/recipes',
    '/guide/extensions/cosid-redis': '/guide/backends/redis',
    '/guide/extensions/cosid-jdbc': '/guide/backends/jdbc',
    '/guide/extensions/cosid-mongo': '/guide/backends/mongo',
    '/guide/extensions/cosid-zookeeper': '/guide/backends/zookeeper',
    '/guide/cosid-proxy': '/guide/backends/proxy',
    '/guide/extensions/cosid-test': '/guide/backends/test-suite',
    '/guide/extensions/cosid-spring-boot-starter': '/guide/integrations/spring-boot-starter',
    '/guide/extensions/cosid-mybatis': '/guide/integrations/mybatis',
    '/guide/extensions/cosid-spring-data-jdbc': '/guide/integrations/spring-data-jdbc',
    '/guide/extensions/cosid-jackson': '/guide/integrations/jackson',
    '/guide/extensions/cosid-activiti': '/guide/integrations/activiti',
    '/guide/extensions/cosid-flowable': '/guide/integrations/flowable',
    '/guide/extensions/cosid-axon': '/guide/integrations/axon',
    '/guide/extensions/cosid-shardingsphere': '/guide/integrations/shardingsphere',
    '/guide/advanced/cosid-annotation': '/guide/integrations/annotation',
    '/guide/sharding/mod-cycle': '/guide/integrations/sharding/mod-cycle',
    '/guide/sharding/interval-timeline': '/guide/integrations/sharding/interval-timeline',
    '/guide/migration-4.0': '/guide/migration/4.0',
    '/guide/faq/faq': '/guide/operations/faq',
    '/guide/best-practices': '/guide/operations/production',
    '/guide/faq/perf-test': '/reference/performance/benchmark',
    '/guide/faq/perf-vs': '/reference/performance/comparison',
    '/guide/faq/Performance-CosId-Leaf': '/reference/performance/cosid-vs-leaf',
}

/**
 * Returns the new path of a moved page, keeping the locale prefix, or `undefined` when the page did not move.
 * `path` is the route path without the site base, for example `/zh/guide/snowflake.html`.
 */
export function redirectOf(path: string): string | undefined {
    const match = /^(\/zh)?(\/.*?)(\.html)?$/.exec(path)
    if (!match) {
        return undefined
    }
    const [, locale = '', page, html = ''] = match
    const target = redirects[page]
    return target ? `${locale}${target}${html}` : undefined
}
