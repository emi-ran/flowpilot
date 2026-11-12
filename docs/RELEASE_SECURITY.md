# Release tag security

`release.yml` only accepts `vX.Y.Z` tags. Its unprivileged validation job verifies the push event tag object (annotated or lightweight), peels it to a commit, and requires that commit equal fresh `origin/main`. It also requires a matching `app` `versionName` and a successful `Android CI` push workflow and `Build & Test` job for that exact commit. An existing release is rejected except for an asset-free draft for the same tag, which can be recovered. Only then can the environment-protected signing job run.

The `Build & Test` job skips Gradle and the SDK-free scripts when no Android or build inputs changed, including workflow-only changes; a successful job alone does not prove those checks ran. The release job separately runs the SDK-free scripts, unit tests, debug lint and debug assembly before building the signed release.

Before tagging a new version, update the version-specific APK `versionCode` and `versionName` checks, release title and required change-section headings in `release.yml`, along with the app version, changelog section and `docs/RELEASE_NOTES_X.Y.Z.md`. The notes must contain the workflow's `{{APK_NAME}}`, `{{SHA256}}` and `{{RELEASE_COMMIT}}` markers and required headings. Validation also queries the latest published release and checks its Verification, Installation and Compatibility headings; an unavailable or nonconforming latest release stops the workflow.

## Required GitHub repository setup

GitHub rulesets cannot live in repository. Configure in **Settings > Rules > Rulesets**:

1. Create active **Tag** ruleset targeting `v*`.
2. Enable **Restrict creations**, **Restrict updates**, **Restrict deletions**, and **Require signed tags**. This rejects unsigned lightweight tags; workflow still validates either tag object form defensively.
3. Add only dedicated trusted release maintainers to bypass list. Do not grant `GitHub Actions`, broad write roles, or administrators bypass unless separately justified and audited.
4. Create or retain active **Branch** ruleset for `main`: require pull requests, require `Android CI / Build & Test`, require branch up to date before merge, restrict force pushes and deletions, and limit bypass list to trusted maintainers.
5. Create environment `release-signing`. Move `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` from repository secrets into that environment, then delete repository-level copies. Require at least one trusted release maintainer approval and prohibit self-review. Historical tag workflow revisions cannot read environment-only secrets because they do not reference this environment.
6. Set default `GITHUB_TOKEN` workflow permissions to read-only. `release.yml` declares workflow-level `actions: read` and `contents: write`; its validation job uses `actions: read` and `contents: read`, while the release job uses `contents: write`, `id-token: write`, and `attestations: write` for publishing and provenance. Do not allow actions to create or approve pull requests.

Tag rules prevent post-validation tag moves or deletion/recreation. Branch rules keep qualifying commits reviewed and CI-gated before tagging. Environment-scoped secrets prevent historical tag workflow revisions from reaching signing material. GitHub rulesets, environment protection, token defaults, secret migration, and bypass membership require repository-admin configuration and cannot be enforced from this repository.
