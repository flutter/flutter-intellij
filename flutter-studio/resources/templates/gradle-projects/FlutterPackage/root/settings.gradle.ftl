include ':${projectName}'
<#if hasMonolithicAppWrapper>include ':${monolithicAppProjectName}'</#if>
<#if hasInstantAppWrapper>include ':${baseFeatureName}', ':${instantAppProjectName}'</#if>
