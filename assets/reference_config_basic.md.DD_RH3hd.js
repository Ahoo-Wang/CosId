import{_ as t,c as i,a2 as a,o as e}from"./chunks/framework.Dg3afQf7.js";const c=JSON.parse('{"title":"基础配置","description":"","frontmatter":{},"headers":[],"relativePath":"reference/config/basic.md","filePath":"reference/config/basic.md","lastUpdated":1744776295000}'),d={name:"reference/config/basic.md"};function n(h,s,l,r,o,p){return e(),i("div",null,s[0]||(s[0]=[a(`<h1 id="基础配置" tabindex="-1">基础配置 <a class="header-anchor" href="#基础配置" aria-label="Permalink to &quot;基础配置&quot;">​</a></h1><blockquote><p><code>me.ahoo.cosid.spring.boot.starter.CosIdProperties</code></p></blockquote><table tabindex="0"><thead><tr><th>名称</th><th>数据类型</th><th>说明</th><th>默认值</th></tr></thead><tbody><tr><td>enabled</td><td><code>boolean</code></td><td>是否启用 CosId</td><td><code>true</code></td></tr><tr><td>namespace</td><td><code>String</code></td><td>命名空间，用于隔离不同应用间的分布式ID</td><td><code>cosid</code></td></tr></tbody></table><p><strong>YAML 配置样例</strong></p><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">cosid</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  namespace</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">\${spring.application.name}</span></span></code></pre></div><h2 id="idconverterdefinition" tabindex="-1">IdConverterDefinition <a class="header-anchor" href="#idconverterdefinition" aria-label="Permalink to &quot;IdConverterDefinition&quot;">​</a></h2><blockquote><p><code>me.ahoo.cosid.spring.boot.starter.IdConverterDefinition</code></p></blockquote><table tabindex="0"><thead><tr><th>名称</th><th>数据类型</th><th>说明</th><th>默认值</th></tr></thead><tbody><tr><td>type</td><td><code>IdConverterDefinition.Type</code></td><td>类型：<code>TO_STRING</code>/<code>SNOWFLAKE_FRIENDLY</code>/<code>RADIX</code></td><td><code>RADIX</code></td></tr><tr><td>prefix</td><td><code>String</code></td><td>前缀</td><td><code>&quot;&quot;</code></td></tr><tr><td>radix</td><td><code>IdConverterDefinition.Radix</code></td><td><code>Radix62IdConverter</code> 转换器配置</td><td></td></tr><tr><td>friendly</td><td><code>IdConverterDefinition.Friendly</code></td><td>转换器配置</td><td></td></tr></tbody></table><h3 id="radix" tabindex="-1">Radix <a class="header-anchor" href="#radix" aria-label="Permalink to &quot;Radix&quot;">​</a></h3><table tabindex="0"><thead><tr><th>名称</th><th>数据类型</th><th>说明</th><th>默认值</th></tr></thead><tbody><tr><td>char-size</td><td><code>String</code></td><td>字符串ID长度</td><td><code>11</code></td></tr><tr><td>pad-start</td><td><code>boolean</code></td><td>当字符串不满足 <code>charSize</code> 时，是否填充字符(<code>&#39;0&#39;</code>)。如果需要保证字符串有序，需开启该功能</td><td><code>false</code></td></tr></tbody></table><h3 id="friendly" tabindex="-1">Friendly <a class="header-anchor" href="#friendly" aria-label="Permalink to &quot;Friendly&quot;">​</a></h3><table tabindex="0"><thead><tr><th>名称</th><th>数据类型</th><th>说明</th><th>默认值</th></tr></thead><tbody><tr><td>pad-start</td><td><code>boolean</code></td><td>当字符串不满足 <code>charSize</code> 时，是否填充字符(<code>&#39;0&#39;</code>)。如果需要保证字符串有序，需开启该功能</td><td><code>false</code></td></tr></tbody></table><p><strong>YAML 配置样例</strong></p><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">cosid</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  snowflake</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    share</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      converter</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        prefix</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">cosid_</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        radix</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">          pad-start</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">false</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">          char-size</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">11</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  segment</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    share</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      converter</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        prefix</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">cosid_</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        radix</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">          pad-start</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">false</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">          char-size</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">8</span></span></code></pre></div>`,14)]))}const E=t(d,[["render",n]]);export{c as __pageData,E as default};
