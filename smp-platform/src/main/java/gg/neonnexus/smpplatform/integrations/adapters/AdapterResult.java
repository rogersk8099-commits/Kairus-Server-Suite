package gg.neonnexus.smpplatform.integrations.adapters;

import java.util.Optional;

public record AdapterResult<T>(boolean available, Optional<T> value, String detail) {
    public static <T> AdapterResult<T> unavailable(String detail) { return new AdapterResult<>(false, Optional.empty(), detail); }
    public static <T> AdapterResult<T> of(T value) { return new AdapterResult<>(true, Optional.ofNullable(value), ""); }
    public static <T> AdapterResult<T> failed(String detail) { return new AdapterResult<>(true, Optional.empty(), detail); }
}
