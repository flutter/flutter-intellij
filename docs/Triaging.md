## Inbox Tracking

The [Inbox Query](https://github.com/flutter/flutter-intellij/issues?q=is%3Aissue%20state%3Aopen%20-label%3AP0%20-label%3AP1%20-label%3AP2%20-label%3AP3)
contains all the

* open issues that
* have no priority assignment.

The inbox tracker should do the following initial triage:

* Is the issue invalid? Close it, with a brief explanation.
* Is the issue a general question, like _"How can I make a blinking button?"_ Close it and redirect
  to [discord](https://github.com/flutter/flutter/blob/main/docs/contributing/Chat.md); fodder for a redirecting response can be harvested
  from this [message](https://gist.github.com/pq/9c8293516b055b369e34e7410c52d2d8).
* Is the issue better filed against Flutter? Move it using the
  GitHub [issue transfer UI](https://docs.github.com/en/issues/tracking-your-work-with-issues/administering-issues/transferring-an-issue-to-another-repository#transferring-an-open-issue-to-another-repository).
* Is the issue better filed against the Dart SDK? Consider creating a new issue on the [Dart SDK](https://github.com/dart-lang/sdk/issues)
  or ask the author to do so (and close the original issue).
* Is the issue an obvious duplicate? Close it with a pointer to the duplicated issue.
* Is this issue a bug? Add the `bug` label.
* Is this issue a feature? Add the `enhancement` label.
* Assign a priority label.
    * For P0s, let the team know and find an immediate owner. Fixes for P0s get patched into the current stable release.
    * For P1s, assign an owner and ping them. We'll plan to get fixes for P1s into the next stable release.
* Milestone assignment:
    * For very high priority issues, assign to the current or upcoming milestones; these are ones you know people plan to work on imminently
    * For things that have a high likelihood of being triaged them into the next milestone during planning, assign to the 'On Deck'
      milestone
    * For things we're not willing to close, assign to the 'Backlog' milestone
* Assign any relevant `topic-` labels, and
* Edit the issue's title to best represent our new understanding of the issue; this will save time for every other person who needs to skim
  the issue titles in the future.

## PR Bots

- if an issue with the `waiting for customer response` label is not responded to in 14 days, it's automatically closed with an appropriate
  message.

## Using gemini CLI for triage

### Basic setup with GitHub extension

Set up gemini CLI to have extensions relevant for triage:

1. Get a GitHub personal access token ([instructions](https://github.com/settings/personal-access-tokens/new))
2. Install the GitHub Gemini extension
   `gemini extensions install https://github.com/github/github-mcp-server` ([doc with more details](https://github.com/github/github-mcp-server/blob/main/docs/installation-guides/install-gemini-cli.md))
3. Start `gemini` and verify that you have the extension by calling `/extensions list`

Once in the gemini CLI, you can say something like "help me with triage". Example response:

```
✦ Here is the first issue to triage:

  Issue #458: Can't debug single line lambda in IntelliJ

   * URL: https://github.com/flutter/flutter-intellij/issues/458
   * Summary: It's not possible to set a breakpoint on the body of a single-line lambda function. The IDE sets the breakpoint on the containing method call instead of the lambda's inner expression.
   * Labels: topic-debugging
   * Last Updated: 2025-09-29

  This looks like a long-standing feature request. It seems useful for debugging. I would suggest the following:

   * Priority: This could be a P2 or P3. It's a useful feature but there is a workaround (expanding the lambda to a block body).
   * Action: I can try to reproduce this with a minimal project to confirm it's still an issue.

  What do you think?
```

Notes:
- There are instructions for gemini in the `tool/triage/GEMINI.md` file, and gemini should be able to access this whenever you start
`gemini` in the flutter-intellij directory.
- This extension can also help with reviewing PRs, or any other tasks that require context from GitHub.

### (Experimental) Embeddings extension for comparing issues and search

The general concept of embeddings is that for each issue in our repository, gemini can create a local "embedding" document, which is a
space-efficient vector representation of the issue. Then, the vector representations can quickly be compared to each other or to a query (
once the query is also turned into an embedding), so that we can do things like generate groups of duplicate issues or run a fuzzy search of
issues. For more information, see [embeddings doc](https://ai.google.dev/gemini-api/docs/embeddings).

1. Get a gemini API key (there are internal instructions for this)
2. Install the embeddings extension: `gemini extensions install https://github.com/jakemac53/embeddings_playground`
3. Similarly as above, you can enter gemini and check that it's active with `/extensions list`

To use this extension, you can ask things like "generate a list of duplicate issues that are open"

Note: Jake put this extension together over a few days during our hackathon, and I've only barely tried it. So there are probably many ways
it can be improved.
