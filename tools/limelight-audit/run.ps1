# Run from any directory with Java 17 and the project's dependencies already cached.
param(
    [string]$SdkRoot = (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'),
    [string]$GradleCache = (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1')
)
$ErrorActionPreference = 'Stop'
$taskRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$taskBuild = Join-Path $taskRepo 'build\limelight-audit'
$taskReal = Join-Path $taskBuild 'sdk-classes'
$taskFake = Join-Path $taskBuild 'fixture-classes'
New-Item -ItemType Directory -Force -Path $taskReal, $taskFake | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskClasspath = @((Join-Path $SdkRoot 'platforms\android-35\android.jar'))
if (!(Test-Path -LiteralPath $taskClasspath[0])) { throw 'Android SDK platform 35 is required.' }
foreach ($taskArtifact in @('RobotCore', 'Hardware', 'FtcCommon')) {
    $taskCached = Join-Path $GradleCache "org.firstinspires.ftc\$taskArtifact\12.0.0"
    $taskAar = Get-ChildItem -LiteralPath $taskCached -Filter '*.aar' -Recurse -File | Select-Object -First 1
    if (!$taskAar) { throw "Missing cached FTC 12.0.0 $taskArtifact AAR." }
    $taskJar = Join-Path $taskBuild "$taskArtifact.jar"
    $taskZip = [IO.Compression.ZipFile]::OpenRead($taskAar.FullName)
    try { [IO.Compression.ZipFileExtensions]::ExtractToFile($taskZip.GetEntry('classes.jar'), $taskJar, $true) }
    finally { $taskZip.Dispose() }
    $taskClasspath += $taskJar
}
$taskPedro = Get-ChildItem -LiteralPath (Join-Path $GradleCache 'com.pedropathing\core\3.0.1') -Filter 'core-3.0.1.jar' -File -Recurse | Select-Object -First 1
if (!$taskPedro) { throw 'Missing cached Pedro core 3.0.1 JAR.' }
$taskClasspath += $taskPedro.FullName
$taskCp = $taskClasspath -join ';'
$taskPackage = Join-Path $taskRepo 'TeamCode\src\main\java\org\firstinspires\ftc\teamcode\limelight'
$taskSources = @(Get-ChildItem -LiteralPath $taskPackage -Filter '*.java' -File)
$taskTests = @(Get-ChildItem -LiteralPath (Join-Path $taskRepo 'TeamCode\src\test\java\org\firstinspires\ftc\teamcode\limelight') -Filter '*.java' -File)
# Compile production code against real SDK signatures first. Fixtures never establish SDK compatibility.
& javac -source 8 -target 8 -proc:none -classpath $taskCp -d $taskReal @($taskSources.FullName) @($taskTests.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Real SDK compilation failed.' }
foreach ($taskTest in @('ButineRegression', 'PlannerAuditChecks', 'VisionChecks', 'VisionAuditChecks', 'RunnerChecks', 'RunnerAuditChecks')) {
    & java -cp ($taskReal + ';' + $taskCp) ('org.firstinspires.ftc.teamcode.limelight.' + $taskTest)
    if ($LASTEXITCODE -ne 0) { throw "$taskTest failed." }
}
# Separate output/classpath deliberately replaces only camera transport/logging for frame injection.
$taskFixtures = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'fixtures') -Filter '*.java' -Recurse -File)
& javac -source 8 -target 8 -proc:none -classpath $taskCp -d $taskFake @($taskSources.FullName) @($taskFixtures.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Camera fixture compilation failed.' }
foreach ($taskTest in @('CameraFrameAudit', 'CameraRunnerAudit')) {
    & java -cp ($taskFake + ';' + $taskCp) $taskTest
    if ($LASTEXITCODE -ne 0) { throw "$taskTest failed." }
}
Write-Output 'Limelight audit passed. This script does not replace the full Gradle build or robot testing.'
