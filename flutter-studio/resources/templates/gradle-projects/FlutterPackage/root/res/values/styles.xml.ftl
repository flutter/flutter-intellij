<resources>

    <!-- Base application theme. -->
    <style name="AppTheme" parent="<#if
            backwardsCompatibility!true>Theme.AppCompat<#else><#if
            minApiLevel gte 21>android:Theme.Material<#elseif
            minApiLevel gte 11>android:Theme.Holo<#else
            >android:Theme.Light</#if></#if><#if
            minApiLevel gte 11>.Light</#if><#if
            minApiLevel gte 14>.DarkActionBar</#if>">
        <!-- Customize your theme here. -->
<#if (buildApi gte 22) && backwardsCompatibility!true>
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="colorPrimaryDark">@color/colorPrimaryDark</item>
        <item name="colorAccent">@color/colorAccent</item>
</#if>
    </style>

</resources>
