package gg.neonnexus.smpplatform.integrations.api;

import java.net.http.HttpHeaders;

public record ApiResponse(int statusCode, String body, HttpHeaders headers) {
    public boolean successful() { return statusCode >= 200 && statusCode < 300; }
}
