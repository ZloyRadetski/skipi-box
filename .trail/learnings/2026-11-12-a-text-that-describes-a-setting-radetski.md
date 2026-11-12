---
title: "A text that describes a setting must be rewritten whenever the setting's behavior changes, in all five languages"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 1, tokens: 1964 }
id: l-a5b1eeac
scope: repo
status: active
src: agent
touches:
  - docs/text-audit-2026-11-12.md
---

## Decided
Whoever changes what a setting, option or message does also rewrites its text in en, ru, fa, zh and zh-rCN in the same change. zh and zh-rCN carry the same text. Placeholders must match the English one (a check over all five files found none out of step).

## Tried and ruled out
Trusting the existing fa and zh texts: they were machine translations that changed meaning (Trojan became a horse, port a harbour, MTU a person) and each lacked about 130 strings that silently showed English. Merging duplicate keys in the same pass: it needs a decision on which identical English texts mean the same thing in every language.

## Watch out
Texts that outlived their logic were the usual fault: a restore summary promising a ROOT boot script after ROOT mode was removed, a system-proxy summary promising SOCKS where only HTTP is set, DNS texts describing a fallback that no longer exists. Strings with no code reference are dead and should be deleted rather than kept.

In the code:
- shared/ui/src/commonMain/composeResources/values*/strings.xml
- docs/text-audit-2026-11-12.md lists what was checked and what is still undecided
