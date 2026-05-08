# Creating a SegmentChainId Manually with Redis and Radix62 + Prefix

## Dependencies

You need two CosId modules on your classpath:

- `me.ahoo.cosid:cosid-core` — core ID generation (SegmentChainId, converters)
- `me.ahoo.cosid:cosid-spring-redis` — Redis-based segment distributor

If using Gradle:

```groovy
implementation("me.ahoo.cosid:cosid-core")
implementation("me.ahoo.cosid:cosid-spring-redis")
```

## Full Example

```java
import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.StringSegmentId;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributor;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class ManualSegmentChainExample {

    public static void main(String[] args) {

        // 1. Create the Redis connection and StringRedisTemplate
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6379);
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);

        // 2. Create the Redis-based segment distributor
        //    Parameters: namespace, name, offset, step, redisTemplate
        //    - namespace: logical grouping for the ID space
        //    - name: unique name within the namespace
        //    - offset: starting value (0 = start from 0)
        //    - step: how many IDs per segment (larger = fewer Redis calls, but more potential gaps)
        SpringRedisIdSegmentDistributor distributor = new SpringRedisIdSegmentDistributor(
            "my-business",    // namespace
            "order-id",       // name
            0,                // offset
            100,              // step (IDs per segment)
            redisTemplate
        );
        distributor.ensureOffset(); // initializes the Redis counter on first use

        // 3. Create the SegmentChainId (the actual ID generator)
        //    Uses default TTL (forever) and default safe distance (2)
        SegmentChainId segmentChainId = new SegmentChainId(distributor);

        // 4. Create the converter: Radix62 with "BIZ-" prefix
        IdConverter converter = new PrefixIdConverter(
            "BIZ-",
            Radix62IdConverter.PAD_START  // padded to fixed 11-char radix62 string
        );

        // 5. Wrap with StringSegmentId to use the custom converter
        StringSegmentId idGenerator = new StringSegmentId(segmentChainId, converter);

        // 6. Generate IDs
        for (int i = 0; i < 10; i++) {
            long numericId = idGenerator.generate();
            String stringId = idGenerator.generateAsString();
            System.out.println("numeric: " + numericId + " -> string: " + stringId);
        }
    }
}
```

## How It Works — Step by Step

1. **Redis connection**: A `StringRedisTemplate` is created from a Lettuce connection factory. CosId's Redis distributor uses `StringRedisTemplate.opsForValue().increment()` as an atomic counter to allocate segment ranges. The key format is `cosid:{namespace.name}.adder`.

2. **Segment distributor** (`SpringRedisIdSegmentDistributor`): This implements `IdSegmentDistributor`. When asked for the next segment, it atomically increments the Redis counter by the step size and returns the new max ID. For example, with step=100, the first call returns 100, the second returns 200, and so on.

3. **SegmentChainId**: This is the high-performance segment chain algorithm. It chains segments together with a lock-free prefetch worker that runs in the background. The prefetch worker dynamically adjusts how many segments to prefetch based on consumption rate. Key parameters:
   - `idSegmentTtl` (default: forever) — how long a segment remains valid
   - `safeDistance` (default: 2) — minimum number of segments to keep prefetched ahead

4. **IdConverter chain**: The `PrefixIdConverter` wraps `Radix62IdConverter.PAD_START`, which encodes the numeric ID as a radix-62 string (characters 0-9, A-Z, a-z) padded to 11 characters, then prepends "BIZ-". For example, numeric ID `1` becomes `"BIZ-00000000001"`.

5. **StringSegmentId**: This decorator wraps the `SegmentChainId` with the custom `IdConverter`, so `generate()` returns the raw long and `generateAsString()` returns the converted string with prefix.

## Sample Output

```
numeric: 1 -> string: BIZ-00000000001
numeric: 2 -> string: BIZ-00000000002
numeric: 3 -> string: BIZ-00000000003
...
```

## Key Files in the Codebase

- `cosid-core/.../segment/SegmentChainId.java` — segment chain ID generator
- `cosid-core/.../segment/StringSegmentId.java` — decorator that applies custom IdConverter
- `cosid-core/.../converter/PrefixIdConverter.java` — prefix decorator for string IDs
- `cosid-core/.../converter/Radix62IdConverter.java` — radix-62 encoder
- `cosid-spring-redis/.../SpringRedisIdSegmentDistributor.java` — Redis-backed distributor
- `cosid-spring-redis/.../SpringRedisIdSegmentDistributorFactory.java` — factory for creating distributors

## Notes

- Call `distributor.ensureOffset()` once after construction to initialize the Redis key with the starting offset value.
- The step size controls the segment size. A larger step means fewer Redis round-trips but potentially more wasted IDs if the application restarts. A step of 100 is a reasonable starting point.
- `SegmentChainId` starts a background prefetch thread automatically via `PrefetchWorkerExecutorService.DEFAULT`.
- If you want variable-length Radix62 strings (no padding), use `Radix62IdConverter.INSTANCE` instead of `PAD_START`.
