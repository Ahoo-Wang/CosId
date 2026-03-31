# CosId Documentation

Source code for the CosId documentation site. This repository contains the documentation for [CosId](https://github.com/Ahoo-Wang/CosId), a universal, flexible, and high-performance distributed ID generator.

## Quick Start

```bash
pnpm install
pnpm run docs:dev
```

## Available Scripts

```sh
pnpm run docs:dev   # Start development server with hot reload
pnpm run docs:build # Build documentation for production
pnpm run docs:preview # Preview production build locally
```

## Documentation Structure

```
docs/
├── en/                      # English documentation
│   ├── guide/               # Getting started, guides, tutorials
│   │   ├── getting-started.md
│   │   ├── introduction.md
│   │   ├── cosid-generator.md
│   │   ├── segment.md
│   │   ├── segment-chain.md
│   │   ├── snowflake.md
│   │   ├── id-converter.md
│   │   ├── provider.md
│   │   ├── advanced/
│   │   ├── extensions/
│   │   ├── faq/
│   │   └── sharding/
│   ├── reference/           # Configuration reference
│   │   └── config/
│   ├── public/             # Static assets (images, logos)
│   └── index.md            # Home page
└── zh/                     # Chinese documentation
    ├── guide/
    ├── reference/
    ├── public/
    └── index.md
```

## How to Contribute

Contributions to improve documentation are welcome! Please refer to the main CosId project's [Contributing Guide](https://github.com/Ahoo-Wang/CosId/blob/main/CONTRIBUTING.md) for more details.

## Links

- [CosId Main Repository](https://github.com/Ahoo-Wang/CosId)
- [Online Documentation](https://cosid.ahoo.me/)
- [Gitee Mirror](https://gitee.com/AhooWang/CosId)
