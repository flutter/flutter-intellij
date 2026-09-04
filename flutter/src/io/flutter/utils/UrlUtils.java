package io.flutter.utils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class UrlUtils {
  public static String generateHtmlFragmentWithHrefTags(String input) {
    StringBuilder builder = new StringBuilder();
    for (String token : input.split(" ")) {
      if (!builder.isEmpty()) {
        builder.append(" ");
      }
      try {
        URL url = new URI(token).toURL();
        builder.append("<a href=\"").append(url).append("\">").append(url).append("</a>");
      }
      catch (IllegalArgumentException | MalformedURLException | URISyntaxException e) {
        builder.append(token);
      }
    }
    return builder.toString();
  }
}
