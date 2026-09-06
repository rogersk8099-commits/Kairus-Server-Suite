package gg.neonnexus.smpplatform.integrations.link;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** HMAC protects low-entropy human codes at rest; host configuration must provide the key without committing it. */
public final class CodeHasher {
    private final SecretKey key;
    public CodeHasher(SecretKey key) { this.key = key; }
    public String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot hash link code", e); }
    }
}
