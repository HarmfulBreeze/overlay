package com.jcs.overlay.utils;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;

// The methods are used by Moshi for serialization/deserialization.
@SuppressWarnings("unused")
public class Uint64Adapter {
    @ToJson
    String toJson(Long aLong) {
        return Long.toUnsignedString(aLong);
    }

    @FromJson
    Long fromJson(String aLong) {
        return Long.parseUnsignedLong(aLong);
    }
}
