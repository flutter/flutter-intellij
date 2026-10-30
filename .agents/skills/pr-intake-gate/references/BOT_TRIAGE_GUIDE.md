# Bot Review Finding Triage Guide

This guide establishes the mandatory protocol for handling automated review feedback from Gemini Code Assist, static analysis bots, and linters in `flutter-intellij`.

---

## Why Gating Bot Review Triage Matters

In open-source IntelliJ plugin repositories, analysis showed that over a typical quarter:
- Over 70% of pull requests received at least one `[MUST-FIX]` or `[CONCERN]` finding from AI review bots.
- More than 20% of findings were marked as "resolved" with **neither a code fix nor an explanatory reply**.
- More than 10% of findings remained completely unresolved when pull requests were merged.

When findings are silently dismissed, human codeowners are forced to spend their limited review time diagnosing the exact same issues or dealing with avoidable regressions in production.

This intake gate enforces a simple rule:
**Answer the bot, don't just dismiss it.**

---

## Finding Severity Categories

Review comments posted by Gemini Code Assist and repository bots use standardized severity tags:

| Tag | Severity | Action Required Before Human Review |
|---|---|---|
| `[MUST-FIX]` | Critical | **Mandatory**: Must be fixed in code, or refuted as `[FALSE-POSITIVE]` with technical proof. |
| `[CONCERN]` | High | **Mandatory**: Must be resolved in code, or justified with `[INTENTIONAL]` technical rationale. |
| `[NIT]` | Low | **Optional**: Author discretion; does not block readiness. |

---

## Approved Response Protocols

If a finding is **not** resolved by modifying code, you must post an explicit inline reply using one of the two approved tags:

### 1. The `[INTENTIONAL]` Response

Used when the code intentionally deviates from a standard recommendation due to an architectural constraint, platform version boundary, or backward-compatibility requirement.

#### Valid Criteria
- Preserving deprecated API usages because the replacement API does not exist in an older, supported IntelliJ Platform baseline.
- Retaining a specific data structure or synchronization primitive for performance or concurrency invariants.
- Deferring an optimization to an upcoming scoped architectural refactor.

#### Concrete Examples

> **Bot Finding:**
> `[CONCERN] ReadAction.compute(ThrowableComputable) is deprecated in 2024.3+. Use computeBlocking instead.`
>
> **Acceptable `[INTENTIONAL]` Reply:**
> `[INTENTIONAL] We currently support IntelliJ Platform 2024.1 as our minimum baseline. computeBlocking is only available starting in 2024.3, so calling it directly causes NoSuchMethodError at runtime on older builds. We have wrapped this in ApplicationManager.getApplication().runReadAction(...) to ensure cross-version compatibility.`

> **Bot Finding:**
> `[CONCERN] The method loadSdkMetadata() blocks the calling thread and is called during project startup.`
>
> **Acceptable `[INTENTIONAL]` Reply:**
> `[INTENTIONAL] This call is executed strictly from a background coroutine on the IO dispatcher (see line 42), not on the Event Dispatch Thread (EDT). Project initialization remains non-blocking.`

---

### 2. The `[FALSE-POSITIVE]` Response

Used when the bot's static analyzer lacked sufficient context or made an incorrect assumption about the code or framework.

#### Valid Criteria
- The bot flagged an EDT violation, but the enclosing method is already annotated or guaranteed to run on a background thread.
- The bot suggested extracting an instantiation outside a ReadAction, but the object's constructor internally accesses VirtualFiles or PSI requiring read access.
- The bot flagged a potential null dereference, but a previous assertion or framework contract guarantees non-nullity.

#### Concrete Examples

> **Bot Finding:**
> `[CONCERN] Minimize the scope of ReadAction by extracting DartConsoleFilter instantiation outside the read lock.`
>
> **Acceptable `[FALSE-POSITIVE]` Reply:**
> `[FALSE-POSITIVE] The DartConsoleFilter constructor internally invokes DartUrlResolver.getInstance(project), which performs PSI index queries and throws a ReadAccessException if called without holding the read lock. Therefore, the instantiation must remain inside the ReadAction.`

> **Bot Finding:**
> `[MUST-FIX] Potential NullPointerException: project.getBasePath() can return null.`
>
> **Acceptable `[FALSE-POSITIVE]` Reply:**
> `[FALSE-POSITIVE] Line 58 explicitly validates project.getBasePath() != null and returns early before this block is reached.`

---

## Prohibited Behaviors (Presubmit / Gate Violations)

1. **Silent Dismissal**: Clicking "Resolve conversation" on a `[MUST-FIX]` or `[CONCERN]` thread without either a code change or a reply.
2. **Non-Technical Dismissals**: Replying with "Will fix later", "Not important", "Works on my machine", or "LGTM" without technical explanation.
3. **Weakening Code to Satisfy the Bot**: Introducing arbitrary `catch (Throwable t) {}` blocks or dummy null checks that mask underlying architectural issues.

---

## Recommended Author & Agent Workflow

1. Push your initial pull request as a **Draft**.
2. Wait 2–3 minutes for CI static checks and Gemini Code Assist to analyze the diff.
3. Review all posted bot comments.
4. For valid issues: implement fixes, commit changes, and push updates.
5. For invalid or intentional issues: post inline replies using `[INTENTIONAL]` or `[FALSE-POSITIVE]`.
6. Once all `[MUST-FIX]` and `[CONCERN]` comments are resolved or answered, proceed with generating the verification stamp and marking the PR ready for human review.
