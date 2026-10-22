package com.droidoperator.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DroidAccessibilityService extends AccessibilityService {

    private static final String TAG = "DroidOperatorService";
    public static DroidAccessibilityService INSTANCE = null;
    private LocalApiServer apiServer = null;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
        Log.i(TAG, "DroidOperator Accessibility Service Connected!");

        // Start Localhost API server (127.0.0.1:8765)
        try {
            apiServer = new LocalApiServer(8765, this);
            apiServer.start();
            Log.i(TAG, "LocalApiServer started on port 8765");
        } catch (IOException e) {
            Log.e(TAG, "Failed to start LocalApiServer", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Events can be logged or streamed if needed
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "DroidOperator Accessibility Service Interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (apiServer != null) {
            apiServer.stop();
            apiServer = null;
        }
        INSTANCE = null;
        Log.i(TAG, "DroidOperator Accessibility Service Destroyed");
    }

    /**
     * Dumps the current active window hierarchy into a clean, compact JSON array.
     */
    public String dumpUiJson() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return "[]";
        }

        JSONArray array = new JSONArray();
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        traverseTree(root, nodes);

        int index = 0;
        for (AccessibilityNodeInfo node : nodes) {
            try {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);

                // Ignore off-screen or zero-size elements
                if (bounds.width() <= 0 || bounds.height() <= 0) {
                    continue;
                }

                CharSequence text = node.getText();
                CharSequence desc = node.getContentDescription();
                CharSequence viewId = node.getViewIdResourceName();
                CharSequence className = node.getClassName();

                // Only keep elements that have text, description, or are interactive
                boolean isClickable = node.isClickable();
                boolean isEditable = node.isEditable();
                boolean isCheckable = node.isCheckable();
                boolean hasContent = (text != null && text.length() > 0) || (desc != null && desc.length() > 0);

                if (!isClickable && !isEditable && !isCheckable && !hasContent) {
                    continue;
                }

                JSONObject obj = new JSONObject();
                obj.put("index", index++);
                obj.put("class", className != null ? className.toString() : "");
                obj.put("text", text != null ? text.toString() : "");
                obj.put("desc", desc != null ? desc.toString() : "");
                obj.put("id", viewId != null ? viewId.toString() : "");
                obj.put("clickable", isClickable);
                obj.put("editable", isEditable);
                obj.put("checked", node.isChecked());

                JSONArray boundsArray = new JSONArray();
                boundsArray.put(bounds.left);
                boundsArray.put(bounds.top);
                boundsArray.put(bounds.right);
                boundsArray.put(bounds.bottom);
                obj.put("bounds", boundsArray);

                // Center coordinates for direct clicking
                obj.put("centerX", bounds.centerX());
                obj.put("centerY", bounds.centerY());

                array.put(obj);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing node", e);
            }
        }

        return array.toString();
    }

    private void traverseTree(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list) {
        if (node == null) return;
        list.add(node);
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseTree(child, list);
            }
        }
    }

    /**
     * Performs a tap gesture at physical screen coordinates (x, y).
     */
    public boolean click(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        return dispatchGesture(builder.build(), null, null);
    }

    /**
     * Performs a smooth drag / swipe gesture from (x1, y1) to (x2, y2).
     */
    public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(durationMs, 100));

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        return dispatchGesture(builder.build(), null, null);
    }

    /**
     * Injects text into the currently focused editable input field.
     */
    public boolean typeText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            // Find first editable node if no explicit focus
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            traverseTree(root, nodes);
            for (AccessibilityNodeInfo n : nodes) {
                if (n.isEditable()) {
                    focused = n;
                    break;
                }
            }
        }

        if (focused != null && focused.isEditable()) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        }

        return false;
    }

    /**
     * Triggers global system keys (back, home, recents, notifications).
     */
    public boolean performGlobal(String action) {
        if ("back".equalsIgnoreCase(action)) {
            return performGlobalAction(GLOBAL_ACTION_BACK);
        } else if ("home".equalsIgnoreCase(action)) {
            return performGlobalAction(GLOBAL_ACTION_HOME);
        } else if ("recents".equalsIgnoreCase(action)) {
            return performGlobalAction(GLOBAL_ACTION_RECENTS);
        } else if ("notifications".equalsIgnoreCase(action)) {
            return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        }
        return false;
    }
}
