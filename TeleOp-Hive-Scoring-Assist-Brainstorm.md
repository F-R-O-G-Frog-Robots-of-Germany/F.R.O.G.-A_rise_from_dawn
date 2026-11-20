# TeleOp Hive scoring assist

Reduce the time from arriving near the Hive to scoring POLLEN by letting the robot finish alignment and time the feed after the upward-facing Cell is confirmed.

## Start with one confirmation per visit

Give each Cell a small scoring zone on its approach side. The driver heads for the Cell they can see is up. Once the robot arrives with a roughly correct heading, holding `A` could confirm that the Cell in front of the robot is visibly up and settled. The robot finishes the turn and starts the feed as soon as it is aligned and ready. Releasing `A` or moving either driving stick cancels the attempt. Make this a single attempt per hold, so a cancelled or finished attempt needs a fresh confirmation.

That is the smallest camera-free prototype. It depends on the driver correctly identifying the near Cell. If we want the software to catch a wrong-side arrival, have the operator explicitly select **audience-side Cell up** or **far-side Cell up**. Then the robot can compare the selection with its scoring zone. If the two disagree, withhold the feed, show the expected approach on the Driver Station or rumble, and let the driver reposition. Make the selection valid for the current approach only. Any operator who sees a tip can release or cancel immediately.

In BIOBUZZ, the two Cells of a Hive pivot together. The rules specify the upward-facing Cell for causing a tip and advise pausing when the Hive is tipping. The starting position is defined in setup, but an alliance partner can change it during Auto or TeleOp. Our own launches give insufficient evidence to count tips because misses, partner scores, and retained pieces all affect the result. [FIRST BIOBUZZ staging, §10.3.1](https://ftc-resources.firstinspires.org/ftc/game/manual#page=83) · [Hive scoring, §10.5.1](https://ftc-resources.firstinspires.org/ftc/game/manual-10#page=7) · [Rule G417](https://ftc-resources.firstinspires.org/ftc/game/manual-11#page=13)

## What the robot can already do

- Pedro uses the Pinpoint localizer with odometry pods. The pods and gyro supply an estimate of field X/Y and heading from a known start. The IMU measures the robot’s rotation and cannot tell which Hive Cell is up. Check the Auto-to-TeleOp pose handoff and use the known-pose reset at its matching physical placement. Allow for odometry drift near zone boundaries. [Pedro configuration](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java#L41) · [TeleOp pose initialization](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java#L220)
- `Main` already implements a tap-`A` heading correction using a hold at the current X/Y. It also calls `prepare_shot()`. `Shooter.nearest_hive()` selects the side from field Y. Use that geometry to seed the approach zones, and treat the upward Cell as a separate input. Make the confirmed Cell drive both the heading and the distance used for shooter settings throughout the attempt. [Main aim helper](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java#L164) · [Shooter field-side selection](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java#L258)
- The shooter already prepares when it detects a full load or scoring is requested. Check whether it reaches usable RPM during the approach. Reuse the RPM and hood checks to start feeding. The existing shot request can wait and then start when RPM becomes ready, so an assist must cancel its pending request and stop its active feed when permission is lost. [Shooter readiness and update](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java#L360)

## Where the assist takes over

Start with two mirrored approaches per alliance, one per Cell. Give each a preferred stopping pose and a small surrounding zone with room for the robot to rotate. A distance band alone can include the wrong approach or a position too close to the frame. Use measured linear speed and yaw rate for a short settling period because neutral sticks can still leave the chassis coasting. Use the existing in-place aiming behavior first. Add only a small position correction inside the scoring zone if practice shows the driver regularly stops a little outside the reliable shot location.

```text
confirmed Cell matches this scoring zone and the pose is usable
    + assist is armed, the sticks are quiet, and the robot has settled
    + heading is within the tested correction range
        → hold position, finish turning, and prepare the shooter

still inside the zone, stable and aligned
    + upward Cell confirmation is current and shooter is ready
    + a load is known to be present
        → start one scoring attempt

cancel, Cell changes or starts tipping, alignment is lost, or attempt expires
        → stop the assist feed and release drive control
```

Check the permission after TeleOp refreshes the pose and before it updates the mechanisms. Start with a bounded feed and use positive piece detection if the robot can confirm individual pieces. The intake has placeholder distance thresholds to calibrate. Its full-load signal can help qualify a full-load attempt, and a partial-load attempt needs a signal that at least one piece is present. If the other Cell comes up, guide the driver to its approach. An automatic trip around the Hive would add traffic decisions and needs a separate justification. [TeleOp loop order](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java#L108) · [Intake sensing](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java#L136)

## Getting to hands-free arrivals

With an onboard observation of the upward Cell, a scoring mode can remain armed across visits. Each new arrival in the matching zone can trigger its own alignment and feed sequence. Use fresh observations and treat an obscured or moving Cell as unknown until it settles.

- **Simple range sensor.** From a repeatable approach, look at a part of the Cell whose measured range clearly differs between the two stable positions. Try this only if the CAD or a mock-up shows a usable line of sight and a clear margin across the whole scoring zone. An ambiguous return or obstruction should withhold feed.
- **FTC-compatible USB webcam.** A standard webcam with the FTC SDK’s VisionPortal can report an AprilTag’s ID and orientation. The ID identifies the Cell. The tag’s orientation, interpreted using the camera mount, indicates whether the Cell is up. Keep Pinpoint for driving. This option depends on seeing a usable tag from both approaches. [FTC VisionPortal overview](https://ftc-docs.firstinspires.org/en/latest/apriltag/vision_portal/visionportal_overview/visionportal-overview.html) · [Rule R708](https://ftc-resources.firstinspires.org/ftc/game/manual-12#page=25)

## Prototype in the simulator first

Compare manual trigger, one-confirmation assist, and arrival-triggered assist with a simulated Cell observation. Give the controller only the inputs the real version would have. Script wrong-side arrivals, partner tips during spin-up, neutral sticks while coasting, pose drift, and stick cancellation while the shooter is waiting for RPM. The existing TeleOp aim checks can exercise those controller transitions with fake follower data. [TeleOpHiveAimChecks](tests/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/TeleOpHiveAimChecks.java#L25)

Track time from arrival to first scored POLLEN, scoring success per visit, and wrong-side feed attempts. Timestamp arrival, alignment, and RPM readiness separately. If alignment is usually last, try a return route that naturally leaves the robot facing the Cell. If RPM is usually last, overlap more of spin-up with the drive back. Compare either change with time spent adding a position correction. Calibrate the scoring zones and motion thresholds when hardware and practice access are available.

## Changes you would like made

- Preferred driver or operator control for confirming the upward Cell:
- Which approach to prototype first:
- Sensors available besides Pinpoint:

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
