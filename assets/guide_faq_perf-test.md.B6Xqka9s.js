import{_ as a}from"./chunks/Throughput-Of-SegmentChainId.Dbnl_Gpc.js";import{_ as n,a as i}from"./chunks/Throughput-Of-IntervalShardingAlgorithm-RangeShardingValue.CauZn_Be.js";import{_ as e,a as p}from"./chunks/Throughput-Of-ModShardingAlgorithm-RangeShardingValue.BY-CRkKl.js";import{_ as t,c as l,a2 as h,o as r}from"./chunks/framework.Dg3afQf7.js";const d="/assets/Percentile-Sample-Of-SegmentChainId.CpiQNHk9.png",_=JSON.parse('{"title":"JMH-Benchmark","description":"","frontmatter":{},"headers":[],"relativePath":"guide/faq/perf-test.md","filePath":"guide/faq/perf-test.md","lastUpdated":1744776295000}'),o={name:"guide/faq/perf-test.md"};function c(k,s,g,m,u,C){return r(),l("div",null,s[0]||(s[0]=[h('<h1 id="jmh-benchmark" tabindex="-1">JMH-Benchmark <a class="header-anchor" href="#jmh-benchmark" aria-label="Permalink to &quot;JMH-Benchmark&quot;">​</a></h1><h2 id="运行环境说明" tabindex="-1">运行环境说明 <a class="header-anchor" href="#运行环境说明" aria-label="Permalink to &quot;运行环境说明&quot;">​</a></h2><ul><li>基准测试运行环境：笔记本开发机 ( MacBook Pro (M1) )</li><li>所有基准测试都在开发笔记本上执行。</li><li><strong>Redis</strong>、<strong>MySql</strong> 部署环境也在该笔记本开发机上。</li></ul><h2 id="segmentchainid" tabindex="-1">SegmentChainId <a class="header-anchor" href="#segmentchainid" aria-label="Permalink to &quot;SegmentChainId&quot;">​</a></h2><h3 id="吞吐量-ops-s" tabindex="-1">吞吐量 (ops/s) <a class="header-anchor" href="#吞吐量-ops-s" aria-label="Permalink to &quot;吞吐量 (ops/s)&quot;">​</a></h3><p align="center"><img src="'+a+`" alt="Throughput-Of-SegmentChainId"></p><div class="vp-code-group vp-adaptive-theme"><div class="tabs"><input type="radio" name="group-WRe1f" id="tab-Qv2uDd1" checked><label data-title="Gradle" for="tab-Qv2uDd1">Gradle</label><input type="radio" name="group-WRe1f" id="tab-rYLtk7T"><label data-title="Java" for="tab-rYLtk7T">Java</label></div><div class="blocks"><div class="language-shell vp-adaptive-theme active"><button title="Copy Code" class="copy"></button><span class="lang">shell</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">gradle</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> cosid-redis:jmh</span></span></code></pre></div><div class="language-shell vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">shell</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">java</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -jar</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> cosid-redis/build/libs/cosid-redis-1.8.6-jmh.jar</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -bm</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> thrpt</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -wi</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> 1</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -rf</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> json</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -f</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> 1</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> RedisChainIdBenchmark</span></span></code></pre></div></div></div><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Benchmark                       (step)   Mode  Cnt          Score          Error  Units</span></span>
<span class="line"><span>RedisChainIdBenchmark.generate       1  thrpt    5  106188349.580 ± 26035022.285  ops/s</span></span>
<span class="line"><span>RedisChainIdBenchmark.generate     100  thrpt    5  112276460.950 ±  4091990.852  ops/s</span></span>
<span class="line"><span>RedisChainIdBenchmark.generate    1000  thrpt    5  110181522.770 ± 15531341.449  ops/s</span></span></code></pre></div><div class="vp-code-group vp-adaptive-theme"><div class="tabs"><input type="radio" name="group-_S94e" id="tab-NxlpbFI" checked><label data-title="Gradle" for="tab-NxlpbFI">Gradle</label><input type="radio" name="group-_S94e" id="tab-_mnT7h3"><label data-title="Java" for="tab-_mnT7h3">Java</label></div><div class="blocks"><div class="language-shell vp-adaptive-theme active"><button title="Copy Code" class="copy"></button><span class="lang">shell</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">gradle</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> cosid-jdbc:jmh</span></span></code></pre></div><div class="language-shell vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">shell</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">java</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -jar</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> cosid-jdbc/build/libs/cosid-jdbc-1.8.6-jmh.jar</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -bm</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> thrpt</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -wi</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> 1</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -rf</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> json</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -f</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> 1</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> MySqlChainIdBenchmark</span></span></code></pre></div></div></div><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Benchmark                       (step)   Mode  Cnt          Score         Error  Units</span></span>
<span class="line"><span>MySqlChainIdBenchmark.generate       1  thrpt    5  110020245.619 ± 4514432.472  ops/s</span></span>
<span class="line"><span>MySqlChainIdBenchmark.generate     100  thrpt    5  111589201.024 ± 1565714.192  ops/s</span></span>
<span class="line"><span>MySqlChainIdBenchmark.generate    1000  thrpt    5  115287146.614 ± 4471990.880  ops/s</span></span></code></pre></div><h3 id="每次操作耗时的百分位数-us-op" tabindex="-1">每次操作耗时的百分位数(us/op) <a class="header-anchor" href="#每次操作耗时的百分位数-us-op" aria-label="Permalink to &quot;每次操作耗时的百分位数(us/op)&quot;">​</a></h3><blockquote><p><a href="https://zh.wikipedia.org/wiki/%E7%99%BE%E5%88%86%E4%BD%8D%E6%95%B0" target="_blank" rel="noreferrer">百分位数</a> ，统计学术语，若将一组数据从小到大排序，并计算相应的累计百分点，则某百分点所对应数据的值，就称为这百分点的百分位数，以Pk表示第k百分位数。百分位数是用来比较个体在群体中的相对地位量数。</p></blockquote><p align="center"><img src="`+d+`" alt="Percentile-Sample-Of-SegmentChainId"></p><div class="language-shell vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">shell</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">java</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -jar</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> cosid-redis/build/libs/cosid-redis-1.8.6-jmh.jar</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -bm</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> sample</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -wi</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> 1</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -rf</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> json</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -f</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> 1</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -tu</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> us</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> step_1000</span></span></code></pre></div><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Benchmark                                            Mode      Cnt   Score    Error  Units</span></span>
<span class="line"><span>RedisChainIdBenchmark.step_1000                    sample  1336271   0.024 ±  0.001  us/op</span></span>
<span class="line"><span>RedisChainIdBenchmark.step_1000:step_1000·p0.00    sample              ≈ 0           us/op</span></span>
<span class="line"><span>RedisChainIdBenchmark.step_1000:step_1000·p0.50    sample            0.041           us/op</span></span>
<span class="line"><span>RedisChainIdBenchmark.step_1000:step_1000·p0.90    sample            0.042           us/op</span></span>
<span class="line"><span>RedisChainIdBenchmark.step_1000:step_1000·p0.95    sample            0.042           us/op</span></span>
<span class="line"><span>RedisChainIdBenchmark.step_1000:step_1000·p0.99    sample            0.042           us/op</span></span>
<span class="line"><span>RedisChainIdBenchmark.step_1000:step_1000·p0.999   sample            0.042           us/op</span></span>
<span class="line"><span>RedisChainIdBenchmark.step_1000:step_1000·p0.9999  sample            0.208           us/op</span></span>
<span class="line"><span>RedisChainIdBenchmark.step_1000:step_1000·p1.00    sample           37.440           us/op</span></span></code></pre></div><div class="language-shell vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">shell</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">java</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -jar</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> cosid-jdbc/build/libs/cosid-jdbc-1.8.6-jmh.jar</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -bm</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> sample</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -wi</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> 1</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -rf</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> json</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -f</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> 1</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -tu</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> us</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> step_1000</span></span></code></pre></div><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Benchmark                                            Mode      Cnt    Score   Error  Units</span></span>
<span class="line"><span>MySqlChainIdBenchmark.step_1000                    sample  1286774    0.024 ± 0.001  us/op</span></span>
<span class="line"><span>MySqlChainIdBenchmark.step_1000:step_1000·p0.00    sample               ≈ 0          us/op</span></span>
<span class="line"><span>MySqlChainIdBenchmark.step_1000:step_1000·p0.50    sample             0.041          us/op</span></span>
<span class="line"><span>MySqlChainIdBenchmark.step_1000:step_1000·p0.90    sample             0.042          us/op</span></span>
<span class="line"><span>MySqlChainIdBenchmark.step_1000:step_1000·p0.95    sample             0.042          us/op</span></span>
<span class="line"><span>MySqlChainIdBenchmark.step_1000:step_1000·p0.99    sample             0.042          us/op</span></span>
<span class="line"><span>MySqlChainIdBenchmark.step_1000:step_1000·p0.999   sample             0.083          us/op</span></span>
<span class="line"><span>MySqlChainIdBenchmark.step_1000:step_1000·p0.9999  sample             0.208          us/op</span></span>
<span class="line"><span>MySqlChainIdBenchmark.step_1000:step_1000·p1.00    sample           342.528          us/op</span></span></code></pre></div><h2 id="snowflakeid" tabindex="-1">SnowflakeId <a class="header-anchor" href="#snowflakeid" aria-label="Permalink to &quot;SnowflakeId&quot;">​</a></h2><div class="vp-code-group vp-adaptive-theme"><div class="tabs"><input type="radio" name="group-MhkXT" id="tab-acivcj-" checked><label data-title="Gradle" for="tab-acivcj-">Gradle</label><input type="radio" name="group-MhkXT" id="tab-I0uGkX1"><label data-title="Java" for="tab-I0uGkX1">Java</label></div><div class="blocks"><div class="language-shell vp-adaptive-theme active"><button title="Copy Code" class="copy"></button><span class="lang">shell</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">gradle</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> cosid-core:jmh</span></span></code></pre></div><div class="language-shell vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">shell</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">java</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -jar</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> cosid-core/build/libs/cosid-core-1.8.6-jmh.jar</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -bm</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> thrpt</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -wi</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> 1</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -rf</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> json</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> -f</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;"> 1</span></span></code></pre></div></div></div><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Benchmark                                                    Mode  Cnt        Score   Error  Units</span></span>
<span class="line"><span>SnowflakeIdBenchmark.millisecondSnowflakeId_friendlyId      thrpt       4020311.665          ops/s</span></span>
<span class="line"><span>SnowflakeIdBenchmark.millisecondSnowflakeId_generate        thrpt       4095403.859          ops/s</span></span>
<span class="line"><span>SnowflakeIdBenchmark.safeJsMillisecondSnowflakeId_generate  thrpt        511654.048          ops/s</span></span>
<span class="line"><span>SnowflakeIdBenchmark.safeJsSecondSnowflakeId_generate       thrpt        539818.563          ops/s</span></span>
<span class="line"><span>SnowflakeIdBenchmark.secondSnowflakeId_generate             thrpt       4206843.941          ops/s</span></span></code></pre></div><h2 id="cosidintervalshardingalgorithm" tabindex="-1">CosIdIntervalShardingAlgorithm <a class="header-anchor" href="#cosidintervalshardingalgorithm" aria-label="Permalink to &quot;CosIdIntervalShardingAlgorithm&quot;">​</a></h2><table tabindex="0"><thead><tr><th><strong>PreciseShardingValue</strong></th><th><strong>RangeShardingValue</strong></th></tr></thead><tbody><tr><td><img src="`+n+'"></td><td><img src="'+i+`"></td></tr></tbody></table><div class="language-shell vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">shell</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">gradle</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> cosid-shardingsphere:jmh</span></span></code></pre></div><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span># JMH version: 1.29</span></span>
<span class="line"><span># VM version: JDK 11.0.13, OpenJDK 64-Bit Server VM, 11.0.13+8-LTS</span></span>
<span class="line"><span># VM options: -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/work/CosId/cosid-shardingsphere/build/tmp/jmh -Duser.country=CN -Duser.language=zh -Duser.variant</span></span>
<span class="line"><span># Blackhole mode: full + dont-inline hint</span></span>
<span class="line"><span># Warmup: 1 iterations, 10 s each</span></span>
<span class="line"><span># Measurement: 1 iterations, 10 s each</span></span>
<span class="line"><span># Timeout: 10 min per iteration</span></span>
<span class="line"><span># Threads: 1 thread, will synchronize iterations</span></span>
<span class="line"><span># Benchmark mode: Throughput, ops/time</span></span>
<span class="line"><span>Benchmark                                                         (days)   Mode  Cnt         Score   Error  Units</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time      10  thrpt       53279788.772          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time     100  thrpt       38114729.365          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time    1000  thrpt       32714318.129          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time   10000  thrpt       22317905.643          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp            10  thrpt       20028091.211          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp           100  thrpt       19272744.794          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp          1000  thrpt       17814417.856          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp         10000  thrpt       12384788.025          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time        10  thrpt       18716732.080          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time       100  thrpt        8436553.492          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time      1000  thrpt        1655952.254          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time     10000  thrpt         185348.831          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_range_timestamp              10  thrpt        9410931.643          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_range_timestamp             100  thrpt        5792861.181          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_range_timestamp            1000  thrpt        1585344.761          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.cosid_range_timestamp           10000  thrpt         196663.812          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.office_precise_timestamp           10  thrpt          72189.800          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.office_precise_timestamp          100  thrpt          11245.324          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.office_precise_timestamp         1000  thrpt           1339.128          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.office_precise_timestamp        10000  thrpt            113.396          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.office_range_timestamp             10  thrpt          64679.422          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.office_range_timestamp            100  thrpt           4267.860          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.office_range_timestamp           1000  thrpt            227.817          ops/s</span></span>
<span class="line"><span>IntervalShardingAlgorithmBenchmark.office_range_timestamp          10000  thrpt              7.579          ops/s</span></span></code></pre></div><h2 id="cosidmodshardingalgorithm" tabindex="-1">CosIdModShardingAlgorithm <a class="header-anchor" href="#cosidmodshardingalgorithm" aria-label="Permalink to &quot;CosIdModShardingAlgorithm&quot;">​</a></h2><table tabindex="0"><thead><tr><th><strong>PreciseShardingValue</strong></th><th><strong>RangeShardingValue</strong></th></tr></thead><tbody><tr><td><img src="`+e+'"></td><td><img src="'+p+`"></td></tr></tbody></table><div class="language-shell vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">shell</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6F42C1;--shiki-dark:#B392F0;">gradle</span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;"> cosid-shardingsphere:jmh</span></span></code></pre></div><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span># JMH version: 1.29</span></span>
<span class="line"><span># VM version: JDK 11.0.13, OpenJDK 64-Bit Server VM, 11.0.13+8-LTS</span></span>
<span class="line"><span># VM options: -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/work/CosId/cosid-shardingsphere/build/tmp/jmh -Duser.country=CN -Duser.language=zh -Duser.variant</span></span>
<span class="line"><span># Blackhole mode: full + dont-inline hint</span></span>
<span class="line"><span># Warmup: 1 iterations, 10 s each</span></span>
<span class="line"><span># Measurement: 1 iterations, 10 s each</span></span>
<span class="line"><span># Timeout: 10 min per iteration</span></span>
<span class="line"><span># Threads: 1 thread, will synchronize iterations</span></span>
<span class="line"><span># Benchmark mode: Throughput, ops/time</span></span>
<span class="line"><span>Benchmark                                     (divisor)   Mode  Cnt          Score   Error  Units</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.cosid_precise          10  thrpt       121431137.111          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.cosid_precise         100  thrpt       119947284.141          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.cosid_precise        1000  thrpt       113095657.321          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.cosid_precise       10000  thrpt       108435323.537          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.cosid_precise      100000  thrpt        84657505.579          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.cosid_range            10  thrpt        37397323.508          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.cosid_range           100  thrpt        16905691.783          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.cosid_range          1000  thrpt         2969820.981          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.cosid_range         10000  thrpt          312881.488          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.cosid_range        100000  thrpt           31581.396          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.office_precise         10  thrpt         9135460.160          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.office_precise        100  thrpt         1356582.418          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.office_precise       1000  thrpt          104500.125          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.office_precise      10000  thrpt            8619.933          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.office_precise     100000  thrpt             629.353          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.office_range           10  thrpt         5535645.737          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.office_range          100  thrpt           83271.925          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.office_range         1000  thrpt             911.534          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.office_range        10000  thrpt               9.133          ops/s</span></span>
<span class="line"><span>ModShardingAlgorithmBenchmark.office_range       100000  thrpt               0.208          ops/s</span></span></code></pre></div>`,28)]))}const y=t(o,[["render",c]]);export{_ as __pageData,y as default};
