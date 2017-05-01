/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.rutgers.winlab.common;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_OK;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 *
 * @author ubuntu
 */
public class HTTPUtility {

    public static final String CROSS_DOMAIN_HOST_IP = "INTR_IP";
    public static final String HTTP_METHOD_STATIC = "GET";
    public static final String HTTP_METHOD_DYNAMIC = "POST";
    public static final String HTTP_HEADER_HOST = "Host";
    public static final String HTTP_HEADER_LAST_MODIFIED = "Last-Modified";
    public static final String HTTP_HEADER_IF_MODIFIED_SINCE = "If-Modified-Since";
    public static final String OUTGOING_GATEWAY_DOMAIN_SUFFIX = "";
    public static final SimpleDateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    public static final String HTTP_RESPONSE_UNSUPPORTED_ACTION_FORMAT = "<h1>501 Not Implemented</h1>Unsupported HTTP method: %s%n";
    public static final String HTTP_RESPONSE_FAIL_INPROCESS = "<h1>500 Internal Server Error</h1>Error in processing: %s<pre>%s</pre>%n";
    public static final String HTTP_RESPONSE_FILE_NOT_FOUND_FORMAT = "<h1>404 Not Found</h1>Cannot find file: %s%n";

    public static void writeQuickResponse(HttpExchange exchange, int responseCode, String format, Object... params) throws IOException {
        byte[] data = String.format(format, params).getBytes();
        exchange.sendResponseHeaders(responseCode, data.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
            output.flush();
        }
        exchange.close();
    }

    public static void writeBody(HttpExchange exchange, byte[] data, int size, Date lastModified) throws IOException {
        if (lastModified != null) {
            HTTP_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
            exchange.getResponseHeaders().add(HTTP_HEADER_LAST_MODIFIED, HTTP_DATE_FORMAT.format(lastModified));
        }
        exchange.sendResponseHeaders(HTTP_OK, size);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data, 0, size);
            output.flush();
        }
        exchange.close();
    }

    public static void writeNotModified(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(HTTP_NOT_MODIFIED, -1);
        exchange.getResponseBody().close();
        exchange.close();

    }

    public static void writeBody(HttpExchange exchange, InputStream data, long size, Date lastModified) throws IOException {
        byte[] buf = new byte[1500];
        if (lastModified != null) {
            HTTP_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
            exchange.getResponseHeaders().add(HTTP_HEADER_LAST_MODIFIED, HTTP_DATE_FORMAT.format(lastModified));
        }
        exchange.sendResponseHeaders(HTTP_OK, size);

        try (OutputStream output = exchange.getResponseBody()) {
            int read;
            while (size > 0 && (read = data.read(buf, 0, (int)Math.min(size, buf.length))) > 0) {
                output.write(buf, 0, read);
                size -= read;
            }
            output.flush();
        }
        exchange.close();
    }
}
