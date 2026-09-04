We have an early-access version of the plugin, called the dev channel. This version is updated daily, containing all changes made to the
main branch at the time of the build. To use it, you need to configure your settings to look for plugins on the dev channel
repository.

## Configuring settings for the dev release

NOTE: If you already have the Flutter plugin installed, please uninstall it before proceeding. Once you have the plugin installed from one
channel it will not be available on other channels.

Open the Plugins preference page (macOS) or settings page (Windows, Linux). Click the gear at the top-right and select _Manage Plugin
Repositories_.

![Screen shot of repositories page](https://lh4.googleusercontent.com/zo9vANXp01YXJC_tQGuiLJxgbdtRFWV-VXViIx_MnqCphGi8PhhQbNTa7H-8ogl0AxIpU7enEQpAs3FZ8lSd0eUw4FpSkxXRkDoQj9uCpvs93D4pTdIrjyK0--q9xBPXTQ0MN7PB)

A list editor will pop up that allows you to edit custom plugin repositories. Add this to the list:
[https://plugins.jetbrains.com/plugins/dev/list](https://plugins.jetbrains.com/plugins/dev/list)

![Screen shot of repositories](https://lh3.googleusercontent.com/W4o9xr8IAx0ROAc5NLeTFMbV8b_0ONukXiQdbU9nPbsY3l1eYsqPhyRMU5GkCA93JgqEensjFHSP_AFY0UAGLtZF2epZmH-GoDNlK0okegrF-jsdpy0GuPPEt4CnqzwalWJVril3)

Then click OK. The version of the Flutter plugin you see in the Marketplace list will be the dev channel version.

![Screen shot of plugins](https://lh6.googleusercontent.com/q8L3R4Rqyjb9gbrKui1SK7YvVLejBXg4TLE0Nif28nxRj69pxrgQY4cwFGiCHuBEegar5MvUgCWY2ETn2lABzG2HjZznPNAtprQRGoUenFrbxpsPNRM-gnxMCAkOpGcI3bGJtRwz)

## Revert to stable releases

To revert to stable releases, access "Manage Plugin Repositories" as described above, then remove the dev URL. This will prevent the IDE
from suggesting the dev releases in the future.

Go to the [Flutter plugin versions](https://plugins.jetbrains.com/plugin/9212-flutter/versions/stable) and download the stable release you'd
like to use.

Install the downloaded plugin by going to the Plugin page and clicking on the gear icon, then selecting "Install plugin from disk".