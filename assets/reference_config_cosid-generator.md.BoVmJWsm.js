import{_ as s,c as e,a2 as i,o as a}from"./chunks/framework.Dg3afQf7.js";const k=JSON.parse('{"title":"Machine 配置","description":"","frontmatter":{},"headers":[],"relativePath":"reference/config/cosid-generator.md","filePath":"reference/config/cosid-generator.md","lastUpdated":1744776295000}'),d={name:"reference/config/cosid-generator.md"};function n(h,t,o,l,r,c){return a(),e("div",null,t[0]||(t[0]=[i(`<h1 id="machine-配置" tabindex="-1">Machine 配置 <a class="header-anchor" href="#machine-配置" aria-label="Permalink to &quot;Machine 配置&quot;">​</a></h1><blockquote><p><code>me.ahoo.cosid.spring.boot.starter.cosid.CosIdGeneratorProperties</code></p></blockquote><table tabindex="0"><thead><tr><th>名称</th><th>数据类型</th><th>说明</th><th>默认值</th></tr></thead><tbody><tr><td>enabled</td><td><code>boolean</code></td><td>是否启用</td><td><code>false</code></td></tr><tr><td>type</td><td><code>enum</code></td><td>类型：<code>RADIX62</code>/<code>RADIX36</code>/<code>FRIENDLY</code></td><td><code>RADIX62</code></td></tr><tr><td>namespace</td><td><code>String</code></td><td>命名空间</td><td><code>cosid</code></td></tr><tr><td>timestamp-bit</td><td><code>int</code></td><td>时间戳位数</td><td><code>44</code></td></tr><tr><td>machine-bit</td><td><code>int</code></td><td>机器位数</td><td><code>20</code></td></tr><tr><td>sequence-bit</td><td><code>int</code></td><td>序列位数</td><td><code>16</code></td></tr></tbody></table><h2 id="配置案例" tabindex="-1">配置案例 <a class="header-anchor" href="#配置案例" aria-label="Permalink to &quot;配置案例&quot;">​</a></h2><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">cosid</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  namespace</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">\${spring.application.name}</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  machine</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    enabled</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    distributor</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      type</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">jdbc</span></span>
<span class="line highlighted"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  generator</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line highlighted"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    enabled</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span></code></pre></div>`,5)]))}const E=s(d,[["render",n]]);export{k as __pageData,E as default};
