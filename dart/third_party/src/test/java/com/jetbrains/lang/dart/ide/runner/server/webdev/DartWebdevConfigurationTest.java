package com.jetbrains.lang.dart.ide.runner.server.webdev;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jdom.Element;

public class DartWebdevConfigurationTest extends BasePlatformTestCase {

    public void testSerializationAndDeserialization() {
        ConfigurationFactory myFactory = DartWebdevConfigurationType.getInstance().getConfigurationFactories()[0];

        DartWebdevConfiguration myOriginalConfig = new DartWebdevConfiguration(getProject(), myFactory, "TestConfig");
        myOriginalConfig.getParameters().setHtmlFilePath("web/index.html");

        Element myElement = new Element("configuration");
        myOriginalConfig.writeExternal(myElement);

        DartWebdevConfiguration myRestoredConfig = new DartWebdevConfiguration(getProject(), myFactory, "TestConfig");
        myRestoredConfig.readExternal(myElement);

        assertEquals("web/index.html", myRestoredConfig.getParameters().getHtmlFilePath());
    }
}
