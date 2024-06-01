## Inbox Tracking
 
The [Inbox Query](https://github.com/flutter/flutter-intellij/issues?utf8=✓&q=is%3Aissue+is%3Aopen+no%3Amilestone) contains all the

* open issues that
* have no milestone assignment.
 
The inbox tracker should do the following initial triage:

* Is the issue invalid? Close it, with a brief explanation.
* Is the issue a general question, like _"How can I make a blinking button?"_ Close it and redirect to [Stack Overflow](https://stackoverflow.com/tags/flutter) or [gitter](https://gitter.im/flutter/flutter); fodder for a redirecting response can be harvested from this [message](https://gist.github.com/pq/9c8293516b055b369e34e7410c52d2d8).
* Is the issue better filed against Flutter or the Dart SDK?  Move it using the GitHub [Issue Mover](https://github-issue-mover.appspot.com/).
* Is the issue an obvious duplicate?  Close it with a pointer to the duplicated issue.
* Is this issue a bug?  Add the `bug` label.
* Is this issue a feature?  Add the `enhancement` label.
* Is the issue a `P0` or a `P1`? Assign the relevant label.
  * For P0s, let the team know and find an immediate owner. Fixes for P0s get patched into the current stable release.
  * For P1s, assign an owner and ping them. We'll plan to get fixes for P1s into the next stable release.
* Milestone assignment:
  * For very high priority issues, assign to the current or upcoming milestones; these are ones you know people plan to work on imminently
  * For things that have a high likelihood of being triaged them into the next milestone during planning, assign to the 'On Deck' milestone
  * For things we're not willing to close, assign to the 'Backlog' milestone
* Assign any relevant `topic-` labels, and
* Edit the issue's title to best represent our new understanding of the issue; this will save time for every other person who needs to skim the issue titles in the future

## PR Bots
- if an issue with the `waiting for customer response` label is not responded to in 14 days, it's automatically closed with an appropriate message
- to move an issue to another repo, add the comment: `/move to flutter`

## flutter/flutter issues

For issues that clearly belong in the flutter/flutter repo, please do very initial triaging (for example, report 'flutter doctor -v' output with your bug), remove any of our github labels, and move the issue to the flutter/flutter repo by adding the comment to the issue: `/move to flutter`.

We don't have a process for monitoring or triaging IDE issue in the flutter/flutter repo, but likely should. Some candidate github searches:

- https://github.com/flutter/flutter/issues?q=is%3Aissue+is%3Aopen+label%3A%22dev%3A+ide+-+jetbrains%22
- https://github.com/flutter/flutter/labels/dev%3A%20ide%20-%20vscode

To move flutter/flutter issues to the flutter-intellij repo, add a comment to the issue: `/move to ide`.
