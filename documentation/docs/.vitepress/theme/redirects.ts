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
 * Pages moved by a documentation restructure (4.x, then 5.0), old path to its current path, without the locale prefix and `.html`.
 * Links to an old page land on the site's not-found route, which redirects them here.
 */
export const redirects: Record<string, string> = {
    // The 4.x restructure (3.x paths), flattened to their 5.0 pages.
    '/guide/snowflake': '/guide/concepts/layout-and-codecs',
    '/guide/segment': '/guide/concepts/segment',
    '/guide/segment-chain': '/guide/concepts/segment',
    '/guide/concepts/segment-chain': '/guide/concepts/segment',
    '/guide/cosid-generator': '/guide/concepts/layout-and-codecs',
    '/guide/id-converter': '/guide/concepts/layout-and-codecs',
    '/guide/provider': '/guide/concepts/runtime',
    '/guide/advanced/id-generator': '/guide/concepts/runtime',
    '/guide/specific-id': '/guide/recipes',
    '/guide/extensions/cosid-redis': '/guide/backends/redis',
    '/guide/extensions/cosid-jdbc': '/guide/backends/jdbc',
    '/guide/extensions/cosid-mongo': '/guide/backends/mongo',
    '/guide/extensions/cosid-zookeeper': '/guide/backends/zookeeper',
    '/guide/cosid-proxy': '/guide/server',
    '/guide/extensions/cosid-test': '/guide/backends/test-suite',
    '/guide/extensions/cosid-spring-boot-starter': '/guide/integrations/spring-boot-starter',
    '/guide/extensions/cosid-mybatis': '/guide/integrations/mybatis',
    '/guide/extensions/cosid-spring-data-jdbc': '/guide/integrations/spring-data-jdbc',
    '/guide/extensions/cosid-jackson': '/guide/integrations/jackson',
    '/guide/extensions/cosid-activiti': '/guide/integrations/activiti',
    '/guide/extensions/cosid-flowable': '/guide/integrations/flowable',
    '/guide/extensions/cosid-axon': '/guide/integrations/axon',
    '/guide/extensions/cosid-shardingsphere': '/guide/integrations/sharding',
    '/guide/advanced/cosid-annotation': '/guide/integrations/annotation',
    '/guide/sharding/mod-cycle': '/guide/integrations/sharding',
    '/guide/sharding/interval-timeline': '/guide/integrations/sharding',
    '/guide/migration-4.0': '/guide/migration/from-4x',
    '/guide/faq/faq': '/guide/operations/faq',
    '/guide/best-practices': '/guide/operations/production',
    '/guide/faq/perf-test': '/reference/performance/benchmark',
    '/guide/faq/perf-vs': '/reference/performance/benchmark',
    '/guide/faq/Performance-CosId-Leaf': '/reference/performance/benchmark',
    // The 4.x pages that 5.0 removed or merged (R5-B).
    '/guide/concepts/snowflake': '/guide/concepts/layout-and-codecs',
    '/guide/concepts/cosid-generator': '/guide/concepts/layout-and-codecs',
    '/guide/concepts/id-converter': '/guide/concepts/layout-and-codecs',
    '/guide/concepts/machine-id': '/guide/concepts/slot-lease',
    '/guide/concepts/id-generator': '/guide/concepts/runtime',
    '/guide/concepts/provider': '/guide/concepts/runtime',
    '/guide/backends/proxy': '/guide/server',
    '/guide/integrations/shardingsphere': '/guide/integrations/sharding',
    '/guide/integrations/sharding/mod-cycle': '/guide/integrations/sharding',
    '/guide/integrations/sharding/interval-timeline': '/guide/integrations/sharding',
    '/guide/operations/rolling-upgrade': '/guide/operations/production',
    '/guide/migration/4.0': '/guide/migration/from-4x',
    '/reference/config/machine': '/reference/config/basic',
    '/reference/config/snowflake': '/reference/config/generators',
    '/reference/config/segment': '/reference/config/generators',
    '/reference/config/cosid-generator': '/reference/config/generators',
    '/reference/config/proxy': '/reference/config/server',
    '/reference/config/zookeeper': '/guide/backends/zookeeper',
    '/reference/config/shardingsphere': '/guide/integrations/sharding',
    '/reference/performance/comparison': '/reference/performance/benchmark',
    '/reference/performance/cosid-vs-leaf': '/reference/performance/benchmark',
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
