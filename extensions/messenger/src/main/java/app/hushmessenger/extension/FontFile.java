package app.hushmessenger.extension;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * The picked font's copy in Messenger's own files. Drawing reads the copy, never the picker's URI, which its provider can
 * take back. A file has to start like an sfnt font, be at most {@link #MAX_MEGABYTES} and load in Android before its copy
 * replaces the old one in one rename.
 */
final class FontFile {
    /** A full Chinese, Japanese and Korean font runs to about 20 MB. A Latin one is well under 1 MB. */
    static final int MAX_MEGABYTES = 20;
    static final long MAX_BYTES = MAX_MEGABYTES * 1024L * 1024L;
    static final String NAME = "hushmessenger-font";
    static final String PARTIAL = NAME + ".part";

    static final int TRUETYPE = 0x00010000;
    /** 'true', Apple's TrueType tag. */
    static final int TRUETYPE_APPLE = 0x74727565;
    /** 'OTTO', OpenType with CFF outlines. */
    static final int OPENTYPE_CFF = 0x4F54544F;
    /** 'ttcf', a collection. Android builds its first font. */
    static final int COLLECTION = 0x74746366;

    private static final int FVAR = 0x66766172;
    private static final int WGHT = 0x77676874;
    private static final int MAX_TABLES = 512;
    private static final int MAX_AXES = 64;

    enum Refusal { UNREADABLE, NOT_A_FONT, TOO_LARGE, WONT_LOAD, NOT_SAVED }

    /** A file turned down. The copy it would have replaced is still there. */
    static final class Refused extends Exception {
        final Refusal reason;

        Refused(Refusal reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    /** Whether Android builds a typeface from a file, the last check a copy passes. */
    interface Check {
        boolean loads(File file);
    }

    /**
     * The saved name that goes with the copy. save runs after the checks and before the rename, and undo puts the old name
     * back when the rename then fails. A throw from save counts as a name that didn't save.
     */
    interface Choice {
        boolean save();

        void undo();
    }

    private static final Choice FILE_ONLY = new Choice() {
        @Override public boolean save() { return true; }
        @Override public void undo() { }
    };

    /**
     * A folder of their own for tests. Robolectric shares one files folder across a run, and Windows won't replace a copy
     * an earlier test's typeface still maps. Null on a phone.
     */
    static volatile File testFolder;

    private FontFile() { }

    static File file(Context context) {
        File folder = testFolder;
        return new File(folder != null ? folder : context.getFilesDir(), NAME);
    }

    static boolean isFontTag(int tag) {
        return tag == TRUETYPE || tag == TRUETYPE_APPLE || tag == OPENTYPE_CFF || tag == COLLECTION;
    }

    static void copy(InputStream input, File target, Check check) throws Refused {
        copy(input, target, check, FILE_ONLY);
    }

    /**
     * Copies [input] to [target] through a partial file beside it, and closes [input]. The first four bytes are read before
     * anything is written, so a photo picked by mistake is never copied, and reading stops one chunk past the limit.
     */
    static void copy(InputStream input, File target, Check check, Choice choice) throws Refused {
        if (input == null) throw new Refused(Refusal.UNREADABLE, "No stream to read");
        File partial = new File(target.getParentFile(), PARTIAL);
        boolean kept = false;
        try {
            byte[] head = new byte[4];
            int got = readFully(input, head);
            if (got < head.length || !isFontTag(tagOf(head)))
                throw new Refused(Refusal.NOT_A_FONT, got < head.length ? "Shorter than a font header" : "No font tag");
            write(input, head, partial);
            boolean loads;
            try {
                loads = check.loads(partial);
            } catch (RuntimeException failure) {
                loads = false;
            }
            if (!loads) throw new Refused(Refusal.WONT_LOAD, "Android built no typeface from it");
            boolean saved;
            try {
                saved = choice.save();
            } catch (RuntimeException failure) {
                saved = false;
            }
            if (!saved) throw new Refused(Refusal.NOT_SAVED, "The font file's name couldn't be saved");
            try {
                moveIn(partial, target);
            } catch (Refused refused) {
                try {
                    choice.undo();
                } catch (RuntimeException ignored) {
                    // Picking again mends a name that points at the old copy.
                }
                throw refused;
            }
            kept = true;
        } finally {
            try {
                input.close();
            } catch (IOException | RuntimeException ignored) {
                // Every byte wanted was read, or the copy was already turned down.
            }
            if (!kept) partial.delete();
        }
    }

    /** Replaces [target] whole or leaves it as it was. The old copy is never deleted first. */
    private static void moveIn(File partial, File target) throws Refused {
        try {
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException failure) {
            // The class only: a message can carry a path.
            throw new Refused(Refusal.NOT_SAVED, failure.getClass().getSimpleName());
        }
    }

    private static void write(InputStream input, byte[] head, File partial) throws Refused {
        OutputStream output = null;
        try {
            output = new FileOutputStream(partial);
            output.write(head);
            long total = head.length;
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = read(input, buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) throw new Refused(Refusal.TOO_LARGE, "Larger than " + MAX_BYTES + " bytes");
                output.write(buffer, 0, count);
            }
            output.close();
            output = null;
        } catch (IOException error) {
            throw new Refused(Refusal.NOT_SAVED, error.getClass().getSimpleName());
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                    // The copy is turned down already and its file removed after this.
                }
            }
        }
    }

    private static int read(InputStream input, byte[] buffer) throws Refused {
        try {
            return input.read(buffer);
        } catch (IOException | RuntimeException error) {
            throw new Refused(Refusal.UNREADABLE, error.getClass().getSimpleName());
        }
    }

    private static int readFully(InputStream input, byte[] buffer) throws Refused {
        int filled = 0;
        while (filled < buffer.length) {
            int count;
            try {
                count = input.read(buffer, filled, buffer.length - filled);
            } catch (IOException | RuntimeException error) {
                throw new Refused(Refusal.UNREADABLE, error.getClass().getSimpleName());
            }
            if (count < 0) break;
            filled += count;
        }
        return filled;
    }

    private static int tagOf(byte[] head) {
        return ((head[0] & 0xFF) << 24) | ((head[1] & 0xFF) << 16) | ((head[2] & 0xFF) << 8) | (head[3] & 0xFF);
    }

    /**
     * The least and greatest value of [file]'s 'wght' axis from its fvar table, or null for a font without one or with
     * tables that can't be read. A collection answers for its first font.
     */
    static float[] weightAxis(File file) {
        try (RandomAccessFile font = new RandomAccessFile(file, "r")) {
            long start = 0;
            if (readInt(font, 0) == COLLECTION) {
                if (readInt(font, 8) < 1) return null;
                start = readUnsignedInt(font, 12);
            }
            int tables = readUnsignedShort(font, start + 4);
            if (tables > MAX_TABLES) return null;
            for (int index = 0; index < tables; index++) {
                long record = start + 12 + 16L * index;
                if (readInt(font, record) != FVAR) continue;
                long fvar = readUnsignedInt(font, record + 8);
                int axesAt = readUnsignedShort(font, fvar + 4);
                int axes = readUnsignedShort(font, fvar + 8);
                int axisSize = readUnsignedShort(font, fvar + 10);
                if (axisSize < 20 || axes > MAX_AXES) return null;
                for (int axis = 0; axis < axes; axis++) {
                    long at = fvar + axesAt + (long) axisSize * axis;
                    if (readInt(font, at) != WGHT) continue;
                    // Fixed 16.16: minimum, default, maximum.
                    float least = readInt(font, at + 4) / 65536f;
                    float greatest = readInt(font, at + 12) / 65536f;
                    return least >= 1 && greatest <= 1000 && least <= greatest ? new float[] {least, greatest} : null;
                }
                return null;
            }
            return null;
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private static int readInt(RandomAccessFile font, long at) throws IOException {
        font.seek(at);
        return font.readInt();
    }

    private static long readUnsignedInt(RandomAccessFile font, long at) throws IOException {
        return readInt(font, at) & 0xFFFFFFFFL;
    }

    private static int readUnsignedShort(RandomAccessFile font, long at) throws IOException {
        font.seek(at);
        return font.readUnsignedShort();
    }
}
