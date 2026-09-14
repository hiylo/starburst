<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# Contributing to StarBurst

Thanks for your interest in contributing! Please take a moment to review this guide.

## Code of Conduct

Be respectful and constructive. Harassment, bullying, or any form of discrimination will not be
tolerated. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for the full policy. If you observe
unacceptable behavior, report it to the maintainers.

## Reporting Bugs

- Search [the issue tracker](https://github.com/hiylo/starburst/issues) first to avoid duplicates.
- Include the app version, Android version, device model, and the OpenCode server version.
- Attach a reproduction step, expected vs. actual behavior, and a diagnostic log export if possible.

## Feature Requests

Open an issue describing the feature, the problem it solves, and any trade-offs you foresee.
Clearly mark it as a feature request.

## Development Setup

1. Android Studio (latest stable) with the Android SDK.
2. Clone the repository.
3. Build and run: `./gradlew assembleDebug`.
4. The project bundles MNN native libraries and the on-device model; first build takes a few minutes.

## Pull Request Process

1. Create a branch with a descriptive name (e.g. `fix/session-delete`, `feat/streaming-suggestions`).
2. Make focused changes; keep the diff small and reviewable.
3. Run the unit tests: `./gradlew testDebugUnitTest`.
4. Verify the app builds in release mode: `./gradlew assembleRelease`.
5. Update `CHANGELOG.md` under the "Unreleased" section.
6. Open a pull request against `main` and describe the change, tests run, and any caveats.

## Code Style

- Kotlin, ktlint-style (4-space indent, 120-column limit).
- Public APIs documented with Javadoc.
- Prefer reusing existing utilities over duplicating logic.
- Keep `app/src/main/cpp/starburst_mnn_jni.cpp` focused on the JNI boundary.

## License

By contributing you agree that your contributions are licensed under the project's
[MIT License](LICENSE).
