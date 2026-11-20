# Security Policy

## Supported versions

| Line | Branch | Supported |
| :--- | :--- | :--- |
| Slimefun 6 (Universal 26.x, Minecraft 1.21 → 26.2) | `main` | Yes |
| Slimefun4-Drake 1.21.11 line | `legacy/slimefun4-drake-1.21` | Critical fixes only |

## Reporting a vulnerability

Please **do not open a public issue** for a security problem (item or block duplication exploits, permission bypasses,
remote code paths, data exposure).

1. Use GitHub's private reporting: **Security → Report a vulnerability** on this repository; or
2. Contact the maintainers privately on the [DrakesCraft Discord](https://discord.gg/rv3vtXZTk7) (open a ticket and say it is a security report).

Include the version, steps to reproduce, and the impact you observed. We aim to acknowledge reports within 72 hours and will
credit you in the fix unless you prefer to stay anonymous.

## Dependencies

Dependency updates are proposed weekly by Dependabot (`.github/dependabot.yml`) and CodeQL and secret scanning run in CI.
