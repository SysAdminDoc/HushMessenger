package com.facebook.messaging.model.attachment;

import android.net.Uri;

/** A video attachment's data, which carries several addresses. Here so a video can stand in for something that isn't audio. */
public final class VideoData {
    public Uri A09;
    public Uri A0A;
    public Uri A0B;
    public Uri A0C;

    public VideoData(Uri playable) {
        this.A0B = playable;
    }
}
