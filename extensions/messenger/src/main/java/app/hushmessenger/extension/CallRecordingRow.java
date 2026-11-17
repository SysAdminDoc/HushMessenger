package app.hushmessenger.extension;

import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * The rows under Record calls (#193): which calls record themselves, and how long their files are kept. A folder of
 * calls grows on its own and nobody goes back through it, so the keeping offers a week, a month or three months, and
 * the tidy-up runs after the next recording is saved rather than on a timer of its own.
 */
final class CallRecordingRow {
    private final SettingsActivity page;
    private final List<RadioButton> choices = new ArrayList<>();
    private final List<RadioButton> scopes = new ArrayList<>();

    CallRecordingRow(SettingsActivity page) {
        this.page = page;
    }

    /** Which calls record themselves, with the chosen chats underneath when that's the one picked. */
    void addScopeTo(LinearLayout content) {
        page.ui.add(content, page.ui.text(page.text.get(CallRecordings.SCOPE), 15, page.ui.text, true), 8);
        RadioGroup group = new RadioGroup(page);
        group.setTag("call_recording_scope_choices");
        group.setOrientation(LinearLayout.VERTICAL);
        for (String scope : CallRecordings.SCOPES) {
            RadioButton choice = page.radio(CallRecordings.SCOPE + "_" + scope, page.text.get(CallRecordings.SCOPE + "_" + scope));
            choice.setOnClickListener(clicked -> pickScope(scope));
            group.addView(choice, new RadioGroup.LayoutParams(-1, -2));
            scopes.add(choice);
        }
        page.ui.add(content, group, 4);
        page.ui.add(content, page.ui.text(page.text.get("call_recording_scope_help"), 13, page.ui.muted, false), 8);
        fill();
    }

    void addKeepTo(LinearLayout content) {
        page.ui.add(content, page.ui.text(page.text.get("call_recording_keep"), 15, page.ui.text, true), 8);
        RadioGroup group = new RadioGroup(page);
        group.setTag("call_recording_keep_choices");
        group.setOrientation(LinearLayout.VERTICAL);
        for (String days : CallRecordings.KEEP_CHOICES) {
            RadioButton choice = page.radio("call_recording_keep_" + days, label(days));
            choice.setOnClickListener(clicked -> pick(days));
            group.addView(choice, new RadioGroup.LayoutParams(-1, -2));
            choices.add(choice);
        }
        page.ui.add(content, group, 4);
        page.ui.add(content, page.ui.text(page.text.get("call_recording_keep_help"), 13, page.ui.muted, false), 8);
        fill();
    }

    /** Shows what's saved, so coming back to the page or a change made elsewhere reads right. */
    void fill() {
        String now = String.valueOf(CallRecordings.keepDays());
        for (RadioButton choice : choices) choice.setChecked(choice.getTag().equals("call_recording_keep_" + now));
        String scope = CallRecordings.scope();
        for (RadioButton choice : scopes) choice.setChecked(choice.getTag().equals(CallRecordings.SCOPE + "_" + scope));
    }

    private String label(String days) {
        return page.text.get(id(days), days);
    }

    private static String id(String days) {
        return "0".equals(days) ? "call_recording_keep_all" : "call_recording_keep_days";
    }

    private void pick(String days) {
        if (Settings.preferences != null) Settings.preferences.edit().putString(CallRecordings.KEEP, days).apply();
        fill();
        // The label itself goes in untouched, since the toast's own text is what gets turned around for a right to left locale.
        page.feedback(page.text.get("call_recording_keep_picked", page.text.format(id(days), days)), Toast.LENGTH_SHORT);
    }

    private void pickScope(String scope) {
        if (Settings.preferences != null) Settings.preferences.edit().putString(CallRecordings.SCOPE, scope).apply();
        fill();
        page.fillRecordCallers();
        page.feedback(page.text.get("call_recording_scope_picked", page.text.format(CallRecordings.SCOPE + "_" + scope)),
            Toast.LENGTH_SHORT);
    }
}
