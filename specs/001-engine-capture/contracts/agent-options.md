# Contract: Agent Options String

**Feature**: `001-engine-capture`

## Invocation

```
java -javaagent:simula-agent.jar[=key=value[;key=value...]] ...
```

The option string (after `=`) is a `;`-separated list of `key=value` pairs.
Keys are case-insensitive; whitespace around keys/values is trimmed.

| Key | Value | Default | Effect |
|-----|-------|---------|--------|
| `observer` | fully-qualified class name | none | Start the observer hook thread |
| `timeout` | integer seconds ≥ 1 | `30` | Observer/accessor wait budget |
| `supervise` | `true` / `false` (case-insensitive) | `false` | Inject `isSupervised = true` into `EngineImpl.checkSupervision()` so the framework registers a `SimulaSupervisor` |

## Parsing rules

- An empty option string or absent `=` yields defaults.
- A pair without `=`, an empty key, or an unknown key is ignored with a single
  `[simula-agent] WARN` line (FR-008).
- A non-integer or `< 1` timeout falls back to the default with a warning.
- `supervise` is true only for the exact value `true` (any case); any other value is `false`.
- Malformed input NEVER aborts the JVM; parsing failures degrade to defaults.

## Examples

```
-javaagent:simula-agent.jar                                  # capture only
-javaagent:simula-agent.jar=observer=com.acme.MyObserver     # + observer hook
-javaagent:simula-agent.jar=observer=com.acme.MyObserver;timeout=5
-javaagent:simula-agent.jar=supervise=true                   # + framework supervision
```

## Exit behavior

The agent installs no shutdown hooks that alter the application's exit code; the
application's exit code and stdout are unaffected by the agent (SC-002).
