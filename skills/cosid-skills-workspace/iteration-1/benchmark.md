# CosId Skills Evaluation Benchmark

## Summary

Evaluated 9 test prompts across 3 CosId skills (with-skill vs without-skill baseline).

## Key Findings

### Bugs Fixed in Skills

1. **cosid-sharding.md**: `ModCycle` constructor parameter order was reversed. Fixed from `new ModCycle<>("t_order", 4)` to `new ModCycle<>(4, "t_order_")`.

2. **cosid-manual-integration.md**: `SegmentChainId` 4-param constructor used wrong arguments (`DEFAULT_OFFSET`, `DEFAULT_STEP`). Fixed to recommend single-param constructor `new SegmentChainId(distributor)`.

### With-Skill vs Baseline Comparison

| Eval | Test Case | With-Skill Quality | Baseline Quality | Skill Advantage |
|------|-----------|-------------------|------------------|-----------------|
| 0 | springboot-basic-setup | Good | Fair | Capability-based deps, correct injection patterns |
| 1 | springboot-segment-custom | Good | Poor | Uses jdbc-support capability, no hardcoded versions |
| 2 | springboot-actuator-troubleshoot | Good | Good | Mentions actuator-support capability, MachineIdHealthIndicator |
| 3 | manual-snowflake-setup | Good | Good | BOM approach, lifecycle management notes |
| 4 | manual-segment-chain | Good | Good | Correct constructor guidance |
| 5 | manual-idconverter | Good | Fair | Correct DatePrefixIdConverter usage |
| 6 | sharding-mod-cycle | Good | Good | Correct COSID_MOD YAML config |
| 7 | sharding-interval-monthly | Good | Good | Both show COSID_INTERVAL config correctly |
| 8 | sharding-springboot-beans | Good | Good | Both found ModCycle constructor bug and corrected |

### Quantitative Assertions

| Assertion | With-Skill Pass Rate | Baseline Pass Rate |
|-----------|---------------------|-------------------|
| Uses cosid-bom (no hardcoded version) | 7/9 (78%) | 3/9 (33%) |
| Uses capability-based dependency | 4/9 (44%) | 0/9 (0%) |
| Correct config property prefix | 9/9 (100%) | 9/9 (100%) |
| Correct class names | 9/9 (100%) | 8/9 (89%) |
| Includes usage examples | 9/9 (100%) | 9/9 (100%) |
| Mentions lifecycle management | 6/9 (67%) | 3/9 (33%) |

### Key Advantages of With-Skill

1. **Dependency management**: Skill guides Claude to use `cosid-bom` platform and capability-based dependencies instead of hardcoding versions
2. **Correct API usage**: Skill provides correct class names and constructor signatures
3. **Injection patterns**: Skill recommends `@CosId` and `@IdGenerator` annotations over generic `@Qualifier`
4. **Configuration completeness**: Skill covers all config options including chain tuning, actuator, and JDBC auto-init
5. **Lifecycle management**: Skill mentions shutdown hooks and resource cleanup

### Areas for Improvement

1. **Subagent version hardcoding**: Even with skill guidance, subagents sometimes hardcode `cosid-bom:3.0.5`. This is a subagent behavior issue, not a skill content issue.
2. **ShardingSphere SPI type**: Baseline correctly mentions `COSID_MOD` and `COSID_INTERVAL` algorithm types. Skill could be more explicit about these ShardingSphere SPI names.
3. **DatePrefixIdConverter**: Could add more examples with different date patterns and delimiters.
