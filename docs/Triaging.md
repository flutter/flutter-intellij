## Inbox Tracking
 
The [Inbox Query](https://github.com/flutter/flutter-intellij/issues?q=is%3Aissue%20state%3Aopen%20-label%3AP0%20-label%3AP1%20-label%3AP2%20-label%3AP3) contains all the

* open issues that
* have no priority assignment.
 
The inbox tracker should do the following initial triage:

* Is the issue invalid? Close it, with a brief explanation.
* Is the issue a general question, like _"How can I make a blinking button?"_ Close it and redirect to [discord](https://github.com/flutter/flutter/blob/main/docs/contributing/Chat.md); fodder for a redirecting response can be harvested from this [message](https://gist.github.com/pq/9c8293516b055b369e34e7410c52d2d8).
* Is the issue better filed against Flutter?  Move it using the GitHub [issue transfer UI](https://docs.github.com/en/issues/tracking-your-work-with-issues/administering-issues/transferring-an-issue-to-another-repository#transferring-an-open-issue-to-another-repository).
* Is the issue better filed against the Dart SDK? Consider creating a new issue on the [Dart SDK](https://github.com/dart-lang/sdk/issues) or ask the author to do so (and close the original issue).
* Is the issue an obvious duplicate?  Close it with a pointer to the duplicated issue.
* Is this issue a bug?  Add the `bug` label.
* Is this issue a feature?  Add the `enhancement` label.
* Assign a priority label.
  * For P0s, let the team know and find an immediate owner. Fixes for P0s get patched into the current stable release.
  * For P1s, assign an owner and ping them. We'll plan to get fixes for P1s into the next stable release.
* Milestone assignment:
  * For very high priority issues, assign to the current or upcoming milestones; these are ones you know people plan to work on imminently
  * For things that have a high likelihood of being triaged them into the next milestone during planning, assign to the 'On Deck' milestone
  * For things we're not willing to close, assign to the 'Backlog' milestone
* Assign any relevant `topic-` labels, and
* Edit the issue's title to best represent our new understanding of the issue; this will save time for every other person who needs to skim the issue titles in the future.

## PR Bots
- if an issue with the `waiting for customer response` label is not responded to in 14 days, it's automatically closed with an appropriate message.
