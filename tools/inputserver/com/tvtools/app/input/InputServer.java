package com.tvtools.app.input;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * Persistent TV-side key injector.
 *
 * <p>Started once with {@code app_process} and fed one Android keycode per line on
 * stdin. It uses the same hidden InputManager API as {@code adb shell input}, but
 * avoids starting an ART process for every key press. Runs as the shell user, which
 * already has INJECT_EVENTS; nothing is installed.</p>
 */
public final class InputServer {

    public static void main(String[] args) throws Exception {
        Object inputManager;
        Method inject;
        try {
            Class<?> managerClass = Class.forName("android.hardware.input.InputManager");
            inputManager = managerClass.getMethod("getInstance").invoke(null);
            inject = managerClass.getMethod(
                    "injectInputEvent", InputEvent.class, int.class);
        } catch (Throwable t) {
            System.err.println("TVT_ERR " + t);
            System.exit(2);
            return;
        }

        if (inputManager == null) {
            System.err.println("TVT_ERR no InputManager");
            System.exit(3);
            return;
        }

        System.out.println("TVT_READY");
        System.out.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if ("QUIT".equals(line)) break;

            for (String part : line.split("\\s+")) {
                int keyCode;
                try {
                    keyCode = Integer.parseInt(part);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                try {
                    long now = SystemClock.uptimeMillis();
                    KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
                    down.setSource(InputDevice.SOURCE_KEYBOARD);
                    KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
                    up.setSource(InputDevice.SOURCE_KEYBOARD);
                    // 0 = INJECT_INPUT_EVENT_MODE_ASYNC; ordering is preserved by InputDispatcher.
                    inject.invoke(inputManager, down, 0);
                    inject.invoke(inputManager, up, 0);
                } catch (Throwable t) {
                    System.err.println("TVT_FAIL " + keyCode + " " + t);
                    System.err.flush();
                }
            }
        }
    }
}
