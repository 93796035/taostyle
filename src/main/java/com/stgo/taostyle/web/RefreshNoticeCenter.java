package com.stgo.taostyle.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/websocket/refreshNotice")
public class RefreshNoticeCenter {

    private static final String GUEST_PREFIX = "Guest";
    private static final AtomicInteger connectionIds = new AtomicInteger(0);
    private static final HashMap<String, CopyOnWriteArraySet<RefreshNoticeCenter>> connectionPoolMap =
            new HashMap<String, CopyOnWriteArraySet<RefreshNoticeCenter>>();

    private final String nickname;
    private Session session;

    public RefreshNoticeCenter() {
        nickname = GUEST_PREFIX + connectionIds.getAndIncrement();
    }

    @OnOpen
    public void start(
            Session session) {
        this.session = session;
        String userNameStr = session.getUserPrincipal().getName();
        int pos = userNameStr.indexOf('*');
        String key = userNameStr.substring(pos + 1);
        CopyOnWriteArraySet<RefreshNoticeCenter> set = connectionPoolMap.get(key);
        if (set == null) {
            set = new CopyOnWriteArraySet<RefreshNoticeCenter>();
            connectionPoolMap.put(key, set);
        }
        if (!set.contains(this)) {
            set.add(this);
        }
    }

    @OnClose
    public void end() {
        String userNameStr = session.getUserPrincipal().getName();
        int pos = userNameStr.indexOf('*');
        String key = userNameStr.substring(pos + 1);
        CopyOnWriteArraySet<RefreshNoticeCenter> set = connectionPoolMap.get(key);
        set.remove(this);
    }

    @OnMessage
    public void incoming(
            String message) {
    }

    @OnError
    public void onError(
            Throwable t) throws Throwable {
        String userNameStr = session.getUserPrincipal().getName();
        int pos = userNameStr.indexOf('*');
        String key = userNameStr.substring(pos + 1);
        broadcast("something is wrong", key);
    }

    public static void broadcast(
            String msg,
            String personId) {
        CopyOnWriteArraySet<RefreshNoticeCenter> set = connectionPoolMap.get(personId);
        if (set == null || set.size() == 0) {
            return;
        }

        for (RefreshNoticeCenter client : connectionPoolMap.get(personId)) {
            try {
                synchronized (client) {
                    client.session.getBasicRemote().sendText(msg);
                }
            } catch (IOException e) {
                // log.debug("Chat Error: Failed to send message to client", e);
                connectionPoolMap.remove(client);
                try {
                    client.session.close();
                } catch (IOException e1) {
                    // Ignore
                }
                String message = String.format("* %s %s", client.nickname, "has been disconnected.");
                broadcast(message, personId);
            }
        }
    }
}
