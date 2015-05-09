package org.phoenixframework.channels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class Push {
    private static final Logger LOG = Logger.getLogger(Push.class.getName());

    private Channel channel = null;
    private String event = null;
    private String refEvent = null;
    private Payload payload = null;
    private Envelope receivedEnvelope = null;
//  TODO  private List afterHooks = null;
    private Map<String, List<IMessageCallback>> recHooks = new HashMap<>();
    private boolean sent = false;

    Push(final Channel channel, final String event, final Payload payload) {
        this(channel, event, payload, null);
    }

    Push(final Channel channel, final String event, final Payload payload, final Push mergePush) {
        this.channel = channel;
        this.event = event;
        this.payload = payload;
        if(mergePush != null) {
            // TODO - Merge afterHooks ?
            this.recHooks.putAll(mergePush.getRecHooks());
        }
    }

    void send() throws IOException {
        final String ref = channel.getSocket().makeRef();
        LOG.log(Level.FINE, "Push send, ref={0}", ref);

        this.refEvent = Socket.replyEventName(ref);
        this.receivedEnvelope = null;

        this.channel.on(this.refEvent, new IMessageCallback() {
            @Override
            public void onMessage(final Envelope envelope) {
                Push.this.receivedEnvelope = envelope;
                Push.this.matchReceive(((Payload)receivedEnvelope.getPayload()).getResponseStatus(), envelope, ref);
                Push.this.cancelRefEvent();
                // TODO Push.this.cancelAfters();
            }
        });

        // TODO this.startAfters();
        this.sent = true;
        final Envelope envelope = new Envelope(this.channel.getTopic(), this.event, this.payload, ref);
        this.channel.getSocket().push(envelope);
    }

    /**
     * @param status
     * @param callback
     *
     * @return This instance's self
     */
    Push receive(final String status, final IMessageCallback callback) {
        if(this.receivedEnvelope != null) {
            final String receivedStatus = this.receivedEnvelope.getPayload().getResponseStatus();
            if(receivedStatus != null && receivedStatus.equals(status)) {
                callback.onMessage(this.receivedEnvelope); // TODO - What is best to provide here. Polymorphic messages.
            }
        }
        synchronized(recHooks) {
            List<IMessageCallback> statusHooks = this.recHooks.get(status);
            if(statusHooks == null) {
                statusHooks = new ArrayList<>();
                this.recHooks.put(status, statusHooks);
            }
            statusHooks.add(callback);
        }

        return this;
    }

    // TODO after(ms, callback)
    // TODO cancelAfter

    private void matchReceive(final String status, final Envelope envelope, final String ref) {
        synchronized (recHooks) {
            final List<IMessageCallback> statusCallbacks = this.recHooks.get(status);
            if(statusCallbacks != null) {
                for (final IMessageCallback callback : statusCallbacks) {
                    callback.onMessage(envelope);
                }
            }
        }
    }

    Channel getChannel() {
        return channel;
    }

    String getEvent() {
        return event;
    }

    Payload getPayload() {
        return payload;
    }

    Envelope getReceivedEnvelope() {
        return receivedEnvelope;
    }

    Map<String, List<IMessageCallback>> getRecHooks() {
        return recHooks;
    }

    boolean isSent() {
        return sent;
    }

    private void cancelRefEvent() {
        this.channel.off(this.refEvent);
    }
}