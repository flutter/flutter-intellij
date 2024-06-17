# Build your widgets inline with Hot UI

HotUI allows you to see and interact with your widgets directly in your IDE as you’re building them. This feature is very early so not all functionality is yet available in the version of Flutter you are using.

We call this feature “Hot UI” and, like Hot Reload, as you make the changes in your code, it updates the hosted UI directly. You can also interact with the hosted UI (like changing a color, as shown here) and that change goes directly into your code. To enable the Hot UI preview, enable this experiment in the Flutter plugin. Property editing is available on the stable Flutter channel and the device screen mirror will be available on master soon.

## Enabling
This feature is currently experimental. To enable, go to Preferences > Languages & Frameworks > Flutter
Then check "Enable Hot UI" under "Experiments".

## Outline view integration.
At the bottom of the Flutter outline view Hot UI provides a list of properties for the current Widget that can be edited inline. When a property edit is made, a hot reload will automatically be triggered so you can see the results of the change on the device.

## Code editor integration.
Your location in the code editor syncs with the outline view and the highlighted widget on the device to make it easier to see how your code relates to the running application. 

## Screen mirror (coming soon)
If your version of Flutter is new enough, you will see a screen mirror next to the editable list of properties. You can click on the screen mirror to select the widget in the editor and outline view.