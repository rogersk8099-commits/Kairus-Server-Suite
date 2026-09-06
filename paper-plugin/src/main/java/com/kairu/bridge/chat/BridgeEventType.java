package com.kairu.bridge.chat;

/** Event names sent to the control plane. Keep these stable: they are an API contract, not display text. */
public enum BridgeEventType {
    CHAT,
    PLAYER_JOIN,
    PLAYER_LEAVE,
    PLAYER_DEATH,
    PLAYER_ADVANCEMENT,
    SERVER_STARTED,
    SERVER_STOPPING,
    MAINTENANCE
}
