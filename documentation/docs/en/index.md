---
layout: home
title: Universal, Flexible, High-Performance Distributed ID Generator

hero:
  name: "CosId"
  text: "Universal, Flexible, High-Performance Distributed ID Generator"
  tagline: "CosId 5: uniqueness that depends on no clock, one runtime for every strategy, one starter"
  image:
    src: /logo.png
    alt: CosId
  actions:
    - theme: brand
      text: Quick Start
      link: /guide/getting-started
    - theme: alt
      text: Introduction
      link: /guide/introduction
    - theme: alt
      text: Migrating from 4.x
      link: /guide/migration/from-4x
    - theme: alt
      text: 4.x docs
      link: https://github.com/Ahoo-Wang/CosId/tree/4.x/documentation/docs/en
    - theme: alt
      text: GitHub
      link: https://github.com/Ahoo-Wang/CosId

features:
- title: Unique by construction
  icon: 🛡️
  details: "A machine slot is leased as a time window: successive holders' windows never overlap, a rule kept inside the store. Clock skew, GC pauses and failovers cost availability, never uniqueness."
- title: Every strategy
  icon: 🌐
  details: "Snowflake (any Layout, milliseconds or seconds, JS-safe), the 80-bit CosId, segments that size themselves to a time budget, and periodic segments per year, month or day."
- title: One runtime
  icon: 🧩
  details: "Spring Boot, cosid-server and plain Java all start the same CosIdRuntime from one configuration model; stores are declared once and mixed freely."
- title: Five stores, one contract
  icon: 🗄️
  details: "JDBC (MySQL, MariaDB, PostgreSQL, SQL Server, H2), Redis, MongoDB, ZooKeeper and the remote server implement two primitives, verified by one TCK."
- title: High performance
  icon: 🚀
  details: "No lock and no I/O on the hot path: a segment id is one atomic add, a Snowflake id a short critical section. 5.0 is gated on matching 4.x in every benchmark."
- title: Observable
  icon: 📈
  details: "19 Micrometer meters, a health indicator for the slot lease, and an Actuator endpoint that describes and exercises every generator."
---

::: warning Release candidate
`5.0.0-rc.x` is a release candidate. From RC1 on, the Java API, the `cosid.*` configuration and the storage formats
and protocols are frozen: later 5.x releases only add to them. Production use should still wait for GA; until then,
stay on 4.x there ([4.x docs](https://github.com/Ahoo-Wang/CosId/tree/4.x/documentation/docs/en)).
:::
