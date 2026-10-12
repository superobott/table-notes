# Review this Android app with CodeRabbit

CodeRabbit performs an AI code review. The Android build, lint, backup unit tests, and checks on an actual phone are separate forms of validation.

## First review of the whole app

1. Create a GitHub repository for Table Notes. A private repository is suitable if you do not want to publish the source. Start `main` with a README-only commit.
2. Allow the already-connected CodeRabbit GitHub App to access this repository. Connecting your GitHub account alone does not necessarily grant access to every repository.
3. Create a branch such as `android-app` from `main`.
4. Put the **contents** of this source folder at the repository root on that branch, including hidden `.github`, `.gitignore`, and `.coderabbit.yaml` files. Do not upload the APK or put the ZIP in GitHub as a substitute for source files.
5. Open a pull request from `android-app` into `main`. All app files should appear as added changes for the first review. Do not merge it before the review.
6. In the PR conversation, add `@coderabbitai full review`. Read the findings, make fixes on the same branch, and then request an incremental review with `@coderabbitai review`.
7. Also check the Android workflow results and install the verified app on your phone to test real interactions.

The included `.coderabbit.yaml` provides Android-specific review guidance. CodeRabbit's Change Stack is an optional organized view of the same review, generated automatically. It does not require a separate stack account. Slack is also optional.

For later releases, open a PR containing only that release's changes. A full review command reviews the PR's scope, not every unchanged file in the repository.

Never commit a Google API key, signing keystore, `local.properties`, or personal review backups. The API key is entered and encrypted on your phone; no key is needed for compilation or the offline unit tests. GitHub Actions checks are not a live Google API test. A debug APK built on GitHub uses a different signing key from the supplied personal APK, so it cannot necessarily replace the installed copy directly.

Official references: [GitHub connection](https://docs.coderabbit.ai/platforms/github-com), [review commands](https://docs.coderabbit.ai/reference/review-commands), [Change Stack](https://docs.coderabbit.ai/change-stack).
