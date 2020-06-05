package com.jcs.overlay.websocket.messages.J2W;

/**
 * Class that should be extended by all classes that represent a message to be sent to the webapp.<br>
 * <p>
 * These message classes should all have a messageType field that is not equal to "OverrideThis" and implement
 * the {@link WebappMessage#getMessageType()} method.<br>
 * <p>
 * More methods might be added in the future.
 */
public abstract class WebappMessage {
    protected transient String messageType = "OverrideThis";

    public abstract String getMessageType();
}
