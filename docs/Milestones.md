# Milestones and Planning

- we're using monthly [milestones](https://github.com/flutter/flutter-intellij/milestones)
- at the beginning of a milestone, we triage issues from the [Backlog](https://github.com/flutter/flutter-intellij/milestone/10) and [On Deck](https://github.com/flutter/flutter-intellij/milestone/11) milestones into the new milestone
- any work done or issues fixed should be assigned to the current milestone
- at the end of the milestone, we run through the [testing plan](https://github.com/flutter/flutter-intellij/blob/master/docs/testing.md) and validate the candidate release
- before releasing, we update the [changelog](https://github.com/flutter/flutter-intellij/blob/master/resources/META-INF/plugin.xml#L22) (based on the work tracking in the milestone)
- we then [build and release](../docs/building.md), and iterate
