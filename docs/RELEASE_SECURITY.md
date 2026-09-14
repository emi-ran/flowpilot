# Release tag security

`release.yml` only accepts `vX.Y.Z` tags. Its unprivileged validation job verifies push event tag object (annotated or lightweight), peels it to a commit, and requires that commit equal fresh `origin/main`. It also requires matching `app` `versionName`, successful `Android CI` push workflow and `Build & Test` job for exact commit, and no existing GitHub Release for tag. Only then can the environment-protected signing job run.

## Required GitHub repository setup

GitHub rulesets cannot live in repository. Configure in **Settings > Rules > Rulesets**:

1. Create active **Tag** ruleset targeting `v*`.
2. Enable **Restrict creations**, **Restrict updates**, **Restrict deletions**, and **Require signed tags**. This rejects unsigned lightweight tags; workflow still validates either tag object form defensively.
3. Add only dedicated trusted release maintainers to bypass list. Do not grant `GitHub Actions`, broad write roles, or administrators bypass unless separately justified and audited.
4. Create or retain active **Branch** ruleset for `main`: require pull requests, require `Android CI / Build & Test`, require branch up to date before merge, restrict force pushes and deletions, and limit bypass list to trusted maintainers.
5. Create environment `release-signing`. Move `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` from repository secrets into that environment, then delete repository-level copies. Require at least one trusted release maintainer approval and prohibit self-review. Historical tag workflow revisions cannot read environment-only secrets because they do not reference this environment.
6. Set default `GITHUB_TOKEN` workflow permissions to read-only. `release.yml` explicitly grants only `actions: read` and `contents: write`; do not allow actions to create or approve pull requests.

Tag rules prevent post-validation tag moves or deletion/recreation. Branch rules keep qualifying commits reviewed and CI-gated before tagging. Environment-scoped secrets prevent historical tag workflow revisions from reaching signing material. GitHub rulesets, environment protection, token defaults, secret migration, and bypass membership require repository-admin configuration and cannot be enforced from this repository.
