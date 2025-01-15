import{_ as r,c as a,a2 as t,o}from"./chunks/framework.DA3SO-Rd.js";const p=JSON.parse('{"title":"常见问题","description":"","frontmatter":{},"headers":[],"relativePath":"guide/faq/faq.md","filePath":"guide/faq/faq.md","lastUpdated":1736921966000}'),s={name:"guide/faq/faq.md"};function i(n,e,l,d,c,h){return o(),a("div",null,e[0]||(e[0]=[t('<h1 id="常见问题" tabindex="-1">常见问题 <a class="header-anchor" href="#常见问题" aria-label="Permalink to &quot;常见问题&quot;">​</a></h1><h2 id="cosid-需要部署服务端吗" tabindex="-1">CosId 需要部署服务端吗？ <a class="header-anchor" href="#cosid-需要部署服务端吗" aria-label="Permalink to &quot;CosId 需要部署服务端吗？&quot;">​</a></h2><p>虽然并没有规定 <a href="https://github.com/Ahoo-Wang/CosId" target="_blank" rel="noreferrer">CosId</a> 的使用方式，但是强烈推荐以本地 SDK 的方式使用，用户只需要安装一下 <strong>CosId</strong> 的依赖包做一些简单配置（ <a href="https://github.com/Ahoo-Wang/CosId/tree/main/cosid-example" target="_blank" rel="noreferrer">DEMO</a> ） 即可。</p><div class="tip custom-block"><p class="custom-block-title">TIP</p><p>分布式ID是不适合使用服务端部署模式的(C/S)。使用服务端部署模式，必然会产生网络IO（<em>Client</em>通过远程过程调用<em>Server</em>，获取ID），你想想我们费了那么大劲消除网络IO是为了什么？</p></div><h2 id="prefetchworker-是如何维护安全距离的" tabindex="-1">PrefetchWorker 是如何维护安全距离的？ <a class="header-anchor" href="#prefetchworker-是如何维护安全距离的" aria-label="Permalink to &quot;PrefetchWorker 是如何维护安全距离的？&quot;">​</a></h2><ul><li>定时维护：每隔一段时间<strong>PrefetchWorker</strong>会主动检测安全距离是否满足配置要求，如果不满足则执行<code>NextMaxId</code>预取，保证安全距离。</li><li>被动饥饿唤醒：当获取ID的线程获取ID时没有可用号段，会尝试获取新的号段，并主动唤醒<strong>PrefetchWorker</strong>并告诉他你太慢了，被唤醒的<strong>PrefetchWorker</strong>会检测安全距离是否需要膨胀，然后进行安全距离的维护。</li></ul><h2 id="本机单调、全局趋势递增-为什么还要尽可能保证单调递增" tabindex="-1">本机单调、全局趋势递增-为什么还要尽可能保证单调递增？ <a class="header-anchor" href="#本机单调、全局趋势递增-为什么还要尽可能保证单调递增" aria-label="Permalink to &quot;本机单调、全局趋势递增-为什么还要尽可能保证单调递增？&quot;">​</a></h2><p>从上文的论述中我们不难理解本机单调递增，全局趋势递增是权衡后的设计结果。 但是全局趋势递增的背面是周期内ID乱序，所以尽可能向单调递增优化（降低ID乱序程度）是优化目标，这俩点并不冲突。</p><p>如果各位同学还有其他问题请至 <a href="https://github.com/Ahoo-Wang/CosId/issues" target="_blank" rel="noreferrer">Issues</a> 提交你的疑问。</p>',9)]))}const u=r(s,[["render",i]]);export{p as __pageData,u as default};
