# Bitwig Agent

[![CI](https://github.com/BANANASJIM/bitwig-agent/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/BANANASJIM/bitwig-agent/actions/workflows/ci.yml)
[![GitHub release](https://img.shields.io/github/v/release/BANANASJIM/bitwig-agent?include_prereleases)](https://github.com/BANANASJIM/bitwig-agent/releases)
[![PyPI version](https://img.shields.io/pypi/v/bitwig-agent)](https://pypi.org/project/bitwig-agent/)
[![AUR version](https://img.shields.io/aur/version/bitwig-agent)](https://aur.archlinux.org/packages/bitwig-agent)
[![MIT license](https://img.shields.io/github/license/BANANASJIM/bitwig-agent)](https://github.com/BANANASJIM/bitwig-agent/blob/main/LICENSE)

**Control Bitwig Studio through a local MCP endpoint.** The Bitwig Agent extension exposes track, device, transport and Launcher MIDI tools over Streamable HTTP.

## Quickstart

### 1. Install

For this checkout, build `BitwigAgent.bwextension` with `python3 scripts/build.py`, or use an extension built from the HTTP MCP version. Copy the file into Bitwig's user Extensions folder. The extension runs with Bitwig's bundled Java runtime. Python is used by the source build and optional installer. [Installation details](INSTALL.md)

### 2. Enable in Bitwig

Under **Settings → Controllers → Add controller**, choose vendor **Bitwig Agent** and model **Local API bridge**. Enable the controller. Restart Bitwig after replacing an already loaded extension.

### 3. Connect your MCP client

Add a **Streamable HTTP MCP** server at `http://127.0.0.1:61172/mcp`. Replace the old stdio entry if you configured one before. [Example configuration](examples/mcp-config.json)

## Explore the tools

Use MCP discovery to list the tools and their input schemas. Start with `status` to check the running build, `capabilities` for the available methods, and `context.get` for the current project and tracks.

For an agent, copy the complete [portable skill folder](https://github.com/BANANASJIM/bitwig-agent/tree/main/skills/bitwig-agent), including its references and license, into your client's supported skill location. It covers production workflows, troubleshooting, screenshot-guided next steps and **preserve-A / compare-B creative edits**. Live screenshots and GUI input need your client's desktop tools; the bridge does not control the entire interface. A skill is guidance, **not permission to operate the DAW**.

## Safety and compatibility

- **Local trust only.** The HTTP MCP endpoint listens on unauthenticated IPv4 loopback. Any local process able to connect can control Bitwig. Never expose or forward it to a network.
- **Private interfaces.** The current build leaves Grid patch editing, private modulator reads and private Launcher settings disabled.
- **Never blindly retry an uncertain edit.** A timeout may mean the mutation happened. Discover fresh targets, inspect the outcome and reconcile uncertainty before another write.
- **Package checks are not native acceptance.** Model readback, GUI state, audio, Undo and save/reopen are separate evidence. See [targeting safety](https://github.com/BANANASJIM/bitwig-agent/blob/main/USAGE.md#target-safely-then-observe-completion) and [coverage limits](https://github.com/BANANASJIM/bitwig-agent/blob/main/VERIFICATION.md).

## Documentation

| Need | Guide |
|---|---|
| Installation, upgrades or connection help | [Installation](https://github.com/BANANASJIM/bitwig-agent/blob/main/INSTALL.md) |
| Operations and targeting | [Usage](https://github.com/BANANASJIM/bitwig-agent/blob/main/USAGE.md) · [Protocol](https://github.com/BANANASJIM/bitwig-agent/blob/main/PROTOCOL.md) |
| How the Java MCP extension works | [Architecture](https://github.com/BANANASJIM/bitwig-agent/blob/main/docs/architecture.md) |
| Production guidance, screenshots and A/B workflow | [Agent skill](https://github.com/BANANASJIM/bitwig-agent/tree/main/skills/bitwig-agent) |
| Tests and evidence boundaries | [Verification](https://github.com/BANANASJIM/bitwig-agent/blob/main/VERIFICATION.md) |
| Development and releases | [Contributing](https://github.com/BANANASJIM/bitwig-agent/blob/main/CONTRIBUTING.md) · [Packaging](https://github.com/BANANASJIM/bitwig-agent/blob/main/docs/packaging.md) |
| Private vulnerability reports | [Security](https://github.com/BANANASJIM/bitwig-agent/blob/main/SECURITY.md) |

[Full documentation index](https://github.com/BANANASJIM/bitwig-agent/blob/main/docs/README.md)

## License

Project code is [MIT licensed](https://github.com/BANANASJIM/bitwig-agent/blob/main/LICENSE). Gson is Apache-2.0; see [third-party notices](https://github.com/BANANASJIM/bitwig-agent/blob/main/THIRD_PARTY_NOTICE.md). Bitwig Studio and its proprietary host JAR are separate dependencies and are not bundled.
