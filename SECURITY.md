# Security Policy

## Supported versions

Security fixes target the latest release and the current `main` branch.

## Reporting a vulnerability

Please do not open a public issue for suspected security vulnerabilities.

Use GitHub's private vulnerability reporting:

1. Open the repository's **Security** tab.
2. Select **Advisories**.
3. Select **Report a vulnerability**.

Include affected version or commit, reproduction steps, expected impact, and any suggested mitigation. Do not include real user secrets, credentials, phone numbers, webhook payloads, or other private data.

The maintainer will review the report privately and coordinate validation, remediation, and disclosure through a GitHub Security Advisory when appropriate.

## Scope

High-priority reports include:

- Exposure of webhook credentials, backup passwords, phone numbers, SMS content, or location data
- Authentication, authorization, Android Keystore, backup, or import-validation bypasses
- Unsafe privileged command execution through Shizuku
- Unintended network transmission, telemetry, or persistent sensitive logging
- Dependency or build-pipeline compromise
