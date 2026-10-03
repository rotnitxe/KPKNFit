package com.example.kpkn.testing;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only URI source that can suspend or fail MIME resolution deterministically. */
public final class WorkoutMediaTestContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.example.kpkn.test.workoutmedia";
    public static final String METHOD_CONFIGURE = "configure";
    public static final String METHOD_RELEASE = "release";
    public static final String METHOD_STATS = "stats";
    public static final String METHOD_RESET = "reset";
    public static final String KEY_MIME = "mime";
    public static final String KEY_BYTES = "bytes";
    public static final String KEY_BLOCK_MIME = "blockMime";
    public static final String KEY_FAILURES = "failures";
    public static final String KEY_ENTERED = "entered";
    public static final String KEY_MIME_CALLS = "mimeCalls";

    private volatile Plan currentPlan;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        Plan current = requireCurrentPlan();
        current.typeCalls.incrementAndGet();
        current.typeEntered.countDown();
        try {
            if (!current.typeReleased.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release MIME lookup");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to release MIME lookup", interrupted);
        }

        int remaining;
        int next;
        do {
            remaining = current.failures.get();
            next = Math.max(remaining - 1, 0);
        } while (!current.failures.compareAndSet(remaining, next));
        if (remaining > 0) {
            throw new IllegalStateException("Synthetic MIME lookup failure");
        }
        return current.mime;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Plan current = requireCurrentPlan();
        if (getContext() == null) {
            throw new FileNotFoundException("Provider context is unavailable");
        }
        String pathSegment = uri.getLastPathSegment();
        int pathHash = pathSegment == null ? 0 : pathSegment.hashCode();
        File file = new File(getContext().getCacheDir(), "media-" + pathHash + ".bin");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(current.bytes);
        } catch (IOException error) {
            FileNotFoundException wrapped = new FileNotFoundException("Could not write synthetic media source");
            wrapped.initCause(error);
            throw wrapped;
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** Test controls cross the test-target process boundary through ContentResolver.call. */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_CONFIGURE.equals(method)) {
            if (extras == null) {
                throw new IllegalArgumentException("Provider configuration extras are required");
            }
            String mime = extras.getString(KEY_MIME);
            byte[] bytes = extras.getByteArray(KEY_BYTES);
            if (mime == null || bytes == null) {
                throw new IllegalArgumentException("MIME and bytes are required");
            }
            currentPlan = new Plan(
                    mime,
                    bytes,
                    new AtomicInteger(extras.getInt(KEY_FAILURES, 0)),
                    new CountDownLatch(1),
                    new CountDownLatch(extras.getBoolean(KEY_BLOCK_MIME, false) ? 1 : 0));
            return Bundle.EMPTY;
        }
        if (METHOD_RELEASE.equals(method)) {
            Plan current = currentPlan;
            if (current != null) current.typeReleased.countDown();
            return Bundle.EMPTY;
        }
        if (METHOD_STATS.equals(method)) {
            Plan current = currentPlan;
            Bundle result = new Bundle();
            result.putBoolean(KEY_ENTERED, current != null && current.typeEntered.getCount() == 0L);
            result.putInt(KEY_MIME_CALLS, current == null ? 0 : current.typeCalls.get());
            return result;
        }
        if (METHOD_RESET.equals(method)) {
            Plan current = currentPlan;
            if (current != null) current.typeReleased.countDown();
            currentPlan = null;
            return Bundle.EMPTY;
        }
        Bundle result = super.call(method, arg, extras);
        return result == null ? Bundle.EMPTY : result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private Plan requireCurrentPlan() {
        Plan current = currentPlan;
        if (current == null) throw new IllegalStateException("Provider test was not configured");
        return current;
    }

    private static final class Plan {
        final String mime;
        final byte[] bytes;
        final AtomicInteger failures;
        final CountDownLatch typeEntered;
        final CountDownLatch typeReleased;
        final AtomicInteger typeCalls = new AtomicInteger();

        Plan(String mime, byte[] bytes, AtomicInteger failures, CountDownLatch typeEntered,
             CountDownLatch typeReleased) {
            this.mime = mime;
            this.bytes = bytes;
            this.failures = failures;
            this.typeEntered = typeEntered;
            this.typeReleased = typeReleased;
        }
    }
}