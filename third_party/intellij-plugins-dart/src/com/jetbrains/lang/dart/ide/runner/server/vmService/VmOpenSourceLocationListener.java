package com.jetbrains.lang.dart.ide.runner.server.vmService;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.roderick.weberknecht.WebSocket;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketException;
import de.roderick.weberknecht.WebSocketMessage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO(cbernaschina) remove when changes to Dart Plugin land
 *
 * This class manages the openSourceLocation VM service registration and exposes the request as an event to listeners.
 */
public class VmOpenSourceLocationListener {

  private interface MessageSender {
    void sendMessage(JsonObject message);
    void close();
  }

  public interface Listener {
    void onRequest(@NotNull String isolateId, @NotNull String scriptId, int tokenPos);
  }

  /**
   * Connect to the VM observatory service via the specified URI
   *
   * @return an API object for interacting with the VM service (not {@code null}).
   */
  public static VmOpenSourceLocationListener connect(@NotNull final String url) throws IOException {

    // Validate URL
    final URI uri;
    try {
      uri = new URI(url);
    }
    catch (URISyntaxException e) {
      throw new IOException("Invalid URL: " + url, e);
    }
    String wsScheme = uri.getScheme();
    if (!"ws".equals(wsScheme) && !"wss".equals(wsScheme)) {
      throw new IOException("Unsupported URL scheme: " + wsScheme);
    }

    // Create web socket and observatory
    final WebSocket webSocket;
    try {
      webSocket = new WebSocket(uri);
    }
    catch (WebSocketException e) {
      throw new IOException("Failed to create websocket: " + url, e);
    }
    final VmOpenSourceLocationListener listener = new VmOpenSourceLocationListener(new MessageSender() {
      @Override
      public void sendMessage(JsonObject message) {
        try {
          webSocket.send(message.toString());
        }
        catch (WebSocketException e) {
          // ignore
          e.printStackTrace();
        }
      }

      @Override
      public void close() {
        try {
          webSocket.close();
        }
        catch (WebSocketException e) {
          // ignore
          e.printStackTrace();
        }
      }
    });

    webSocket.setEventHandler(new WebSocketEventHandler() {
      final JsonParser parser = new JsonParser();

      @Override
      public void onClose() {
        // ignore
      }

      @Override
      public void onMessage(WebSocketMessage message) {
        listener.onMessage(parser.parse(message.getText()).getAsJsonObject());
      }

      @Override
      public void onOpen() {
        listener.onOpen();
      }

      @Override
      public void onPing() {
        // ignore
      }

      @Override
      public void onPong() {
        // ignore
      }
    });

    // Establish WebSocket Connection
    try {
      webSocket.connect();
    } catch (WebSocketException e) {
      throw new IOException("Failed to connect: " + url, e);
    }
    return listener;
  }

  final MessageSender sender;
  final List<Listener> listeners = new ArrayList<>();

  private VmOpenSourceLocationListener(@NotNull final MessageSender sender) {
    this.sender = sender;
  }

  public void addListener(@NotNull Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(@NotNull Listener listener) {
    listeners.remove(listener);
  }

  public void disconnect() {
    sender.close();
  }

  private void onMessage(@NotNull final JsonObject message) {
    final JsonElement id;
    final String isolateId;
    final String scriptId;
    final int tokenPos;
    try {
      final String jsonrpc = message.get("jsonrpc").getAsString();
      if (!"2.0".equals(jsonrpc)) {
        return;
      }

      final String method = message.get("method").getAsString();
      if (!"openSourceLocation".equals(method)) {
        return;
      }

      id = message.get("id");
      if (id == null || !id.isJsonPrimitive()) {
        return;
      }

      final JsonObject params = message.get("params").getAsJsonObject();
      if (params == null) {
        return;
      }

      isolateId = params.get("isolateId").getAsString();
      if (isolateId == null || isolateId.isEmpty()) {
        return;
      }

      scriptId = params.get("scriptId").getAsString();
      if (scriptId == null || scriptId.isEmpty()) {
        return;
      }

      tokenPos = params.get("tokenPos").getAsInt();

    } finally {}

    final JsonObject response = new JsonObject();
    response.addProperty("jsonrpc", "2.0");
    response.add("id", id);
    final JsonObject result = new JsonObject();
    result.addProperty("type", "Success");
    response.add("result", result);
    sender.sendMessage(response);

    for (Listener listener : listeners) {
      listener.onRequest(isolateId, scriptId, tokenPos);
    }
  }

  private void onOpen() {
    final JsonObject message = new JsonObject();
    message.addProperty("jsonrpc", "2.0");
    message.addProperty("method", "_registerService");
    final JsonObject params = new JsonObject();
    params.addProperty("service", "openSourceLocation");
    params.addProperty("alias", "IntelliJ");
    message.add("params", params);
    sender.sendMessage(message);
  }
}
