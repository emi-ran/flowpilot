package com.flowpilot.app.actions;

import android.content.Context;
import android.content.ContextWrapper;
import android.net.TetheringManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Shell-side helper that turns the Wi-Fi hotspot on or off.
 *
 * <p>Third-party apps cannot call {@link TetheringManager#startTethering} for Wi-Fi: the tethering
 * service checks that the caller is the active tethering owner, and {@code cmd wifi} is rejected
 * with a {@link SecurityException}. This class is therefore launched with {@code app_process} by
 * Shizuku, i.e. as the shell UID (2000), and presents itself as {@code com.android.shell} so the
 * tethering service accepts the request. That exact package name is required by the service's UID
 * check, so it must never be changed to the app's package.</p>
 *
 * <p>Usage: {@code app_process -Djava.class.path=<base.apk> /system/bin com.flowpilot.app.actions.HotspotBridge on|off}.
 * Exit code 0 means the hotspot is in the requested state, 1 means it could not be changed.</p>
 */
@Keep // Launched by class name through app_process; R8 must keep the name, class and main().
public final class HotspotBridge {

    private static final String SHELL_PACKAGE = "com.android.shell";
    private static final String START_CLASS = "com.flowpilot.app.actions.HotspotBridge";
    private static final long TIMEOUT_SECONDS = 10L;

    /** {@code TetheringManager.TETHER_ERROR_DUPLICATE_REQUEST}: the hotspot is already on. */
    private static final int ERROR_DUPLICATE_REQUEST = 18;
    /** {@code TetheringManager.TETHER_ERROR_UNKNOWN_REQUEST}: tethering is already off. */
    private static final int ERROR_UNKNOWN_REQUEST = 17;

    private HotspotBridge() {
    }

    public static void main(String[] args) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            System.out.println("unsupported: public TetheringManager requires Android 16 or newer");
            System.exit(1);
            return;
        }
        if (args == null || args.length != 1 || (!"on".equals(args[0]) && !"off".equals(args[0]))) {
            System.out.println("usage: " + START_CLASS + " on|off");
            System.exit(1);
            return;
        }

        final boolean enable = "on".equals(args[0]);
        boolean applied;
        try {
            // Kept in a nested class so devices below API 36 fail the guard above without
            // ever resolving TetheringManager, which does not exist there.
            applied = TetheringRunner.apply(enable);
        } catch (Throwable t) {
            System.out.println((enable ? "start" : "stop") + " failed: " + describe(t));
            applied = false;
        }
        System.out.println("done");
        System.exit(applied ? 0 : 1);
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        String type = throwable.getClass().getSimpleName();
        return message == null || message.isEmpty() ? type : type + ": " + message;
    }

    @RequiresApi(36)
    private static final class TetheringRunner {

        private TetheringRunner() {
        }

        static boolean apply(boolean enable) throws Exception {
            android.os.Looper.prepareMainLooper();

            final Context systemContext = systemContext();
            // The tethering service authorises Wi-Fi tethering by caller UID, and the shell UID is
            // only accepted for its own package name — the app's package is rejected.
            final ContextWrapper shellContext = new ContextWrapper(systemContext) {
                @Override
                public String getPackageName() {
                    return SHELL_PACKAGE;
                }

                @Override
                public String getOpPackageName() {
                    return SHELL_PACKAGE;
                }
            };

            final TetheringManager manager = tetheringManager(shellContext);
            final TetheringManager.TetheringRequest request =
                    new TetheringManager.TetheringRequest.Builder(TetheringManager.TETHERING_WIFI).build();
            final Executor executor = new Executor() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }
            };

            return enable
                    ? startTethering(manager, request, executor)
                    : stopTethering(manager, request, executor);
        }

        private static Context systemContext() throws Exception {
            final Class<?> activityThread = Class.forName("android.app.ActivityThread");
            final Method systemMain = activityThread.getDeclaredMethod("systemMain");
            systemMain.setAccessible(true);
            final Object thread = systemMain.invoke(null);

            final Method getSystemContext = activityThread.getDeclaredMethod("getSystemContext");
            getSystemContext.setAccessible(true);
            final Context context = (Context) getSystemContext.invoke(thread);
            if (context == null) {
                throw new IllegalStateException("system context unavailable");
            }
            return context;
        }

        private static TetheringManager tetheringManager(Context context) throws Exception {
            final Supplier<IBinder> supplier = new Supplier<IBinder>() {
                @Override
                public IBinder get() {
                    try {
                        return tetheringBinder();
                    } catch (Exception e) {
                        throw new IllegalStateException("tethering service unavailable", e);
                    }
                }
            };
            final Constructor<TetheringManager> constructor =
                    TetheringManager.class.getConstructor(Context.class, Supplier.class);
            return constructor.newInstance(context, supplier);
        }

        private static IBinder tetheringBinder() throws Exception {
            final Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            final Method getService = serviceManager.getMethod("getService", String.class);
            final IBinder binder = (IBinder) getService.invoke(null, "tethering");
            if (binder == null) {
                throw new IllegalStateException("tethering service not registered");
            }
            return binder;
        }

        private static boolean startTethering(
                TetheringManager manager,
                TetheringManager.TetheringRequest request,
                Executor executor
        ) throws InterruptedException {
            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] started = new boolean[1];
            manager.startTethering(request, executor, new TetheringManager.StartTetheringCallback() {
                @Override
                public void onTetheringStarted() {
                    System.out.println("started");
                    started[0] = true;
                    latch.countDown();
                }

                @Override
                public void onTetheringFailed(int error) {
                    // A duplicate request means the hotspot is already on, which is the desired state.
                    if (error == ERROR_DUPLICATE_REQUEST) {
                        System.out.println("started (already on)");
                        started[0] = true;
                    } else {
                        System.out.println("start failed=" + error);
                        started[0] = false;
                    }
                    latch.countDown();
                }
            });

            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.out.println("start timed out");
                return false;
            }
            if (started[0]) {
                // Let the tethering service finish applying the request before the process exits.
                Thread.sleep(1500L);
            }
            return started[0];
        }

        private static boolean stopTethering(
                TetheringManager manager,
                TetheringManager.TetheringRequest request,
                Executor executor
        ) throws InterruptedException {
            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] stopped = new boolean[1];
            manager.stopTethering(request, executor, new TetheringManager.StopTetheringCallback() {
                @Override
                public void onStopTetheringSucceeded() {
                    System.out.println("stopped");
                    stopped[0] = true;
                    latch.countDown();
                }

                @Override
                public void onStopTetheringFailed(int error) {
                    // An unknown request means tethering is already off, which is the desired state.
                    if (error == ERROR_UNKNOWN_REQUEST) {
                        System.out.println("stopped (already off)");
                        stopped[0] = true;
                    } else {
                        System.out.println("stop failed=" + error);
                        stopped[0] = false;
                    }
                    latch.countDown();
                }
            });

            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.out.println("stop timed out");
                return false;
            }
            if (stopped[0]) {
                // Let the tethering service finish tearing down before the process exits.
                Thread.sleep(1500L);
            }
            return stopped[0];
        }
    }
}
