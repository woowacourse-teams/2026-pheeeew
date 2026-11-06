import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

/** Shell-only persistent input injector; accepted injection is not app acceptance. */
public final class PressTapInjector {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("count intervalMs x y");
        int count = Integer.parseInt(args[0]);
        long interval = Long.parseLong(args[1]);
        float x = Float.parseFloat(args[2]), y = Float.parseFloat(args[3]);
        if (count < 0 || count > 550 || interval < 20 || !Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("invalid bounded input schedule");
        }
        Class<?> managerClass = Class.forName("android.hardware.input.InputManager");
        Object manager = managerClass.getMethod("getInstance").invoke(null);
        Method inject = managerClass.getMethod("injectInputEvent", InputEvent.class, int.class);
        long start = SystemClock.uptimeMillis() + 200;
        StringBuilder records = new StringBuilder();
        for (int i = 0; i < count; i++) {
            long due = start + i * interval;
            long remaining;
            while ((remaining = due - SystemClock.uptimeMillis()) > 0) SystemClock.sleep(remaining);
            long downAt = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, x, y, 0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            boolean downOk;
            try { downOk = (Boolean) inject.invoke(manager, down, 0); } finally { down.recycle(); }
            SystemClock.sleep(10);
            long upAt = SystemClock.uptimeMillis();
            MotionEvent up = MotionEvent.obtain(downAt, upAt, MotionEvent.ACTION_UP, x, y, 0);
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            boolean upOk;
            try { upOk = (Boolean) inject.invoke(manager, up, 0); } finally { up.recycle(); }
            records.append("{\"index\":").append(i + 1).append(",\"dueUptimeMs\":").append(due)
                .append(",\"downUptimeMs\":").append(downAt).append(",\"upUptimeMs\":").append(upAt)
                .append(",\"downInjected\":").append(downOk).append(",\"upInjected\":").append(upOk).append("}\n");
            if (!downOk || !upOk) { System.out.print(records); throw new IllegalStateException("injection rejected"); }
        }
        System.out.print(records);
        System.out.println("{\"completed\":" + count + "}");
    }
}
