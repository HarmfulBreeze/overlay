module overlay.main {
    requires java.base;
    requires java.desktop;
    requires java.net.http;
    requires org.jetbrains.annotations;
    // JNA
    requires com.sun.jna;
    requires com.sun.jna.platform;
    // Apache Lang3
    requires org.apache.commons.lang3;
    // Orianna
    requires com.merakianalytics.orianna;
    requires okhttp3;
    requires org.joda.time;
    requires com.fasterxml.jackson.databind;
    // Moshi
    requires moshi;
    requires com.squareup.moshi.adapters;
    requires kotlin.stdlib;
    requires jdk.unsupported; // required for reflection without no-arg constructor
    // SLF4J
    requires org.slf4j;
    // Java-WebSocket
    requires Java.WebSocket;
    // JCEF
    requires jcef;
    // Misc
    requires sysout.over.slf4j;
    requires typesafe.config;

    // opens declarations for Moshi reflection
    opens com.jcs.overlay.utils;
    opens com.jcs.overlay.websocket.holders;
    opens com.jcs.overlay.websocket.messages.C2J;
    opens com.jcs.overlay.websocket.messages.C2J.champselect;
    opens com.jcs.overlay.websocket.messages.C2J.summoner;
    opens com.jcs.overlay.websocket.messages.J2W;
    opens com.jcs.overlay.websocket.messages.J2W.enums;
}