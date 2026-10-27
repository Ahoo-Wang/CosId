# 5.0.0-RC1 前全面审查(WIP)

> 状态:**进行中**(2026-10-27 起)。本文件是审查的唯一进度记录,每收到一路结果就更新并提交,
> 以便会话中断(额度限制等)后由任何会话接着做。

## 0. 恢复方法(会话中断后从这里继续)

1. `git fetch origin main`,读本文件 §2 的状态表与 §3 的发现。
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
| E 发布就绪 | 进行中 | — |
| 发布门槛(CI) | 运行中:[run 40534275762](https://github.com/Ahoo-Wang/CosId/actions/runs/40534275762) | 报告 PR 待开 |
| #1342 ZK 排除 Netty | CI 中 | — |

## 3. 发现(核实后填写)

_尚无。_

## 4. 修复计划

_待 §3 完成后填写:每条发现 → 归属会话 → PR → 状态。_

## 5. RC1 准入清单

- [ ] 审查 A–E 无未关闭的 BLOCKER
- [ ] 发布门槛报告:全部 PASS,或每个例外有数据并经用户裁定
- [ ] #1342 合入(L14)
- [ ] 遗留事项 L1–L16 中阻塞 RC 的已关闭
- [ ] 发布配置核对通过(E 路)
- [ ] 用户确认发布 RC1
