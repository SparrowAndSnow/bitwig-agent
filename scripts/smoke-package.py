#!/usr/bin/env python3
"""Check the optional installer's version and HTTP MCP configuration.

Run with the consumer environment's Python, preferably with -I. All entry points
run outside the checkout; no extension download, installation, or host calls.
"""
import argparse
from importlib.metadata import version
import json
from pathlib import Path
import subprocess
import sys
import tempfile

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-version", help="Require this installed release version")
    args = parser.parse_args()
    installed_version = version("bitwig-agent")
    if args.expected_version is not None and installed_version != args.expected_version:
        raise RuntimeError(f"Expected {args.expected_version}, installed {installed_version}")
    executables = Path(sys.executable).parent
    installer = executables / "bitwig-agent-install"

    with tempfile.TemporaryDirectory() as cwd:
        subprocess.run([str(installer), "--version"], cwd=cwd, check=True, timeout=30)
        config = json.loads(subprocess.check_output(
            [str(installer), "--mcp-config"], cwd=cwd, text=True, timeout=30
        ))["mcpServers"]["bitwig-agent"]

        if config != {"url": "http://127.0.0.1:61172/mcp"}:
            raise RuntimeError("Unexpected Bitwig Agent HTTP MCP configuration")
        print(f"Bitwig Agent {installed_version}: installer exposes HTTP MCP configuration")


if __name__ == "__main__":
    main()
