---
name: implement-dart-language-feature
description: Guidelines for implementing and reviewing new Dart language features, parsing logic, and grammar modifications.
---

# Dart Language Feature Guidelines

Use this skill when implementing support for a new Dart language feature (e.g., primary constructors, macros, records) or when reviewing code/PRs that modify the Dart grammar and parser.

## 1. Implementation Guidelines

When modifying the grammar and parser to support new syntax, adhere to the following principles:

* **Permissive over Restrictive:** Be permissive when modifying the grammar. Trust that the Dart analyzer will produce the necessary diagnostics on invalid code. We do not need to strictly enforce language semantics at the parser level if it overcomplicates the grammar.
* **Reuse Existing Productions:** Strive to reuse existing grammar productions rather than introducing new ones. If an existing production almost works, explore and document the pros and cons of broadening it before creating a new one.
* **Simplicity:** Opt for simplicity wherever possible. Complex grammar rules are brittle and hard to maintain.
* **Discrete Steps:** Break the work into discrete, logical steps. (e.g., 1. Syntax/Grammar declarations, 2. AST node creation, 3. Parser logic, 4. Formatting/Highlighting).

## 2. Verification and Testing Protocol

It is critical that we have high confidence that the feature is complete and does not break existing functionality.

Before concluding the implementation, you must provide a **Verification Report** to the user containing:
1. **Testing Strategy:** Explain how you intend to verify the feature and test it.
2. **Current Test State:** Explain how the current tests work, what they validate, and what specific edge cases or sub-features are *not* covered.
3. **Proposed Improvements:** Propose new tests to cover the new syntax.
4. **Manual Verification:** Detail what manual verification steps are left for the user to perform (e.g., testing in a live IDE instance).

*Note: Run tests iteratively as you go to verify that your changes have not broken anything.*

## 3. Code Review Mode

If the user asks you to review an implementation of a language feature, evaluate the code against the implementation guidelines above:
* **Critique the Grammar:** Did they introduce a new production where an existing one could be broadened? Is the grammar overly restrictive?
* **Check the Tests:** Did they provide sufficient test coverage? Ask them to explain what is *not* covered.
* **Assess Simplicity:** Point out any areas where the parsing logic could be simplified.
