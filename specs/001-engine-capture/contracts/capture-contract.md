# Contract: Framework Capture Contract

**Feature**: `001-engine-capture`

This feature depends on exactly one framework signature. It is declared a
stability contract (Constitution IX): the agent never breaks the host, but the
capture capability assumes this shape.

## The depended-upon signature

```
jpnco.simula.engine.EngineImpl
  private EngineImpl(String title, Engine parent, int timeFactor, ExecutionMode mode)
```

- **Descriptor**: `(Ljava/lang/String;Ljpnco/simula/Engine;I` +
  `Ljpnco/simula/engine/ExecutionMode;)V`
- **Why it is sufficient**: every public `EngineImpl` constructor in the framework
  delegates to this canonical private constructor (verified in framework source
  for `simula` 0.0.1-SNAPSHOT). Transforming it observes all engine creations.
- **Root test**: the `parent` argument (parameter index 1) is `null` for a root
  engine. Child engines pass a non-null parent and are not captured.

## Agent behavior tied to this contract

| Condition | Agent behavior |
|-----------|----------------|
| Class and descriptor present | Advice appends the new instance to the registry when `parent == null` |
| Class absent (no simula in JVM) | Matcher never fires; observer gets `engineUnavailable()` after timeout; one diagnostic |
| Descriptor changed (framework drift) | Matcher never fires; same soft outcome; host unaffected |

## Verification

- `integration/EngineCaptureIT` launches a fixture app on the module path with
  the agent attached and asserts capture (guards the contract on every build).
- SC-002 asserts stdout/exit-code equality with and without the agent.

## Change policy

If the framework legitimately renames this constructor, update:
1. the matcher descriptor in `capture/EngineTransformer`,
2. `@Advice.Argument` indices in `capture/EngineCaptureAdvice` if parameter order
   changes,
3. this contract file,
and keep the soft-degradation tests green.
