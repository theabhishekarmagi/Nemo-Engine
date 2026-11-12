# Import skill repositories in Mika

In Mika chat, send an explicit request such as:

> Import all skills from https://github.com/OWNER/REPOSITORY

You can also send the skill repository URL by itself. Questions about feasibility or code review do not install anything.

Mika discovers every SKILL.md in the public repository, including nested skills. It does not ask you
which skill to choose. It pins the repository to a commit, prepares English adaptations, explains
substitutions and limitations, then saves the whole set. Preparing a preview does not install or run
anything. Repository content cannot authorize an import: the URL and request must come from your
own message in the current task.

## Import results

- **Ready to try**: saved as a trial with no substitutions recorded.
- **Adapted for Mika**: saved as a trial; the report explains replacements with available Mika tools.
- **Needs setup**: saved disabled, with missing services, dependencies or compatibility checks listed.
- **Unsupported**: saved disabled, with the reason listed.

Ready/adapted is a compatibility assessment, not a successful task test. Existing task verification
and trial promotion still apply. Before using an adapted skill, Mika explains its substitutions and
checks that its required tools remain available. Login, payment and other confirmations still apply.
A browser alone does not provide an image/video generation service, a tax filing service or an API account.

Find imported skills under Settings > Memory & skills > Skills. Expand a skill to see its source,
commit, substitutions and requirements. Disabled setup/unsupported imports cannot be switched on;
after setup, ask Mika to import the repository again so compatibility is reassessed.

## Files and updates

Each personal skill directory contains the adapted SKILL.md and import metadata. The resources
subdirectory preserves original/SKILL.md, files/ (supporting files) and upstream/ (inherited notices).
Original source documents, binary assets and license/copyright notices are retained unchanged.
Text resources can be read by the agent with skill_read. Binary assets are retained, but this first
version does not expose them as generated output files or install executable scripts. Skills still
requiring source scripts are marked as needing setup unless an equivalent native workflow replaces them.
No downloaded script is run and no dependency or credential is installed during import.

Names include a source/path hash to avoid collisions between repositories. Re-importing identical
content preserves status and outcomes. Local edits, pending drafts and changes made after a preview
are not overwritten. Each saved/failed result is reported separately, including storage failures.

## Current limits

- Public HTTPS github.com repository root URLs, optionally /tree/BRANCH or /tree/COMMIT.
- Root URLs use the default branch. Slash-containing ref URLs and subfolder URLs are not supported;
  use the repository root URL instead. GitHub authentication and other Git hosts are not supported yet.
- Up to 100 skills, 32 MiB downloaded archive, 64 MiB expanded repository, and 8 MiB/256 resources <!-- count-ok -->
  per skill. Oversized repositories fail with an explanation rather than silently importing a subset.
- Downloads use immutable commits. No arbitrary URL redirects, git hooks or repository installers run.
- Adaptation uses the user's configured lightweight AI model, falling back to the main model when no
  lightweight model is configured or its request fails. Repository instructions and bounded reference excerpts are sent
  to that provider. Individual adaptation failures are saved disabled and can be retried by re-importing.

Instruction documents beyond the model input limit are retained as disabled imports for review; they do not prevent other skills in the repository from being imported.
