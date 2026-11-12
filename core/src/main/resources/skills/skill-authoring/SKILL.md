---
name: skill-authoring
display: Write a skill
description: Create, import or update a personal workflow skill.
aliases: ["create a skill", "save a skill", "import a skill", "edit a skill", "skill"]
terms: ["workflow", "procedure", "template", "技能", "保存技能", "导入技能", "创建技能", "新建技能", "写个技能", "改技能"]
---
## When to write

Write only when the user explicitly asks to create, import, save or change a skill. Questions about whether something is possible do not authorize saving it. A skill describes a repeatable workflow; a personal preference belongs in memory.
Only `skill_save` can save a personal skill. If unavailable, say that saving is unavailable.

## Check for an existing skill

1. Use `skill_search` for the workflow.
2. If a personal skill already covers it, use `skill_read`, keep its name and supply the returned version when saving the update.
3. Built-in skills cannot be overwritten. Save a distinct personal skill if the user needs a different workflow.

## Clarify what matters

If the request is vague, use `ask_user` for the app or website, the stopping point and any requirements that change the workflow. Confirm those details before saving. Do not ask again when the user already provided a complete workflow.

## Format

Write the title, description, aliases and instructions in English, regardless of the source language. Preserve exact identifiers and necessary UI labels. Use front matter and the five headings below; keep the whole document within 4000 characters.

```
---
name: order-coffee
display: Order coffee
description: Use when ordering coffee for delivery or collection.
aliases: ["order coffee", "buy coffee"]
---
## Entry
## Steps
## Common pitfalls
## Verification
## When to hand over
```

- Use a descriptive name with lowercase letters, digits and hyphens.
- Describe when to use the skill and include likely search phrases in aliases.
- Entry identifies the starting app, website or search. Steps are short numbered actions using page and button names, never fixed coordinates.
- Replace variable names, amounts, addresses, messages and dates with placeholders such as <parameter> or "the user-specified contact".
- Explain known pitfalls, visible evidence of completion and when login, payment, confirmation or a choice requires the user.
- Use backticks only for available tool names. Do not include passwords, verification codes, card numbers or private information.
- Keep only workflow-specific guidance, rather than generic advice the model already knows.

## Importing repositories

When the user provides a GitHub skill repository and asks to import or use it, use `skill_import`.
Import every skill together, never ask the user to choose. Prepare first, explain substitutions and
limitations before installing, then install the preview. Report ready/adapted/setup/unsupported counts
truthfully. Missing setup must not be hidden or enabled. Keep source files and notices. Do not fetch
and save only the first SKILL.md with `skill_save`.

## Importing supplied content

Adapt the user's supplied workflow to this format in English. Preserve its meaning and remove commands or scripts for unavailable tools. Fill missing sections only where supported by the source; do not invent capabilities. Exclude credentials and private information.

## Save and report

1. Call `skill_save` with the whole document and quote the user's actual request from this task.
2. Claim success only after an ok result. Report the name, whether it was created or updated, and that it appears in Settings > Memory & skills for a trial on the next matching task.
3. On failure, correct the reported issue and retry once. If unsuccessful, explain that the skill was not saved and why.
4. Renaming and deletion are managed in Settings. Saving under a new name does not delete the previous skill.

## Verification

The tool returns ok and the reported name and operation match its result.
