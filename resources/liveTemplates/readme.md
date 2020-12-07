## How this works

`flutter_miscellaneous.xml` contains the Flutter related live templates that this plugin
supports. In order to make authoring the templates, and reviewing changes to them, easier,
the actual text content of the templates are split out into separate files, one for each
template. A build step (below) will copy the contents of the inidividual files in-line
into the IntelliJ live template file.

## Adding a new template

To add a new template, create a new entry in the flutter_miscellaneous.xml file. This should
be a valid live template entry. You can provide any content you want for the `value` field.

Next, create a `$name.txt` file. This will hold the actual content of the `value` field. The
re-generate step will copy the contents from that txt file into the xml `value` field.

## Re-generating the template file

To re-generate the flutter_miscellaneous.xml template file, run:

```
./bin/plugin generate
```
