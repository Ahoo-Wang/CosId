# Performance Comparison

TODO

## Core Metrics of Distributed ID Schemes

- **Global (Same Business) Uniqueness**: Uniqueness guarantee is a **necessary condition** for **ID**. Assuming ID is not unique will cause primary key conflicts, which is easy to understand.
    - The so-called global uniqueness usually does not mean uniqueness across all business services, but uniqueness across different deployment replicas of the same business service.
      For example, multiple deployment replicas of the Order service generating `Id` for the `t_order` table require global uniqueness. As for whether the `ID` generated for `t_order_item` is unique with `t_order`, it does not affect the uniqueness constraint and will not cause any side effects.
      The same applies to different business modules. That is, uniqueness mainly solves the ID conflict problem.
- **Orderliness**: The orderliness guarantee is necessary for query data structure algorithms (except Hash algorithms), and is the prerequisite for **binary search** (divide and conquer).
    - MySQL-InnoDB B+ tree is the most widely used. Assuming Id is unordered, the B+ tree, in order to maintain the orderliness of ID, will frequently insert in the middle of the index and move the positions of subsequent nodes, even leading to frequent page splits, which has a huge impact on performance. If we can guarantee the orderliness of ID, this situation is completely different, only needing append write operations. Therefore, the orderliness of ID is very important and is an inevitable characteristic of ID design.
- **Throughput/Performance (ops/time)**: The number of IDs that can be generated per unit time (per second). Generating ID is a very high-frequency operation and is also the most basic. Assuming ID generation is slow, no matter how the system is optimized, better performance cannot be obtained.
    - Generally, we first generate the ID, then perform the write operation. Assuming ID generation is slow, the overall performance upper limit will be limited, which should be easy to understand.
- **Stability (time/op)**: The stability metric can generally be analyzed using **percentile sampling** of each operation time, for example, *[CosId](https://github.com/Ahoo-Wang/CosId)* percentile sampling **P9999=0.208 us/op**, meaning **0% ~ 99.99%** of unit operation times are less than or equal to **0.208 us/op**.
    - [Percentile WIKI](https://en.wikipedia.org/wiki/Percentile): A statistical term. If a set of data is sorted from small to large and the corresponding cumulative percentiles are calculated, then the value corresponding to a certain percentile is called the percentile number, denoted as Pk for the k-th percentile. Percentiles are used to compare the relative position of individuals in a group.
    - Why not use the average *time per operation*: Can the average of Jack Ma's net worth and yours be meaningful?
    - Can the minimum *time per operation* and maximum *time per operation* be used as reference? Because minimum and maximum values only describe the boundary conditions, although they can be used as a reference for stability, they are still not comprehensive. Moreover, *percentiles* already cover these two indicators.
- **Autonomy (Dependency)**: Mainly refers to whether there is dependency on external environment, for example, **segment mode** strongly depends on third-party storage middleware to obtain `NextMaxId`. Autonomy will also affect availability.
- **Availability**: The availability of distributed ID is mainly affected by autonomy, for example, **SnowflakeId** is affected by clock rollback, leading to a short period of unavailability. While **segment mode** is affected by the availability of third-party distributors (`NextMaxId`).
    - [Availability WIKI](https://en.wikipedia.org/wiki/Availability_(system)): The proportion of total available time for a functional individual within a given time interval.
    - MTBF: Mean Time Between Failures
    - MDT: Mean Time To Repair/Recovery
    - Availability=MTBF/(MTBF+MDT)
    - Assuming MTBF is 1 year, MDT is 1 hour, then `Availability=(365*24)/(365*24+1)=0.999885857778792≈99.99%`, which is what we usually call four 9s availability.
- **Adaptability**: Refers to the adaptive ability when facing changes in the external environment. Here we mainly talk about the dynamic scaling of distributed ID performance when facing traffic bursts.
    - **SegmentChainId** can dynamically scale based on **starvation state** for **safe distance**.
    - **SnowflakeId** conventional bit allocation scheme has constant performance of 409.6W, although different TPS performance can be obtained by adjusting the bit allocation scheme, but changing the bit allocation method is destructive, generally determined according to the business scenario and no longer changed.
- **Storage Space**: Still using MySQL-InnoDB B+ tree as an example, secondary indexes store primary key values, the larger the primary key, the more memory cache and disk space it occupies. The less data stored in Page pages, the more disk IO accesses will increase. In short, under the premise of meeting business needs, occupying as little storage space as possible is a good design principle in most scenarios.

## Core Algorithms of Distributed ID

## Bit Partition Algorithm (`SnowflakeId`)

|                                                         |          Performance (Throughput) |          Stability (Percentile) | Autonomy (Dependency)           | Machine Number Allocator                           | Machine Number Recycling | Usage Method                |
|---------------------------------------------------------|-----------------:|-------------------:|-------------------|----------------------------------|-------|:--------------------|
| [CosId](https://github.com/Ahoo-Wang/CosId)             | 4,096,000(ops/s) | P9999=0.244(us/op) | First startup, depends on **machine number allocator** | Manual allocator, K8S, relational database, Redis, ZooKeeper | Supported    | SDK(recommended)/RPC/RESTful |
| [Leaf](https://github.com/Meituan-Dianping/Leaf)        |                  |                    |                   | ZooKeeper                        |       |                     |
| [uid-generator](https://github.com/baidu/uid-generator) |                  |                    |                   | Relational database                           |       |                     |
| [TinyID](https://github.com/didi/tinyid)                |    Does not support **bit partition algorithm** |                    |                   |                                  |

## Segment Algorithm (`SegmentId`)

|                                                         |            Performance (Throughput) |          Stability (Percentile) | Autonomy (Dependency)     | Segment Distributor                  | Adaptability           | Storage Space   | Usage Method                |
|---------------------------------------------------------|-------------------:|-------------------:|-------------|------------------------|---------------|--------|---------------------|
| [CosId](https://github.com/Ahoo-Wang/CosId)             | 127,439,148(ops/s) | P9999=0.208(us/op) | Depends on **segment distributor** | Relational database, Redis, ZooKeeper | Supports `Step` auto-scaling | 64-bit | SDK(recommended)/RPC/RESTful |
| [Leaf](https://github.com/Meituan-Dianping/Leaf)        |                    |                    |             | MySql                  |               |        |                     |
| [uid-generator](https://github.com/baidu/uid-generator) |        Does not support **segment algorithm** |                    |             |                        |               |        |                     |
| [TinyID](https://github.com/didi/tinyid)                |                    |                    |             | Database                    |               |        |                     |


