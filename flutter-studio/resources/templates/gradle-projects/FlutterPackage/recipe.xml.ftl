<?xml version="1.0"?>
<recipe>
    <mkdir at="${escapeXmlAttribute(projectOut)}/libs" />
    <mkdir at="${escapeXmlAttribute(resOut)}/drawable" />

    <merge from="root/settings.gradle.ftl"
             to="${escapeXmlAttribute(topOut)}/settings.gradle" />

    <instantiate from="root/build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />

    <instantiate from="root/AndroidManifest.xml.ftl"
                   to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
<#if hasInstantAppWrapper>
    <instantiate from="root/res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(baseFeatureResOut)}/values/strings.xml" />
<#else>
    <instantiate from="root/res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
</#if>

<#if generateKotlin>
    <instantiate from="root/test/app_package/ExampleInstrumentedTest.kt.ftl"
                   to="${escapeXmlAttribute(testOut)}/ExampleInstrumentedTest.kt" />
<#else>
    <instantiate from="root/test/app_package/ExampleInstrumentedTest.java.ftl"
                   to="${escapeXmlAttribute(testOut)}/ExampleInstrumentedTest.java" />
</#if>

<#if unitTestsSupported>
<#if generateKotlin>
    <instantiate from="root/test/app_package/ExampleUnitTest.kt.ftl"
                   to="${escapeXmlAttribute(unitTestOut)}/ExampleUnitTest.kt" />
<#else>
    <instantiate from="root/test/app_package/ExampleUnitTest.java.ftl"
                   to="${escapeXmlAttribute(unitTestOut)}/ExampleUnitTest.java" />
</#if>
    <dependency mavenUrl="junit:junit:4.12" gradleConfiguration="testCompile" />
</#if>

<dependency mavenUrl="com.android.support.test:runner:+" gradleConfiguration="androidTestCompile" />
<dependency mavenUrl="com.android.support.test.espresso:espresso-core:+" gradleConfiguration="androidTestCompile" />

<#if !createActivity>
    <mkdir at="${escapeXmlAttribute(srcOut)}" />
</#if>

<#if backwardsCompatibility!true>
    <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+" />
</#if>

<#if makeIgnore>
    <copy from="root/module_ignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />
</#if>

<#if enableProGuard>
    <instantiate from="root/proguard-rules.txt.ftl"
                   to="${escapeXmlAttribute(projectOut)}/proguard-rules.pro" />
</#if>

<#if hasMonolithicAppWrapper>
    <mkdir at="${monolithicAppOut}" />
    <instantiate from="root/monolithic-AndroidManifest.xml.ftl"
                   to="${monolithicAppOut}/src/main/AndroidManifest.xml" />
    <instantiate from="root/monolithic-build.gradle.ftl"
                   to="${monolithicAppOut}/build.gradle" />
    <#if makeIgnore>
        <copy from="root/module_ignore"
                to="${monolithicAppOut}/.gitignore" />
    </#if>
</#if>

<#if hasInstantAppWrapper>
    <mkdir at="${instantAppOut}" />
    <instantiate from="root/instantApp-build.gradle.ftl"
                   to="${instantAppOut}/build.gradle" />
    <#if makeIgnore>
        <copy from="root/module_ignore"
                to="${instantAppOut}/.gitignore" />
    </#if>

    <mkdir at="${baseFeatureOut}" />
    <instantiate from="root/baseFeature-AndroidManifest.xml.ftl"
                   to="${baseFeatureOut}/src/main/AndroidManifest.xml" />
    <merge from="root/baseFeature-ApplicationManifest.xml.ftl"
             to="${baseFeatureOut}/src/main/AndroidManifest.xml" />

    <instantiate from="root/baseFeature-build.gradle.ftl"
                   to="${baseFeatureOut}/build.gradle" />
    <#if makeIgnore>
        <copy from="root/module_ignore"
                to="${baseFeatureOut}/.gitignore" />
    </#if>
<#elseif isInstantApp && !isBaseFeature>
    <merge from="root/baseFeatureDependency-build.gradle.ftl"
             to="${baseFeatureOut}/build.gradle" />
</#if>

<#macro copyIconCommands destination>
    <#if buildApi gte 26 && targetApi gte 26>
        <#-- Copy adaptive-icons -->
        <copy from="root/res/mipmap-anydpi-v26/ic_launcher.xml"
                to="${destination}/mipmap-anydpi-v26/ic_launcher.xml" />
        <copy from="root/res/drawable/ic_launcher_background.xml"
                to="${destination}/drawable/ic_launcher_background.xml" />
        <copy from="root/res/mipmap-anydpi-v26/ic_launcher_round.xml"
                to="${destination}/mipmap-anydpi-v26/ic_launcher_round.xml" />
        <@copyMipmap destination=escapeXmlAttribute(destination)
                            icon="ic_launcher_foreground.png" />
    </#if>
    <#if buildApi gte 25 && targetApi gte 25>
        <@copyMipmap destination=escapeXmlAttribute(destination)
                            icon="ic_launcher_round.png" />
    </#if>

    <@copyMipmap destination=escapeXmlAttribute(destination) icon="ic_launcher.png" />
</#macro>

<#macro copyMipmap destination icon>
    <copy from="root/res/mipmap-hdpi/${icon}"
            to="${destination}/mipmap-hdpi/${icon}" />
    <copy from="root/res/mipmap-mdpi/${icon}"
            to="${destination}/mipmap-mdpi/${icon}" />
    <copy from="root/res/mipmap-xhdpi/${icon}"
            to="${destination}/mipmap-xhdpi/${icon}" />
    <copy from="root/res/mipmap-xxhdpi/${icon}"
            to="${destination}/mipmap-xxhdpi/${icon}" />
    <copy from="root/res/mipmap-xxxhdpi/${icon}"
            to="${destination}/mipmap-xxxhdpi/${icon}" />
</#macro>

<#if copyIcons>
    <#if !isLibraryProject>
        <@copyIconCommands destination=escapeXmlAttribute(resOut)/>
    <#elseif hasInstantAppWrapper || isBaseFeature>
        <@copyIconCommands destination=escapeXmlAttribute(baseFeatureResOut)/>
    </#if>
</#if>

<#if !isLibraryProject>
    <instantiate from="root/res/values/styles.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    <#if buildApi gte 22>
        <copy from="root/res/values/colors.xml"
                to="${escapeXmlAttribute(resOut)}/values/colors.xml" />
    </#if>
</#if>

<#if hasInstantAppWrapper || isBaseFeature>
    <instantiate from="root/res/values/styles.xml.ftl"
                   to="${escapeXmlAttribute(baseFeatureResOut)}/values/styles.xml" />
    <#if buildApi gte 22>
        <copy from="root/res/values/colors.xml"
                to="${escapeXmlAttribute(baseFeatureResOut)}/values/colors.xml" />
    </#if>
</#if>


<#if includeCppSupport>
    <mkdir at="${escapeXmlAttribute(nativeSrcOut)}" />

    <instantiate from="root/CMakeLists.txt.ftl"
                   to="${escapeXmlAttribute(projectOut)}/CMakeLists.txt" />
    <instantiate from="root/native-lib.cpp.ftl"
                   to="${escapeXmlAttribute(nativeSrcOut)}/native-lib.cpp" />
</#if>

</recipe>
