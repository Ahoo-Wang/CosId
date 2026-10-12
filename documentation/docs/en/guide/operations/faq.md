# Frequently Asked Questions

## Does CosId require deploying a server?

Although there is no mandatory way to use [CosId](https://github.com/Ahoo-Wang/CosId), it is strongly recommended to use it as a local SDK. Users only need to install the **CosId** dependency package and do some simple configuration ([DEMO](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-example)).

:::tip
Distributed ID is not suitable for server deployment mode (C/S). Using server deployment mode will inevitably introduce network IO (the *Client* calls the *Server* remotely to obtain the ID). Think about why we went to great lengths to eliminate network IO?
:::

## How does PrefetchWorker maintain the safety distance?

- **Timed maintenance**: Every certain period, **PrefetchWorker** actively detects if the safety distance meets the configuration requirements, and if not, executes `NextMaxId` prefetch to ensure safety distance.
- **Passive starvation wake-up**: When the thread acquiring ID acquires ID and there is no available segment, it will try to acquire a new segment, and actively wake up **PrefetchWorker** and tell it that you are too slow, the awakened **PrefetchWorker** will detect if the safety distance needs expansion, and then maintain the safety distance.

## Why ensure monotonic increment as much as possible, even though it's locally monotonic and globally trend-increasing?

From the above discussion, it's not difficult to understand local monotonic increment and global trend increment as a trade-off design result.
However, the downside of global trend increment is ID disorder within the cycle, so optimizing towards monotonic increment as much as possible (reducing ID disorder degree) is the optimization goal. These two points do not conflict.

If you have other questions, please submit your questions to [Issues](https://github.com/Ahoo-Wang/CosId/issues).

