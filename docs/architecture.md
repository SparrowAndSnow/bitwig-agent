# Architecture and ownership

Bitwig Agent is a **thin, bounded control bridge**, not a music-planning service, autonomous session manager or complete project model. The agent chooses a plan within the user's permission, discovers current targets, issues a request and interprets the observed result. Bitwig owns project state and the audio engine. The bridge owns transport, validation, bounded observations and domain-specific admission/completion checks.

This guide describes the Java HTTP MCP implementation. [PROTOCOL.md](../PROTOCOL.md) remains the detailed contract for fields, limits, side effects and recovery boundaries; [VERIFICATION.md](../VERIFICATION.md) describes evidence rather than architecture promises.

## Interfaces and process boundaries

| Boundary | Interface | Owner and responsibility |
|---|---|---|
| Human to agent | Task, scope and explicit permissions | Human owns acceptance and permission; agent owns planning and evidence interpretation |
| MCP client to Java extension | Streamable HTTP at `http://127.0.0.1:61172/mcp` | MCP discovery, bounded requests and structured/text results |
| Network handler to controller host | Scheduled domain operation | Java validates and schedules each request on the controller thread |
| Controller to host model | Public Controller API | Domain-specific targeting, observations and write admission |
| User to extension installation | Copy the extension, or run the optional installer | Separate filesystem operation with backups for installer-managed replacements |

The endpoint binds only to IPv4 loopback and trusts local processes. `BITWIG_AGENT_PORT` is read from the Bitwig process environment. Use that port in the MCP client URL.

## Java ownership

The main sources are under [`extension/src/main/java/dev/bitwigagent`](https://github.com/BANANASJIM/bitwig-agent/tree/main/extension/src/main/java/dev/bitwigagent).

- `BitwigAgentExtension` creates the bridge/listener, forwards controller flushes and closes services on exit.
- `McpHttpServer` handles HTTP and MCP framing. `McpTools` exposes the bundled tool catalog, checks its names against the host registry and forwards calls with deadlines. The catalog lives in `extension/src/main/resources/bitwig-agent-mcp-tools.json`.
- `HostBridge` composes modules, keeps dispatch and validation registries aligned, validates requests and schedules each `HostOperation` through `HostServices`.
- `HostServices` owns shared host proxies and scheduling. `HostOperation` tracks deadlines and completion. Host API work crosses the scheduler boundary rather than running on the network thread.
- `DeviceSources` owns fixed root/layer/child addresses and continuity observations. `ParameterModule` owns continuously observed source-specific parameter data and the single shared active binding/request. Switching flat or nested targets invalidates the prior binding.
- Discovery, Actions, transport, mix, devices and Launcher/MIDI have separate domain modules and observations. Their result semantics differ: a dispatch-only Action is not equivalent to an acknowledged parameter change.
Private Grid, modulator and Launcher settings modules remain in the source tree for earlier builds and are excluded from the runtime registry.

## Request lifecycle and outcomes

1. The agent inspects the installed method schema and obtains fresh discovery/target data. The user, not a token or tool description, supplies authorization.
2. The client sends `tools/call` over HTTP. The request ID correlates the response and the optional `timeoutMs` bounds host work.
3. The loopback transport admits the request and Java validates/routes it. Host work is scheduled with a monotonic deadline; deferred mutations check that deadline too.
4. The domain rechecks the relevant target, context and safety observations. If eligible, it dispatches the operation and observes the method-specific completion evidence.
5. The reply carries a result or an error with its outcome. MCP preserves that distinction. The agent must inspect per-field verification, mutation/dispatch flags and completion—not merely whether a JSON result exists.

`not_dispatched` means the client did not dispatch a request; `not_applied` is a host/domain rejection before the relevant operation was applied. `unknown` means the operation may have occurred. A transport timeout after dispatch, late response failure or stale target after dispatch must not become "failed, therefore retry." Some successful envelopes also report `completion: unknown`; an `observed` status does not cancel that uncertainty.

The deadline is not cancellation or rollback. The extension dispatches each request once. One call can have several native effects, and undo-aware native dispatch does not establish one-call/one-Undo grouping.

On uncertainty, stop the writer, retain the response and ownership record, and reconcile through authorized inspection. Do not blindly retry through either the API or GUI. Unresolved Launcher mutations and native topology lifetimes have specific quarantine rules. Topology admission can survive extension classloader replacement in the same JVM; a required full host restart and inspection does not prove rollback. Follow the exact [protocol recovery boundaries](../PROTOCOL.md#shared-topology-lifecycle-admission), not an improvised reset.

## What a target means

Public track UUIDs, root device indices and exact names address the **currently observed bank**. Direct parameters add source-bound context tokens; Remote Controls have an observed-page context; one-hop layer targets add discovery/context tokens. These mechanisms reject important stale-target cases, but are not persistent physical identity, a project revision lock or atomic exclusion of all host/user activity.

Names can change, same-metadata replacement can be ambiguous, and module/modulator UUIDs can change on reopen. Reacquire all relevant bindings when targets or project lifetimes change. `null`/unreadable/unsupported data is unknown, never an empty collection, zero, false or permission by default.

## Bounded coverage and compatibility

- Tracks, root devices and Launcher windows are 16 each; sends are eight. Layers expose one hop with two layers and two children per layer. There is no general project paging or arbitrary nested traversal.
- MIDI reads use explicit channel/grid/window bounds and do not establish lossless arbitrary-clip export; selection/grid settling is not a general acknowledgement barrier.
- Arranger clip editing, automation curves and dedicated master/cue tools are outside the structured surface.
- See [Verification](../VERIFICATION.md) for the earlier host and build combinations that were exercised.

## Evidence is layered

Schema validation, package consumption, MCP startup, host connectivity, dispatch, model readback, GUI observation, audio rendering/listening, Undo/Redo and save/reopen are distinct observations. None automatically promotes the next layer to passed. The bridge intentionally reports bounded results rather than claiming a plan is musically correct or persisted. The human still owns acceptance.
