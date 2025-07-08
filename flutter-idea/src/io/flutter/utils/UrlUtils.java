package io.flutter.utils;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlUtils {
    public static String generateHtmlFragmentWithHrefTags(String input) {
        StringBuilder builder = new StringBuilder();
        for (String token : input.split(" ")) {
            if (!builder.isEmpty()) {
                builder.append(" ");
            }
            try {
                final URI uri = new URI(token);
                builder.append("<a href=\"").append(uri).append("\">").append(uri).append("</a>");
            }
            catch (URISyntaxException e) {
                builder.append(token);
            }
        }
        return builder.toString();
    }
}
