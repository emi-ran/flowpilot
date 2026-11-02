# Supply chain

FlowPilot ships APKs from GitHub Releases only. This document describes what the build and
release pipeline verifies, and how a user or maintainer can repeat those checks independently.

Scope of this document: Gradle dependency verification, repository content filters, pinned GitHub
Actions, and release APK provenance/signature/checksum checks. It does not claim that dependencies
are free of vulnerabilities, and it does not replace reviewing source changes.

## Gradle dependency verification

Gradle dependency verification is enabled by the presence of
`gradle/verification-metadata.xml`. The file is **generated, never hand-written**:

```bash
# Baseline (plugins and buildscript classpath)
./gradlew --write-verification-metadata sha256 help

# Extend to the app compile/test closure (Gradle records only what the build resolves)
./gradlew --write-verification-metadata sha256 assembleDebug testDebugUnitTest \
  assembleRelease -PreleaseSigningRequired=false
```

A maintainer runs the generation, reviews the resulting XML as source, and commits it. Any
change that adds or upgrades a dependency must regenerate the metadata in the same change; an
unreviewed dependency addition is a supply-chain change.

Once committed, every local and CI build verifies:

- artifact files (JAR/AAR/ZIP) and their POM/Gradle Module Metadata for the entire dependency graph,
- project and settings plugins resolved by `pluginManagement`,
- the exact SHA-256 bytes recorded in the XML.

Gradle's default `strict` verification mode is used: a missing checksum, an unlisted artifact, or a
checksum mismatch fails resolution before compilation. No workflow passes
`--dependency-verification=lenient` or `off`. CI therefore fails on a mismatch rather than warning.

The metadata records SHA-256 checksums (`<verify-signatures>false</verify-signatures>`). That
protects integrity: an artifact swapped in transit or at rest under the same coordinates is
rejected. It does not prove who published the artifact. PGP signature verification can be layered
on later without changing the release contract.

The Gradle distribution itself is pinned separately by `distributionSha256Sum` in
`gradle/wrapper/gradle-wrapper.properties` (`gradle-9.5.0-bin.zip`,
`553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746`).

### Why dependency lockfiles are not used

Dependency locking is intentionally **not** introduced:

- Versions are already fixed: every dependency comes from `gradle/libs.versions.toml` with an exact
  version or from the Compose BOM. There are no dynamic (`+`, `latest.release`) or `SNAPSHOT`
  versions in this project.
- Verification metadata pins the exact artifact bytes, including transitive artifacts. A lockfile
  pins selected versions, not bytes, so it would not add integrity on top of checksums.
- A single-module app has a small graph; per-configuration lock state would add regeneration churn
  and stale-lock risk without a matching security gain.

If dynamic versions or included builds are added later, revisit locking together with verification
metadata.

Known limitation: Gradle does not verify `SNAPSHOT` or changing versions if they are ever
introduced. This repository does not use them today.

## Repository content filters

`settings.gradle.kts` centralises repositories:

- `pluginManagement` and `dependencyResolutionManagement` both restrict `google()` to
  `com.android.*`, `com.google.*`, and `androidx.*`. Those are the groups the Google Maven
  repository actually serves for this build, so Gradle does not query it for unrelated modules.
- `mavenCentral()` is deliberately **not** given an allowlist. It serves most of the graph
  (Kotlin, coroutines, serialization, JUnit, Robolectric, Shizuku, and transitives). A group
  allowlist would be brittle, would need maintenance on every upgrade, and would break legitimate
  transitive resolution. Content filters are repository routing hints, not an authenticity control;
  checksum verification is the integrity control.
- `gradlePluginPortal()` remains available for plugin resolution. Plugin artifacts are pinned by
  version and covered by verification metadata.
- `RepositoriesMode.FAIL_ON_PROJECT_REPOS` remains enabled, so modules cannot add their own
  repositories.

## GitHub Actions pinning

Every `uses:` reference in `.github/workflows/` is pinned to a full 40-character commit SHA with a
human-readable version comment. Tags are mutable; commit SHAs are not.

The release provenance step uses:

```text
actions/attest-build-provenance@4d101475d8b20a2381f78447822ac1eab6504dd8 # v4.2.2
```

That commit is the `v4`/`v4.2.2` tag commit resolved on 2026-11-02 with:

```bash
gh api repos/actions/attest-build-provenance/git/ref/tags/v4
# annotated tag object 8beda2b7ed98355c0e97c0a63bec38ae472e66c4
gh api repos/actions/attest-build-provenance/git/tags/8beda2b7ed98355c0e97c0a63bec38ae472e66c4
# commit 4d101475d8b20a2381f78447822ac1eab6504dd8
```

When bumping an action: resolve the new tag to a commit SHA the same way, confirm the SHA is the
upstream release commit, review the action diff, then update the SHA and the `# vX.Y.Z` comment
together.

## Release APK provenance

`.github/workflows/release.yml` publishes a GitHub build provenance attestation (SLSA v1 predicate)
for the signed release APK. The step runs in the `release` job:

1. after `assembleRelease -PreleaseSigningRequired=true` builds the signed APK,
2. after the APK passes package/version identity checks, `apksigner verify`, signer certificate
   SHA-256 comparison, and `sha256sum --check`,
3. before `softprops/action-gh-release` uploads the assets.

The job requests `id-token: write` (OIDC identity) and `attestations: write` (attestation API). The
step has no `continue-on-error`: if attestation creation fails, the job fails and the release is not
published. The attestation is therefore created only for the exact APK asset that is uploaded.

Verify a downloaded APK:

```bash
gh attestation verify FlowPilot-vX.Y.Z.apk --repo emi-ran/flowpilot
```

`gh` must be recent enough to include the `attestation` command (2.49.0 or newer). The command
checks that the file digest matches an attestation issued by this repository and reports the
signing workflow and source commit. Use `--format json` for machine-readable output.

For a stronger check, also pin the signer workflow (the release workflow is the signer, because it
creates the attestation directly rather than through a reusable workflow):

```bash
gh attestation verify FlowPilot-vX.Y.Z.apk \
  --repo emi-ran/flowpilot \
  --signer-workflow emi-ran/flowpilot/.github/workflows/release.yml
```

Honest limits:

- Only releases published after this attestation step was added carry attestations. The `v1.1.0`
  release predates it and will report no attestation.
- A locally built APK has no attestation; `gh attestation verify` will fail for it.
- The attestation proves which workflow produced the bytes. It does not attest the security of the
  source code or of third-party dependencies.

## Release APK signer certificate

The release workflow verifies the APK signature with Android build-tools 36.0.0 and compares the
signer certificate digest before upload. A missing or different digest fails the job:

```text
Signer #1 certificate SHA-256 digest:
f1e45d3a669954ddfb858d5d3a964a2fa7ba3d00bcb0e65b5ced1ca6d5a79f2b
```

Repeat the check locally:

```bash
# Path on the GitHub runner and on a local Android SDK:
# $ANDROID_HOME/build-tools/36.0.0/apksigner
apksigner verify --print-certs FlowPilot-vX.Y.Z.apk
```

`apksigner verify --verbose` exits non-zero for an invalid or missing signature. Compare the
`Signer #1 certificate SHA-256 digest` line to the value above. If it differs, do not install the
APK: it was signed with a different key, or the file was modified.

This digest identifies the FlowPilot release signing certificate. It is not a Google Play
certificate, and possession of the digest is not proof that the signing key was not compromised.

## SHA-256 checksum

Each release publishes `FlowPilot-vX.Y.Z.apk.sha256` next to the APK and repeats the digest in the
release notes:

```bash
sha256sum FlowPilot-vX.Y.Z.apk
cat FlowPilot-vX.Y.Z.apk.sha256
```

On Windows PowerShell:

```powershell
(Get-FileHash .\FlowPilot-vX.Y.Z.apk -Algorithm SHA256).Hash.ToLower()
```

The `.sha256` file uses `sha256sum` format (`<digest>  <file name>`). The workflow generates it with
`sha256sum` and immediately validates it with `sha256sum --check` before upload. A checksum match
proves the file matches the published digest; combined with the signer check it also shows the file
is signed by the FlowPilot release key. The attestation additionally ties the digest to this
repository's release workflow.

## Checks maintained outside this repository

Repository and environment protection cannot be enforced from committed files. The required GitHub
settings (tag ruleset, protected `main`, `release-signing` environment with required reviewers,
read-only default `GITHUB_TOKEN`) are listed in `docs/RELEASE_SECURITY.md` and must be verified in
the repository settings after any admin change.
