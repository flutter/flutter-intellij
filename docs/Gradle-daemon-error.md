If you work with Gradle projects you might see an error like this:
```
The newly created daemon process has a different context than expected.
Java home is different.
```
I don't know what causes this, but it appears to be internal to Gradle. IntelliJ is trying to sync the project and  this error occurs when Gradle tries to start its daemon. The cached and given locations for the Java JDK are different. The cached one can't be used in IntelliJ so there appears to be no way to fix it.

I found a work around. It is painful. Remove all JDKs, unset JAVA_HOME (if set), and restart the computer. Start IntelliJ and see a different error for the Gradle-based project. Shut down IntelliJ, install a JDK, set JAVA_HOME (if used), and restart the computer. You will have to reconfigure the SDKs in IntelliJ (use the Project Structure editor). If the Gradle project does not re-sync automatically, just restart IntelliJ. You should at least get past the Gradle daemon creation problem doing this.

There are two ways you might see this problem. If you use the `intellij-community` sources you are using at least one Gradle-based module. The other is if you are working with the plugin integration tests. That is also a Gradle-based module.

UPDATE

This problem occurred again. This time I fixed it by defining JAVA_HOME, without deleting anything or restarting the computer. I set JAVA_HOME to the "Contents" directory of the JDK that is distributed with Android Studio. Then I started IntelliJ and reset the Project Structure JDKs to use that distro. After that, a build started automatically and the Gradle daemon was created properly. Still no idea what the underlying problem is, though.