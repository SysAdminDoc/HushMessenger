package app.hushmessenger.extension;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class SystemCameraTest {
    static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, (byte) 0xFF, (byte) 0xD9};
    Application app;
    CameraProvider provider;

    @Before public void reset() {
        app = RuntimeEnvironment.getApplication();
        Settings.initialize(app);
        Settings.preferences.edit().clear().commit();
        Settings.activeAt.clear();
        CrashGuard.resetForTests();
        provider = Robolectric.setupContentProvider(CameraProvider.class, app.getPackageName() + CameraProvider.AUTHORITY_SUFFIX);
        CameraActivity.handedBack = null;
        File[] left = CameraProvider.directory(app).listFiles();
        if (left != null) for (File file : left) assertTrue(file.delete());
    }

    /** A photo the camera app wrote into the capture folder. */
    private File captured(String name) throws Exception {
        File folder = CameraProvider.directory(app);
        assertTrue(folder.isDirectory() || folder.mkdirs());
        File photo = new File(folder, name);
        try (FileOutputStream out = new FileOutputStream(photo)) {
            out.write(PHOTO);
        }
        return photo;
    }

    @Test public void onlyItsOwnSwitchSendsTheCameraButtonToThePhonesCamera() {
        Intent stock = new Intent().setClassName(app.getPackageName(), "com.facebook.messaging.montage.composer.MontageComposerActivity");
        assertSame(stock, Settings.systemCamera(stock));
        assertEquals(7377, Settings.cameraRequestCode(stock, 7377));
        assertEquals(0, Settings.lastActive("system_camera"));
        Settings.preferences.edit().putBoolean("system_camera", true).commit();
        Intent capture = Settings.systemCamera(stock);
        assertEquals(new ComponentName(app.getPackageName(), CameraActivity.class.getName()), capture.getComponent());
        assertEquals("Messenger reads the photo back the way it reads one picked in another app", 1112, Settings.cameraRequestCode(capture, 7377));
        assertEquals(7377, Settings.cameraRequestCode(stock, 7377));
        assertTrue(Settings.lastActive("system_camera") > 0);
        assertNull(Settings.systemCamera(null));
        Settings.preferences.edit().putBoolean("paused", true).commit();
        assertSame("Pause brings Messenger's camera back", stock, Settings.systemCamera(stock));
    }

    @Test public void theLauncherSwapsOnlyTheChatCamerasMarkedLaunch() {
        Settings.hookErrors.clear();
        Settings.preferences.edit().putBoolean("system_camera", true).commit();
        // A story reply starts the same screen with the same request code through the same launcher, without the mark.
        Intent storyReply = new Intent().setClassName(app.getPackageName(), "com.facebook.messaging.montage.composer.MontageComposerActivity");
        assertSame(storyReply, Settings.chatCamera(storyReply));
        assertEquals(7376, Settings.cameraRequestCode(storyReply, 7376));
        assertEquals(0, Settings.lastActive("system_camera"));
        assertNull(Settings.chatCamera(null));

        android.os.Bundle extras = new android.os.Bundle();
        extras.putString("trigger2", "thread");
        Settings.markChatCamera(extras);
        Settings.markChatCamera(null);
        Intent chat = new Intent().setClassName(app.getPackageName(), "com.facebook.messaging.montage.composer.MontageComposerActivity")
            .putExtras(extras);
        Intent capture = Settings.chatCamera(chat);
        assertEquals(new ComponentName(app.getPackageName(), CameraActivity.class.getName()), capture.getComponent());
        assertEquals(1112, Settings.cameraRequestCode(capture, 7376));
        assertTrue(Settings.lastActive("system_camera") > 0);
        assertFalse("The mark comes off the launch", chat.hasExtra(Settings.CHAT_CAMERA_MARK));

        Settings.preferences.edit().putBoolean("system_camera", false).commit();
        Intent again = new Intent(chat).putExtras(extras);
        assertSame("Off gets Messenger's camera, with its extras as they were", again, Settings.chatCamera(again));
        assertFalse(again.hasExtra(Settings.CHAT_CAMERA_MARK));
        assertEquals("thread", again.getStringExtra("trigger2"));
        assertEquals(7376, Settings.cameraRequestCode(again, 7376));
        assertTrue(Settings.hookErrors.isEmpty());
    }

    @Test public void onlyTheCaptureScreensOwnPhotoPassesTheChatsSourceCheck() throws Exception {
        Settings.hookErrors.clear();
        File file = captured("IMG_1791657600000.jpg");
        Uri photo = CameraProvider.uriFor(app, file);
        assertNotNull(CameraProvider.fileFor(app, photo));
        assertFalse("Nothing was handed back yet", Settings.trustCapturedPhoto(photo));
        CameraActivity.handedBack = file.getName();
        assertTrue(Settings.trustCapturedPhoto(photo));
        // Anything else gets Messenger's own answer: another capture in the same folder, other providers, other files
        // in ours, and file URIs.
        assertFalse(Settings.trustCapturedPhoto(CameraProvider.uriFor(app, captured("IMG_1791657600001.jpg"))));
        assertFalse(Settings.trustCapturedPhoto(null));
        assertFalse(Settings.trustCapturedPhoto(Uri.parse("content://media/external/images/media/12")));
        assertFalse(Settings.trustCapturedPhoto(Uri.parse("content://" + app.getPackageName() + ".provider/IMG_1791657600000.jpg")));
        assertFalse(Settings.trustCapturedPhoto(photo.buildUpon().appendPath("x").build()));
        assertFalse(Settings.trustCapturedPhoto(Uri.parse("file:///data/data/" + app.getPackageName() + "/IMG_1791657600000.jpg")));
        // Once the file is gone, its name alone doesn't pass.
        assertTrue(file.delete());
        assertFalse(Settings.trustCapturedPhoto(photo));
        assertTrue(Settings.hookErrors.isEmpty());
    }

    @Test public void onlyTheCaptureScreensOwnPhotoPassesTheOpensOwnFileCheck() throws Exception {
        Settings.hookErrors.clear();
        File file = captured("IMG_1791657600000.jpg");
        Uri photo = CameraProvider.uriFor(app, file);
        assertTrue("Not handed back, so Messenger still refuses its own file", Settings.internalFile(true, photo));
        CameraActivity.handedBack = file.getName();
        assertFalse(Settings.internalFile(true, photo));
        // Every other file Messenger owns is still refused, and a file it doesn't own stays allowed.
        assertTrue(Settings.internalFile(true, Uri.parse("content://" + app.getPackageName() + ".provider/IMG_1791657600000.jpg")));
        assertTrue(Settings.internalFile(true, null));
        assertFalse(Settings.internalFile(false, photo));
        assertFalse(Settings.internalFile(false, Uri.parse("content://media/external/images/media/12")));
        assertTrue(Settings.hookErrors.isEmpty());
    }

    @Test public void thePhotoTheCameraAppSavesComesBackForMessengerToRead() throws Exception {
        try (ActivityController<CameraActivity> controller = Robolectric.buildActivity(CameraActivity.class).setup()) {
            ShadowActivity screen = shadowOf(controller.get());
            Intent camera = screen.getNextStartedActivityForResult().intent;
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, camera.getAction());
            Uri output = camera.getParcelableExtra(MediaStore.EXTRA_OUTPUT);
            assertEquals(app.getPackageName() + CameraProvider.AUTHORITY_SUFFIX, output.getAuthority());
            assertEquals(output, camera.getClipData().getItemAt(0).getUri());
            assertNotEquals(0, camera.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            // The camera app writes through the one URI it was given.
            try (ParcelFileDescriptor descriptor = provider.openFile(output, "w");
                 FileOutputStream out = new FileOutputStream(descriptor.getFileDescriptor())) {
                out.write(PHOTO);
            }
            screen.receiveResult(camera, Activity.RESULT_OK, null);
            assertTrue(controller.get().isFinishing());
            assertEquals(Activity.RESULT_OK, screen.getResultCode());
            Intent result = screen.getResultIntent();
            assertEquals(output, result.getData());
            assertEquals("image/jpeg", result.getType());
            assertEquals("image/jpeg", provider.getType(result.getData()));
            assertEquals(output.getLastPathSegment(), CameraActivity.handedBack);
            assertTrue("The chat's copy checks let this photo through", Settings.trustCapturedPhoto(result.getData()));
            assertFalse(Settings.internalFile(true, result.getData()));
            byte[] read = new byte[PHOTO.length + 1];
            try (ParcelFileDescriptor descriptor = provider.openFile(result.getData(), "r");
                 FileInputStream in = new FileInputStream(descriptor.getFileDescriptor())) {
                assertEquals(PHOTO.length, in.read(read));
            }
            assertArrayEquals(PHOTO, Arrays.copyOf(read, PHOTO.length));
        }
        // Nothing but a capture name in the provider's own folder opens.
        String authority = "content://" + app.getPackageName() + CameraProvider.AUTHORITY_SUFFIX;
        assertNull(CameraProvider.fileFor(app, Uri.parse(authority + "/..%2Fshared_prefs%2Fhushmessenger.xml")));
        assertNull(CameraProvider.fileFor(app, Uri.parse(authority + "/IMG_1.jpg/extra")));
        assertNull(CameraProvider.fileFor(app, Uri.parse("content://other.app" + CameraProvider.AUTHORITY_SUFFIX + "/IMG_1.jpg")));
    }

    @Test public void aCancelledPhotoLeavesTheChatAsItWasAndItsFileGoes() {
        try (ActivityController<CameraActivity> controller = Robolectric.buildActivity(CameraActivity.class).setup()) {
            ShadowActivity screen = shadowOf(controller.get());
            Intent camera = screen.getNextStartedActivityForResult().intent;
            File folder = CameraProvider.directory(app);
            assertEquals(1, folder.listFiles().length);
            screen.receiveResult(camera, Activity.RESULT_CANCELED, null);
            assertTrue(controller.get().isFinishing());
            assertEquals(Activity.RESULT_CANCELED, screen.getResultCode());
            assertEquals(0, folder.listFiles().length);
            assertNull("A cancelled photo is never handed back", CameraActivity.handedBack);
        }
    }
}
