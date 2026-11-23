package com.classdrop.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public final class RoomLinkHandler implements HttpHandler {
    private final StaticHandler staticHandler = new StaticHandler();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // GET /r/{code} serves the index.html page
        // The frontend app.js reads the URL path to pre-fill the roomCode
        staticHandler.handle(exchange);
    }
}
