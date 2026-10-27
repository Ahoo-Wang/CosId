# 5.0.0-RC1 前全面审查(WIP)

> 状态:**进行中**(2026-10-27 起)。本文件是审查的唯一进度记录,每收到一路结果就更新并提交,
> 以便会话中断(额度限制等)后由任何会话接着做。

## 0. 恢复方法(会话中断后从这里继续)

1. 在任何机器上:`git fetch origin && git switch wip/rc-review`(Draft PR [#1344](https://github.com/Ahoo-Wang/CosId/pull/1344)),读本文件 §2 的状态表与 §3 的发现。WIP 只存在于 GitHub 上的 PR 分支,不依赖本机状态。
2. 状态为"进行中"的审查路,如果没有结果,按 §1 的范围重新派一个只读审查(不跑构建,遵守 `AGENTS.md` 的 CPU 规则)。
3. 所有路都有结果后:逐条在代码中核实 → 填 §4 修复计划 → 按归属派会话修复 → 每个修复 PR 合入后在 §4 勾掉。
4. §5 的 RC 准入清单全部通过后,再请用户决定是否发布 RC1。

相关进度见 [roadmap.md](roadmap.md) §5(阶段、里程碑、遗留事项 L1–L16)。

## 1. 审查分工(只读,对照设计文档 01–11)

| 路 | 范围 | 重点 |
|---|---|---|
| A | `core` | 任何可能重号的路径(时间型临界区与窗口、换绑、CLOSED 哨兵、号段切换与补充、周期滚动与封存、LeaseKeeper/StaticSlot、运行时关闭顺序);并发;API 与 module-info;编解码边界 |
| B | `store/*`(jdbc、redis、mongo、zookeeper、remote)与 `store/tck` | 原子性与不变式 W、封存不重建(S6)、超时映射与资源释放、SQL/Lua 注入与参数编码、与 06 / 模块 README 一致 |
| C | `spring/*`、`server`、`integrations/*`、`examples/*` | server 安全(默认鉴权、常量时间比较、作用域、限制、暴露面)、Spring 装配与禁用生成器、可选依赖缺失时能否启动、集成生命周期、残留 `/v5` |
| D | `migration/` | M1–M3、4.x 各 key 形态映射、先封存后写、W 公式、管理写路径与当前存储格式一致(含 ZK owner 索引)、`config` 转换、purge 与凭据 |
| E | 发布就绪 | 发布模块与 artifactId、BOM、签名与 POM、Docker 镜像与 `latest` 规则、文档站、CI 分支、残留 TODO/@Disabled/临时排除项、哪些遗留事项阻塞 RC |

严重度:**BLOCKER**(阻塞 RC)/ **MAJOR** / **MINOR**。

## 2. 状态

| 路 | 状态 | 结果 |
|---|---|---|
| A core | 进行中 | — |
| B stores | 进行中 | — |
| C spring/server/integrations | 进行中 | — |
| D migration | 进行中 | — |
| E 发布就绪 | ✅ 完成,已核实 | 3 BLOCKER、3 MAJOR、9 MINOR(见 §3.E) |
| 发布门槛(CI) | 运行中:[run 40534275762](https://github.com/Ahoo-Wang/CosId/actions/runs/40534275762),spring-start 组已通过 | 分析 Draft PR [#1345](https://github.com/Ahoo-Wang/CosId/pull/1345)(`docs/jmh/5.0/analysis.md`) |
| #1342 ZK 排除 Netty | CI 中 | — |

## 3. 发现(核实后填写)

### 3.E 发布就绪(main @ e272cdde)

| # | 严重度 | 发现 | 位置 | 修复 | 核实 |
|---|---|---|---|---|---|
| E-B1 | BLOCKER | L14 未合入:发布的 `cosid-store-zookeeper` POM 会带入 Netty 4.1 epoll(Central 上 POM 不可改) | `store/zookeeper/build.gradle.kts:20-26` | 合入 #1342 | ✅ |
| E-B2 | BLOCKER | 文档承诺 `cosid-server` Docker 镜像,但 `main` 没有发布镜像的 workflow | `.github/workflows/`(无 docker-deploy);`documentation/docs/*/guide/server.md` | 新增 `docker-deploy.yml`:tag `v*.*.*` 与 `main`,`ahoowang/cosid-server` + ghcr + aliyun,多架构;`latest` 只在主版本 ≥ 5 的 GA tag 上打 | ✅ |
| E-B3 | BLOCKER | 文档说迁移工具 jar 随 release 附带,但没有任何 workflow 上传 | `package-deploy.yml`;`documentation/docs/*/guide/migration/from-4x.md` | package-deploy 增加 job:`:migration:shadowJar` + `gh release upload`(需 `contents: write`) | ✅ |
| E-M1 | MAJOR | 4.x 的 document-deploy 在 release 时部署到同一个 Pages 站点,GA 后 4.x 的补丁发布会覆盖 5.0 文档站 | 分支 `4.x`:`document-deploy.yml` | 4.x 去掉 release 触发或部署到子路径(改 4.x 需用户同意);**RC 必须以 pre-release 发布**,否则 5.0 RC 文档会覆盖当前 4.x 站点 | — |
| E-M2 | MAJOR | 文档站 javadoc 目录名漏了 `integrations/*` 映射,会生成 `cosid-integrations-jackson` | `document-deploy.yml:75-79` | 增加 `integrations/*) artifact="cosid-${module#integrations/}"`,或改由 Gradle 输出名称 | ✅ |
| E-M3 | MAJOR | 发布到 Central 前没有任何校验 job(发布不可逆) | `package-deploy.yml:66-83` | 部署 job `needs:` 一个跑 `check`(或至少 javadoc + checkPublishedMetadata + 发布到本地仓库)的 job | — |
| E-N1 | MINOR | AGENTS.md 仍写"4.x 代码在 `main`" | `AGENTS.md:9-10` | 改为 `git show 4.x:<path>` | — |
| E-N2 | MINOR | `checkPublishedMetadata` 只在 spring 模块上运行,且不拒绝 `enforced-platform` | `build-logic/.../cosid.spring.gradle.kts:82-128` | 挪到 `cosid.library`,并拒绝任何 enforced-platform | — |
| E-N3 | MINOR | 没有自动检查 BOM 是否列全所有发布模块(目前 16 个一致) | `bom/build.gradle.kts` | 增加根项目检查 | — |
| E-N4 | MINOR | POM 的 SCM `connection` 缺 `scm:git:` 前缀,没有 `developerConnection` | `cosid.publish.gradle.kts:56-59` | 补齐 | — |
| E-N5 | MINOR | roadmap 链接的 rc-review.md 尚未在 main 上 | — | 本 PR(#1344)合入后解决 | ✅ |
| E-N6 | MINOR | 基准代码里残留 `TODO(R1)` | `benchmark/v5/.../V5.java:39,75` | 删除 | — |
| E-N7 | MINOR | jar 内资源(Lua、JDBC schema SQL)与 `server/Dockerfile` 无许可证头 | — | 补头 | — |
| E-N8 | MINOR | codeql `paths-ignore` 仍是 4.x 路径 | `codeql-analysis.yml` | 更新 | — |
| E-N9 | MINOR(存疑) | `release: created` 在保存草稿 release 时也会触发 | `package-deploy.yml:16-17` | 改为 `published` | — |

E 路确认通过的:artifactId 与发布范围、示例/基准/server/迁移工具不发布、源码与 javadoc jar 与签名与 POM、BOM 当前完整、无 enforced-platform 泄漏、Docker 镜像名不与 4.x `cosid-proxy` 冲突、文档站侧边栏与重定向、README 坐标、renovate 与 CI 分支、无 `@Disabled`、临时排除项已清除。
发布前手工步骤:`gradle.properties` 的 `version` 改为 `5.0.0-RC1`,`server/README.md:64` 的硬编码版本同步。

## 4. 修复计划

_待 §3 完成后填写:每条发现 → 归属会话 → PR → 状态。_

## 5. RC1 准入清单

- [ ] 审查 A–E 无未关闭的 BLOCKER
- [ ] 发布门槛报告:全部 PASS,或每个例外有数据并经用户裁定
- [ ] #1342 合入(L14)
- [ ] 遗留事项 L1–L16 中阻塞 RC 的已关闭
- [ ] 发布配置核对通过(E 路)
- [ ] 用户确认发布 RC1

> WIP 通过 Draft PR [#1344](https://github.com/Ahoo-Wang/CosId/pull/1344) 保存:本文件与路线图的更新都推送到分支 `wip/rc-review`,换机器也能继续;在阶段节点合入 `main`。各子会话同样一开工就开 Draft PR,WIP 推送到各自 PR 分支。
