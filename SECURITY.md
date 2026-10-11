# Security Policy

## Supported versions

| Version | Spring Boot | Supported                         |
|---------|-------------|-----------------------------------|
| 3.x     | 4.x         | Yes, fixes go to the latest minor |
| 2.x     | 3.x         | Critical fixes only               |
| 1.x     | 2.x         | No                                |

## Reporting a vulnerability

Please **do not** open a public issue, discussion or pull request for a security problem.

Report it privately instead:

- through [GitHub private vulnerability reporting](https://github.com/Ahoo-Wang/CosId/security/advisories/new), or
- by email to ahoowang@qq.com with the subject `[CosId Security]`.

Include the affected version and module, the configuration involved, and steps or code to reproduce.
Areas that are security sensitive in CosId include the `cosid-proxy-server` HTTP API and its authentication,
and anything that can make two instances hand out the same ID (machine ID leases, clock handling, segment
distribution).

## What happens next

- We acknowledge the report within 7 days.
- We confirm the issue, agree on a fix and a disclosure date with you, and prepare patched releases.
- The fix is published together with a GitHub security advisory that credits you, unless you prefer otherwise.
