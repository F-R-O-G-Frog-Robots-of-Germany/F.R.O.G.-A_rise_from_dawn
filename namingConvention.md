# Naming Convention

| Category | Convention | Example |
| --- | --- | --- |
| Functions | snake_case | `update_pose()` |
| Variables and objects | camelCase | `drivePower`, `shooterMotor` |
| Unit and pose values, including parameters | UPPER_SNAKE_CASE | `START_POSE`, `DELAY` |
| Constants and enum members | UPPER_SNAKE_CASE | `MAX_SPEED`, `BLUE` |
| Classes, interfaces, and enum types | PascalCase | `TaskManager`, `Alliance` |
| Java filenames | Match the class name | `TaskManager.java` |
| Packages | lowercase | `opmodes.teleop` |
| Team-owned hardware configuration names | snake_case | `left_motor` |

Exceptions: keep inherited SDK/API method names, SDK identifiers, Android resource names, and required tooling filenames. Mutable shared objects remain camelCase even when their references are `static final`.

Hardware configuration names must match the Robot Controller configuration.
