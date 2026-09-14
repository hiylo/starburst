<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# Security Policy

## Reporting a Vulnerability

Please **do not** open a public issue for security vulnerabilities. Instead, report them privately
to the maintainers by emailing **hiylo@live.com**.

Include the following in your report:

- A description of the vulnerability and its impact.
- Steps to reproduce (versions, device, configuration).
- Any proof-of-concept you are comfortable sharing.

You will receive an acknowledgment within 3 business days, and we aim to triage within 7 days.
Please allow us time to fix and release a patch before public disclosure.

## Scope

This policy applies to the StarBurst client. Issues in the upstream
[OpenCode](https://github.com/anomalyco/opencode) server should be reported to that project.

## Security-relevant behavior

- Server credentials and LLM API keys are encrypted with the Android Keystore and are never
  written to logs or diagnostic exports in plaintext.
- In-app updates verify the APK's SHA-256, package identity, version, and signing certificate.
- Diagnostic logs intentionally omit message content, question/answer text, and secrets.

## Supported versions

| Version | Supported |
|---------|-----------|
| 1.0.x   | ✅ |
