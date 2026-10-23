# How i prompt and how to work with me 
There are usually 4 kinds of prompts i may give you. Decide first which one to use.
The contents of my prompts of my prompts always overwrite/overrule the contents of this file.
If there are spelling mistakes in my prompts, try to find my original message.
If it's not understandable, stop immediately and ask for clarification.

## 1.Trivial Prompts
This includes: file or function lookups, short explanations and renames or easy (re)writes.
Keep it short and precise here. 
file lookups: find the file quickly and give me a short, precise explanation if you have anything to add
function lookups: Find the function quickly and give me required context. If I already explained how the function works, only give context you think is important. 
short explanations: Explain with an average size of an answer.
Dont explain syntax here: Standard FTC Implementation like opModes, subsystems and autonomous routines. 
Explain complicated syntax here: TaskManager, LogManager, anything outside Teamcode, sometimes in Units in case its really complicated syntax
renames: rename exactly like I said, make sure to handle all usages of the renamed something properly. If there is no name stated, use the naming convention in this file
easy (re)writes: This includes small changes or additions which are in the range of 1-5 lines. If this change touches hardware, were a small mistake could break the robot(includes especially Turret, Servo Gate(both not implemented yet), consider it a large change. For small changes, focus on speed and don't write any comments. Further verification isn't needed.
## 2.Planning and Brainstorm(if you are in plan mode always use this)
Always write me a md file for your planning results. Write longer instead of potentially removing information.
Here you should reconstruct intent and not my description.
If intent is unclear, ask questions or assume the most logical or most likely default, but always give me your choice out immediately after deciding on it in the terminal.
Plan out every detail.Write a short goal in one sentence below the title.
Write code snippets for complicated parts into the md file. 
Add a subsection which enables me to write changes i would like to be made.
Below the whole md file write, if you had access to this repo: "Written by --name of your AIModel--, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn"

## 3.longer (Re)writes
First, find information whether you have a plan and who wrote it. You can see it on the end of the given md file.
#### scenario 1: based on a given plan, written by an AI model with access to this repo
Follow this plan, with close to no questioning. 
### scenario 2: written by an external ai 
Follow the general concepts of this plan, but question the code snippets or the approach
### scenario 3: no plan
write a short plan, give it me in the terminal and let me approve it before touching code

### 4.Writing longer explanations/ writing documentation files
write extensive. Explain all syntax besides the really easy stuff. Explain what a variable name means if its an abbreviation. 

## Naming convention 
| Category | Convention | Example |
|---------|------------|---------|
| Functions | snake_case | `update_pose()` |
| Variables | camelCase | `drivePower` |
| Objects | camelCase | `poseEstimator` |
| Classes | PascalCase | `PoseEstimator` |
| Interfaces | PascalCase | `Localizer` |
| Enums (type) | PascalCase | `DriveMode` |
| Enum members | ALL_CAPS | `TANK`, `ARCADE` |
| Constants | ALL_CAPS | `MAX_SPEED` |
| Packages | lowercase | `org.firstinspires.ftc.teamcode.util` |
| OpModes | PascalCase | `AutoBlueLeft` |
| XML config | snake_case | `left_motor` |


## Robot safety and reliability
Every robot part has to be encapsulated properly to make sure nothing breaks. Encapsulation means covering every scenario where a part would break, for example having a closed servo gate and a turned on transfer ant the same time.
* General rules
Always explain what you found online to me. Use those sites as reliable sources: 
limelight: https://docs.limelightvision.io/
pedro pathing: https://pedropathing.com/docs/pathing
general ftc:https://ftc-docs.firstinspires.org/en/latest/index.html
## Important Information
The robot isnt built yet thus anything which includes physical tuning or test isnt possible, you may mention it, but dont assume its possible think about another way or tell me that your approach isnt possible without the robot
