# Axon

`CosIdIdentifierFactory` (artifact `cosid-axon`, package `me.ahoo.cosid.axon`) gives Axon 4's identifiers as CosId text ids. Tested with Axon 4.13.
Axon 5 has no `IdentifierFactory`.

Axon creates its `IdentifierFactory` through `ServiceLoader` and keeps it in a JVM-wide static, so the factory cannot
be handed a runtime. It holds a static stack of **bindings** instead, the only static state in CosId 5:

- `CosIdIdentifierFactory.bind(runtime, generator)` pushes a binding, and the most recent one gives the identifiers.
  Closing the returned binding removes exactly that one, so closing one of several Spring contexts leaves the others
  bound.
- Spring Boot binds the context's runtime right after it starts and unbinds it right before it closes. The generator is
  `cosid.axon.generator-name`, else the system property `cosid.axon`, else `default`.
- Without Spring: `try (var binding = CosIdIdentifierFactory.bind(runtime, null)) { … }`, or pass
  `new CosIdIdentifierFactory(runtime, generator)` where Axon takes a factory.
- With nothing bound, the factory throws `IllegalStateException` saying how to bind a runtime, as 4.x did.
