# Setup and format validation

This skill is portable instructions, not an installer or permission grant. Copy the entire `bitwig-agent` directory, keeping `SKILL.md`, `references/` and `LICENSE` together, into a skill location supported by the chosen client. Do not assume a universal client-specific path. Client skill discovery and MCP configuration are separate tasks; make global/client changes only with explicit permission.

## Missing tools or extension

Install the Java HTTP MCP extension and enable **Bitwig Agent → Local API bridge** in Bitwig's controller settings. Configure the client for Streamable HTTP at `http://127.0.0.1:61172/mcp`. Restart Bitwig after replacing an already loaded extension, then check `status.extension.build` and the methods reported by `capabilities`. Keep the endpoint on loopback. [Installation guide](https://github.com/BANANASJIM/bitwig-agent/blob/main/INSTALL.md)

The current HTTP build leaves private Grid, modulator and Launcher settings tools disabled. Check the running tool list before using examples written for earlier releases.

## Isolated native runs

Native tests can run on an isolated headless display (`weston --backend=headless --xwayland`, `DISPLAY=:1`) so the user's desktop is untouched. Display isolation is not audio or profile isolation: audio devices and the Bitwig profile are still the user's.

## Standard format validation

The [Agent Skills specification](https://agentskills.io/specification) defines the frontmatter and directory format. To validate a source-checkout skill folder with the pinned reference implementation:

```sh
uvx --from "git+https://github.com/agentskills/agentskills.git@69ef37e9424c0a7ea9dd2293b559e43ec8176379#subdirectory=skills-ref" skills-ref validate skills/bitwig-agent
```

For a copied skill, replace the final argument with that folder's actual path. The command downloads a validation tool and needs appropriate network/tool-execution permission; do not run it automatically as part of a musical task. `skills-ref` is a demonstration validator, not a Bitwig runtime dependency.

Format validation checks naming/frontmatter conventions, **not** link resolution, user permission, safe model behavior, host compatibility, native editing, audio or saved persistence. Separately inspect the standalone folder's local references and license. External documentation uses public URLs so copying the folder does not leave checkout-relative links. Public `main` may change; use the matching release tag and installed schemas for version-specific behavior.
