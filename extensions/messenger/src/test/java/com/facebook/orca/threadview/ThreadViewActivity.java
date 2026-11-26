package com.facebook.orca.threadview;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/** Stands in for Messenger's older chat activity, which page and business chats open in. */
public class ThreadViewActivity extends Activity {
    /** Its intent for a chat's key, which Messenger names differently in each build. */
    public static Intent A1H(Context context, Object chat) {
        return new Intent(context, ThreadViewActivity.class).putExtra("chat", String.valueOf(chat));
    }
}
