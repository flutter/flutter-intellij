package io.flutter.integrationTest

import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.wait
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import io.flutter.integrationTest.utils.newProjectDialog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Tag("ui")
@ExtendWith(UseLatestDownloadedIdeBuild::class)
class CreateNewFlutterMobileAppTest {

    private lateinit var run: BackgroundRun

    @BeforeEach
    fun setup() {
        run = Setup.setupTestContextIC(javaClass.simpleName).runIdeWithDriver()
    }

    @AfterEach
    fun tearDown() {
        if (::run.isInitialized) {
            run.closeIdeAndWait()
        }
    }

    @Test
    fun testCreateNewFlutterMobileAppWorkflow() {
        run.driver.withContext {
            // 1. Start IDE (handled by setupTestContextIC and UseLatestDownloadedIdeBuild)
            // No explicit action needed here as the context is already set up.

            // 2. In the menu, go to File -> New -> Project...
            //    This is now handled by the welcome screen interaction.

            // 3. In the New Project Dialog, select Flutter in the Generators list.
            welcomeScreen {
                createNewProjectButton.click()
                newProjectDialog {
                    chooseProjectType("Flutter")
                    
                    // Assert that the Flutter SDK text box is present and not empty.
                    // This relies on the framework's internal SDK path validation which
                    // makes the 'Next' button enabled if valid.
                    Assertions.assertTrue(nextButton.isEnabled(), "Flutter SDK path should be set and Next button enabled.")

                    // 4. Select Next.
                    nextButton.click()

                    // Assert that the new project dialog is open and contains the expected fields.
                    Assertions.assertTrue(projectNameLabel.present(), "Project name field should be visible")
                    Assertions.assertTrue(projectLocationLabel.present(), "Project location field should be visible")

                    // 5. Name the project.
                    val testProjectName = "it_test_project_${System.currentTimeMillis()}"
                    projectNameInput.doubleClick()
                    keyboard {
                        typeText(testProjectName)
                    }

                    // 6. Ensure that Android and IOS platforms are selected
                    Assertions.assertNotNull(androidRadioButton)

                    Assertions.assertTrue(androidRadioButton.isSelected())
                    Assertions.assertTrue(iosRadioButton.isSelected())

                    linuxButton.click()
                    macOsButton.click()
                    webButton.click()
                    windowsButton.click()
                    Assertions.assertTrue(!linuxButton.isSelected())

                    // 7. Add a description.
                    Assertions.assertTrue(descriptionLabel.present(), "Description field should be visible")
                    descriptionInput.doubleClick()
                    // The driver needs a method to get the UI component hierarchy for debugging.
                    // This is a placeholder for that functionality.
                 //   println(descriptionInput)
                    keyboard {
                        typeText("This is a test Flutter application created by the integration test bot.")
                    }

                    // 8. Ensure the application type is selected for the project type
                    // Todo Fix this
                    //Assertions.assertTrue(applicationTypeDropDown.getSelectedItem() == "Application")
                    
                    // 9. Ensure that the android language is Kotlin
                    Assertions.assertTrue(kotlinRadioButton.isSelected)

                    // 10. Click create
                    createButton.click()



                    wait(30.seconds)
                }
                
            }
        }
    }
}
