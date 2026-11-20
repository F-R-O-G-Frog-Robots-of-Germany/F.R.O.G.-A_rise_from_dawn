package org.firstinspires.ftc.teamcode.core.pedro;

import com.pedropathing.revhub.localizers.PinpointConfig;
import com.pedropathing.revhub.localizers.PinpointLocalizer;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.I2cDeviceSynchSimple;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Exercises the pinned SDK's real Pinpoint decoder with a simulated I²C device. */
public final class PinpointBulkReadChecks {
    private static final byte[] STANDARD_WINDOW = {3, 5, 6, 7, 8, 9, 10, 11, 12, 13};
    private static final byte[] COMPACT_WINDOW = {3, 8, 9, 10, 11, 12, 13};
    private static int checks;

    public static void main(String[] args) {
        for (int version : new int[] {1, 2}) {
            Device legacy = new Device(version);
            check(!PinpointBulkReads.configure_window(legacy.driver, true), "legacy uses standard window");
            check(legacy.scopeWrites == 0 && legacy.bulkReads == 0, "legacy receives no new scope command");
        }
        Device unknown = new Device(0);
        expect_failure(() -> PinpointBulkReads.configure_window(unknown.driver, true), "unknown firmware");
        check(unknown.scopeWrites == 0, "unknown firmware receives no scope command");
        check_compact_runtime();
        check_default_recovery(false);
        check_default_recovery(true);
        Device broken = new Device(3);
        broken.invalidReads = 4;
        expect_failure(() -> PinpointBulkReads.configure_window(broken.driver, true), "unverified fallback");
        check(broken.scopeWrites == 2 && broken.bulkReads == 4, "only two attempts per scope");
        System.out.println("Passed " + checks + " Pinpoint bulk-read checks");
    }

    private static void check_compact_runtime() {
        Device device = new Device(3);
        check(PinpointBulkReads.configure_window(device.driver, true), "supported firmware uses compact window");
        check(Arrays.equals(device.window, COMPACT_WINDOW), "status and all six Pedro registers present");
        check(device.lastLength == 29, "compact CRC frame has 28 bytes of data");

        HardwareMap map = new HardwareMap(null, null) {
            @Override public <T> T get(Class<? extends T> type, String name) {
                return type.cast(device.driver);
            }
        };
        PinpointConfig config = new PinpointConfig(c -> {
            c.name.set("pinpoint");
            c.xPodOffset.set(0.0);
            c.yPodOffset.set(0.0);
            c.xPodDirection.set(GoBildaPinpointDriver.EncoderDirection.FORWARD);
            c.yPodDirection.set(GoBildaPinpointDriver.EncoderDirection.FORWARD);
            c.globalDistanceUnit.set(DistanceUnit.MM);
            c.resetMode.set(PinpointLocalizer.ResetMode.NONE);
        });
        PinpointLocalizer localizer = new PinpointBulkReads.CompactLocalizer(map, config, device.driver);
        int before = device.bulkReads;
        device.x = 100;
        localizer.update();
        check(localizer.pose().x() == 100 && localizer.pose().y() == 3, "pose offsets decode correctly");
        check(localizer.velocity().vx == 5 && localizer.velocity().vy == 6, "velocities decode correctly");
        check(localizer.pose().heading() == 0.5 && localizer.velocity().omega == 0.25, "heading values decode correctly");
        check(device.bulkReads == before + 1 && device.singleReads == 1, "one I²C read after setup");

        device.x = 125;
        device.invalidReads = 1;
        localizer.update();
        check(localizer.pose().x() == 125 && device.bulkReads == before + 3, "retry must return a fresh sample");
        device.x = 12000;
        expect_failure(localizer::update, "implausible CRC-valid pose");
        device.window = STANDARD_WINDOW;
        expect_failure(localizer::update, "device restart leaves wire window in default layout");
        check(device.bulkReads == before + 6, "two bad compact reads stop the update");
    }

    private static void check_default_recovery(boolean throwCompactWrite) {
        Device device = new Device(3);
        device.rejectCompact = true;
        device.throwCompactWrite = throwCompactWrite;
        check(!PinpointBulkReads.configure_window(device.driver, true), "falls back on scope failure");
        check(Arrays.equals(device.window, STANDARD_WINDOW), "restores standard device window");
        check(device.scopeWrites == 2 && device.lastLength == 41, "confirms restored window through CRC");
        device.driver.update();
        check(device.lastLength == 40, "standard mode restores SDK local validation");

        Device tuning = new Device(3);
        tuning.window = COMPACT_WINDOW;
        check(!PinpointBulkReads.configure_window(tuning.driver, false), "tuning selects standard window");
        check(Arrays.equals(tuning.window, STANDARD_WINDOW) && tuning.scopeWrites == 1,
                "tuning restores default from previous compact mode");
    }

    private static void expect_failure(Runnable action, String scenario) {
        try {
            action.run();
            throw new AssertionError("Expected failure for " + scenario);
        } catch (IllegalStateException expected) {
            checks++;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Device {
        private final GoBildaPinpointDriver driver;
        private byte[] window = STANDARD_WINDOW;
        private int scopeWrites, bulkReads, singleReads, lastLength, invalidReads;
        private boolean rejectCompact, throwCompactWrite;
        private float x = 2;

        Device(int version) {
            I2cDeviceSynchSimple transport = (I2cDeviceSynchSimple) Proxy.newProxyInstance(
                    I2cDeviceSynchSimple.class.getClassLoader(), new Class<?>[] {I2cDeviceSynchSimple.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "read":
                                int register = (int) args[0];
                                int length = (int) args[1];
                                if (register == 18) return bulk_frame(length);
                                singleReads++;
                                if (register == 2) {
                                    return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
                                            .putInt(version).array();
                                }
                                throw new AssertionError("Unexpected extra single register read " + register);
                            case "write":
                                if ((int) args[0] != 25) return null;
                                byte[] scope = (byte[]) args[1];
                                scopeWrites++;
                                if (scope.length == COMPACT_WINDOW.length && rejectCompact) {
                                    if (throwCompactWrite) throw new IllegalStateException("I²C write failed");
                                    return null;
                                }
                                window = scope.clone();
                                return null;
                            default:
                                return null;
                        }
                    });
            driver = new GoBildaPinpointDriver(transport, false);
        }

        private byte[] bulk_frame(int length) {
            bulkReads++;
            lastLength = length;
            if (invalidReads > 0) {
                invalidReads--;
                return new byte[length];
            }

            ByteBuffer packet = ByteBuffer.allocate(window.length * 4 + 1).order(ByteOrder.LITTLE_ENDIAN);
            for (byte register : window) {
                switch (register) {
                    case 3: packet.putInt(1); break;
                    case 5: packet.putInt(720); break;
                    case 6: packet.putInt(300); break;
                    case 7: packet.putInt(500); break;
                    case 8: packet.putFloat(x); break;
                    case 9: packet.putFloat(3); break;
                    case 10: packet.putFloat(0.5f); break;
                    case 11: packet.putFloat(5); break;
                    case 12: packet.putFloat(6); break;
                    case 13: packet.putFloat(0.25f); break;
                    default: throw new AssertionError("Unexpected Pinpoint register " + register);
                }
            }
            int checksum = 0x90;
            for (int i = 0; i < packet.position(); i++) {
                checksum ^= packet.get(i) & 0xff;
                for (int bit = 0; bit < 8; bit++) {
                    checksum = ((checksum << 1) ^ ((checksum & 0x80) != 0 ? 0x31 : 0)) & 0xff;
                }
            }
            packet.put((byte) checksum);
            return Arrays.copyOf(packet.array(), length);
        }
    }
}
