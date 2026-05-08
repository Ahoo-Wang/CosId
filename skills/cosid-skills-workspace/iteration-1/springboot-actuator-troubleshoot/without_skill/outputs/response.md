# Troubleshooting ClockTooManyBackwardsException and Setting Up Actuator Monitoring

## Part 1: Fixing ClockTooManyBackwardsException

### What Is Happening

`ClockTooManyBackwardsException` is thrown by CosId's `DefaultClockBackwardsSynchronizer` when the system clock moves backwards by more than the configured `brokenThreshold` (default: **500ms**). The flow is:

1. `SnowflakeId.generate()` detects that `currentTimestamp < lastTimestamp` and throws `ClockBackwardsException`.
2. If you have `clockSync: true` (the default), `ClockSyncSnowflakeId` catches that exception and delegates to `ClockBackwardsSynchronizer.syncUninterruptibly()`.
3. The synchronizer checks the backwards duration:
   - **<= 1ms (spinThreshold)**: Spins in a busy-wait loop until the clock catches up.
   - **> 1ms and <= 500ms (brokenThreshold)**: Sleeps for the backwards duration.
   - **> 500ms (brokenThreshold)**: Throws `ClockTooManyBackwardsException`.

### Root Causes on Production

Common causes of clock backwards on production servers:

1. **NTP synchronization jumps**: An NTP daemon steps the clock backwards after a large drift correction.
2. **VM/container live migration**: Cloud providers may cause time jumps during host migration.
3. **Manual time adjustments**: Someone or a misconfigured cron job adjusts system time.

### Solutions

#### Option A: Tune the Clock Backwards Thresholds

Increase the `brokenThreshold` to tolerate larger backwards jumps. In your `application.yml`:

```yaml
cosid:
  machine:
    enabled: true
    clock-backwards:
      spin-threshold: 2        # default is 1 (ms)
      broken-threshold: 2000   # default is 500 (ms), increase to 2s
    distributor:
      type: redis              # or jdbc, zookeeper, etc.
```

This is configured via `MachineProperties.ClockBackwards` and used by `DefaultClockBackwardsSynchronizer` in `CosIdMachineAutoConfiguration`.

#### Option B: Fix NTP Configuration (Recommended)

Configure NTP to slew the clock gradually instead of stepping it:

```
# /etc/chrony.conf (or /etc/ntp.conf)
# For chrony, avoid stepping backwards:
maxslewrate 200
# Let it slew gradually instead of stepping
rtcsync
```

For `ntpd`, add to `/etc/ntp.conf`:
```
tinker panic 0
server <your-ntp-server> iburst
```

The `-x` option tells `ntpd` to only slew (never step), but it is slower at correcting large offsets.

#### Option C: Use a Monotonic Clock Source

On Linux, ensure the JVM uses a monotonic clock source:
```
-XX:+UseLinuxPosixThreadCPUClocks
-Djava.security.egd=file:/dev/./urandom
```

Note: CosId uses `System.currentTimeMillis()` in `MillisecondSnowflakeId`, so the best defense is preventing wall-clock backwards jumps at the OS level.

---

## Part 2: Monitoring ID Generation via Actuator

CosId provides built-in Spring Boot Actuator integration. Here is how to set it up.

### Step 1: Add the Actuator Dependency

CosId's actuator support is a Gradle feature variant. Add both `cosid-spring-boot-starter` and `spring-boot-starter-actuator` to your build:

**Gradle (Kotlin DSL)**:
```kotlin
dependencies {
    implementation("me.ahoo.cosid:cosid-spring-boot-starter:${cosidVersion}")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
```

The `CosIdEndpointAutoConfiguration` is conditionally activated when `org.springframework.boot.actuate.endpoint.annotation.Endpoint` is on the classpath (provided by `spring-boot-starter-actuator`).

### Step 2: Expose Actuator Endpoints

CosId registers three custom actuator endpoints:

| Endpoint | Description |
|---|---|
| `cosid` | Get stat info for all registered ID generators, or a specific one by name |
| `cosidGenerator` | Generate a long ID (share or by name) |
| `cosidStringGenerator` | Generate a string ID (share or by name) |

Plus, the standard `health` endpoint includes machine ID guarding status via `MachineIdHealthIndicator`.

Configure endpoint exposure in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include:
          - cosid
          - cosidGenerator
          - cosidStringGenerator
          - health
  endpoint:
    health:
      show-details: always
```

### Step 3: Query the Endpoints

**Get stats for all ID generators**:
```bash
curl http://localhost:8080/actuator/cosid
```

Returns a map of generator name to `IdGeneratorStat`. For SnowflakeId generators, you get `SnowflakeIdStat` which includes:
- `kind` - generator type
- `epoch` - configured epoch
- `timestampBit`, `machineBit`, `sequenceBit` - bit allocation
- `machineId` - this instance's machine ID
- `lastTimestamp` - last generated timestamp
- `converter` - converter stat

**Get stats for a specific generator**:
```bash
curl http://localhost:8080/actuator/cosid/snowflake_friendly
```

**Check machine ID health**:
```bash
curl http://localhost:8080/actuator/health
```

The `MachineIdHealthIndicator` reports `UP` if all guardian states are healthy, or `DOWN` with error details if any guardian has failed. Failed guardian states include `guardAt` timestamp and the error cause.

**Generate an ID via actuator (useful for smoke testing)**:
```bash
# Generate share ID (long)
curl http://localhost:8080/actuator/cosidGenerator

# Generate named ID (long)
curl http://localhost:8080/actuator/cosidGenerator/snowflake_friendly

# Generate share ID (string)
curl http://localhost:8080/actuator/cosidStringGenerator
```

### Step 4: Enable Swagger/OpenAPI for Actuator (Optional)

If you use Springdoc, you can expose actuator endpoints in the API docs:

```yaml
springdoc:
  show-actuator: true
```

### Complete Example Configuration

Here is a full working configuration combining both the clock backwards fix and actuator monitoring:

```yaml
management:
  endpoints:
    web:
      exposure:
        include:
          - cosid
          - cosidGenerator
          - cosidStringGenerator
          - health
  endpoint:
    health:
      show-details: always

spring:
  application:
    name: my-service
  data:
    redis:
      host: localhost

cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    clock-backwards:
      spin-threshold: 2
      broken-threshold: 2000
    distributor:
      type: redis
    guarder:
      enabled: true
      safe-guard-duration: 5m
  snowflake:
    enabled: true
    share:
      enabled: true
      clock-sync: true
    provider:
      order_id:
        converter:
          type: snowflake_friendly
```

### Key Source Files for Reference

- Clock backwards handling: `cosid-core/src/main/java/me/ahoo/cosid/machine/DefaultClockBackwardsSynchronizer.java`
- Clock backwards exception: `cosid-core/src/main/java/me/ahoo/cosid/snowflake/exception/ClockTooManyBackwardsException.java`
- Clock sync wrapper: `cosid-core/src/main/java/me/ahoo/cosid/snowflake/ClockSyncSnowflakeId.java`
- Machine ID health indicator: `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/machine/MachineIdHealthIndicator.java`
- Actuator endpoints: `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/actuate/`
- Machine properties (threshold config): `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/machine/MachineProperties.java`
- Example with actuator: `examples/cosid-example-redis/src/main/resources/application.yaml`
