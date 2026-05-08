# Fixing ClockTooManyBackwardsException and Setting Up Actuator Monitoring

This guide addresses two issues: resolving the `ClockTooManyBackwardsException` in production and configuring Spring Boot Actuator to monitor ID generation metrics.

---

## Part 1: Resolving ClockTooManyBackwardsException

### Root Cause

CosId's SnowflakeId relies on a monotonically increasing system clock. When the system clock moves **backward**, the `ClockBackwardsSynchronizer` detects it and tries to recover. The `DefaultClockBackwardsSynchronizer` has three tiers of response:

1. **No action** -- clock is normal, no backwards detected.
2. **Spin wait** -- if backwards is within `spinThreshold` (default: 1ms), it spins until the clock catches up.
3. **Thread sleep** -- if backwards is within `brokenThreshold` (default: 500ms), it sleeps for the backwards duration.
4. **Throw `ClockTooManyBackwardsException`** -- if backwards exceeds `brokenThreshold`, the generator cannot safely recover and throws this exception.

This means your production server's clock moved backward by more than 500ms.

### Common Causes in Production

- **NTP sync corrections**: A large NTP adjustment can jump the clock backward.
- **VM live migration**: Cloud providers migrating VMs between hosts can cause clock jumps.
- **Manual clock adjustments**: Someone manually changed the system time.

### Solutions

#### Solution A: Increase the `brokenThreshold` (recommended if backwards is under a few seconds)

Configure the clock backwards tolerance in your `application.yml`:

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis  # your backend
    clock-backwards:
      spin-threshold: 1       # ms - spin wait threshold (default: 1)
      broken-threshold: 2000  # ms - increase from default 500 to 2000
  snowflake:
    enabled: true
    share:
      enabled: true
```

The key properties are under `cosid.machine.clock-backwards`:
- `spin-threshold`: How long to spin-wait (default 1ms). Rarely needs changing.
- `broken-threshold`: The maximum tolerable clock backwards duration in milliseconds (default 500ms). Increase this if your environment occasionally has larger NTP corrections.

These map directly to `DefaultClockBackwardsSynchronizer`'s constructor parameters. The bean is created in `CosIdMachineAutoConfiguration`.

#### Solution B: Fix NTP Configuration (infrastructure level)

Configure NTP to use slewed adjustments (gradual correction) instead of stepped adjustments:

```bash
# /etc/chrony.conf (chrony)
# Allow only slewed corrections, never step the clock backward
makestep 0.1 3

# Or for ntpd, add to /etc/ntp.conf
tinker panic 0
```

#### Solution C: Use a Custom ClockBackwardsSynchronizer (advanced)

You can provide your own bean to override the default:

```java
@Bean
public ClockBackwardsSynchronizer clockBackwardsSynchronizer() {
    // spinThreshold=1ms, brokenThreshold=5000ms (5 seconds)
    return new DefaultClockBackwardsSynchronizer(1, 5000);
}
```

Since `CosIdMachineAutoConfiguration` uses `@ConditionalOnMissingBean`, your bean will take precedence over the auto-configured one.

---

## Part 2: Setting Up Actuator Monitoring

### Step 1: Add the Actuator Capability

CosId uses Gradle feature variants for optional capabilities. You need to require the `actuator-support` capability.

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
        capabilities {
            requireCapability("me.ahoo.cosid:actuator-support")
        }
    }
}
```

**Gradle (Groovy DSL):**

```groovy
dependencies {
    implementation platform("me.ahoo.cosid:cosid-bom")
    implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
        capabilities {
            requireCapability("me.ahoo.cosid:actuator-support")
        }
    }
}
```

This pulls in `spring-boot-starter-actuator` transitively and enables CosId's actuator auto-configuration (`CosIdEndpointAutoConfiguration`).

### Step 2: Expose Actuator Endpoints

CosId registers three custom actuator endpoints plus a health indicator. Configure them in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: cosid,cosidGenerator,cosidStringGenerator,health
  endpoint:
    health:
      show-details: always
```

### Available Endpoints

| Endpoint | Path | Description |
|----------|------|-------------|
| **CosIdEndpoint** | `GET /actuator/cosid` | Statistics for all registered ID generators (returns `Map<String, IdGeneratorStat>`) |
| **CosIdEndpoint (named)** | `GET /actuator/cosid/{name}` | Statistics for a specific named ID generator |
| **CosIdGeneratorEndpoint** | `GET /actuator/cosidGenerator` | Generate a long ID from the share generator |
| **CosIdGeneratorEndpoint (named)** | `GET /actuator/cosidGenerator/{name}` | Generate a long ID from a named generator |
| **CosIdStringGeneratorEndpoint** | `GET /actuator/cosidStringGenerator` | Generate a String ID from the share generator |
| **CosIdStringGeneratorEndpoint (named)** | `GET /actuator/cosidStringGenerator/{name}` | Generate a String ID from a named generator |
| **MachineIdHealthIndicator** | `GET /actuator/health` | Reports UP/DOWN based on machine ID guardian state |

### What You Can Monitor

- **`/actuator/cosid`** -- Returns stats for every registered ID generator, including `IdGeneratorStat` details. This is your primary monitoring endpoint for ID generation metrics.
- **`/actuator/health`** -- The `MachineIdHealthIndicator` checks all guardian states. If any machine ID guard has failed (e.g., lost its machine ID lease), the health check reports DOWN with the error cause and `guardAt` timestamp. This is critical for catching machine ID conflicts early, which can lead to duplicate IDs.
- **`/actuator/cosidGenerator`** -- Useful for ad-hoc testing of ID generation via HTTP.

### Example Health Check Response

When the machine ID guardian detects a problem, the health endpoint returns:

```json
{
  "status": "DOWN",
  "components": {
    "machineId": {
      "status": "DOWN",
      "details": {
        "guardAt": "2026-05-08T10:30:00Z",
        "error": "..."
      }
    }
  }
}
```

### Complete Configuration Example

Here is a full `application.yml` combining both the clock fix and actuator monitoring:

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis
    guarder:
      enabled: true
      safe-guard-duration: 10m
    clock-backwards:
      spin-threshold: 1
      broken-threshold: 2000  # tolerate up to 2 seconds of clock backwards
  snowflake:
    enabled: true
    share:
      enabled: true
    provider:
      order_id:
        converter:
          type: radix
          prefix: ORD-

management:
  endpoints:
    web:
      exposure:
        include: cosid,cosidGenerator,cosidStringGenerator,health
  endpoint:
    health:
      show-details: always
```

### Key Source Files Referenced

- Clock backwards handling: `cosid-core/src/main/java/me/ahoo/cosid/machine/DefaultClockBackwardsSynchronizer.java` (default `brokenThreshold=500ms`, `spinThreshold=1ms`)
- Exception: `cosid-core/src/main/java/me/ahoo/cosid/snowflake/exception/ClockTooManyBackwardsException.java`
- Machine properties (configurable thresholds): `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/machine/MachineProperties.java` (inner class `ClockBackwards`)
- Actuator endpoints: `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/actuate/`
- Health indicator: `cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/machine/MachineIdHealthIndicator.java`
- Actuator capability registration: `cosid-spring-boot-starter/build.gradle.kts` (feature `actuatorSupport`, capability `actuator-support`)
