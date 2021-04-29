package io.flutter.utils;

import java.net.MalformedURLException;
import java.net.URL;

public class UrlUtils {
    public static String generateHtmlFragmentWithHrefTags(String input) {
        StringBuilder builder = new StringBuilder();
        for (String token : input.split(" ")) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            try {
                URL url = new URL(token);
                builder.append("<a href=\"").append(url).append("\">").append(url).append("</a>");
            } catch(MalformedURLException e) {
                builder.append(token);
            }
        }
        return builder.toString();
    }
}
