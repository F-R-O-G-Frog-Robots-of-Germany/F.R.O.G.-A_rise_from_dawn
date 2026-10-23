# Standalone desktop regression checks. Uses cached real Android/FTC/Pedro/Panels signatures.
param(
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$GradleCache = (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'),
    [switch]$CompileOnly
)
$ErrorActionPreference = 'Stop'
$taskRepo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$taskBuild = Join-Path $taskRepo ('build\competition-audit\' + [Guid]::NewGuid().ToString('N'))
$taskClasses = Join-Path $taskBuild 'sdk-classes'
$taskLibs = Join-Path $taskBuild 'libs'
New-Item -ItemType Directory -Force -Path $taskClasses, $taskLibs | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskClasspath = [Collections.Generic.List[string]]::new()
$taskAndroid = Join-Path $SdkRoot 'platforms\android-35\android.jar'
if (!(Test-Path -LiteralPath $taskAndroid)) { throw 'Android SDK platform 35 is required.' }
$taskClasspath.Add($taskAndroid)

function Add-CachedArtifact([string]$taskModule, [string]$taskVersion) {
    $taskModulePath = Join-Path $GradleCache $taskModule
    $taskVersionPath = Join-Path $taskModulePath $taskVersion
    $taskArtifacts = @(Get-ChildItem -LiteralPath $taskVersionPath -Recurse -File |
        Where-Object { $_.Extension -in '.aar', '.jar' -and $_.Name -notmatch '-(sources|javadoc)\.jar$' })
    if ($taskArtifacts.Count -eq 0) { throw "Missing cached dependency $taskModule $taskVersion." }
    foreach ($taskArtifact in $taskArtifacts) {
        if ($taskArtifact.Extension -eq '.aar') {
            $taskJar = Join-Path $taskLibs ($taskArtifact.BaseName + '.jar')
            $taskZip = [IO.Compression.ZipFile]::OpenRead($taskArtifact.FullName)
            try {
                $taskEntry = $taskZip.GetEntry('classes.jar')
                if (!$taskEntry) { throw "No classes.jar in $($taskArtifact.FullName)." }
                [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry, $taskJar, $true)
            } finally { $taskZip.Dispose() }
            $taskClasspath.Add($taskJar)
        } else { $taskClasspath.Add($taskArtifact.FullName) }
    }
}

foreach ($taskModule in Get-ChildItem -LiteralPath (Join-Path $GradleCache 'org.firstinspires.ftc') -Directory) {
    if (Test-Path -LiteralPath (Join-Path $taskModule.FullName '12.0.0')) {
        Add-CachedArtifact ('org.firstinspires.ftc\' + $taskModule.Name) '12.0.0'
    }
}
Add-CachedArtifact 'com.pedropathing\core' '3.0.1'
Add-CachedArtifact 'com.pedropathing\revhub' '3.0.1'
Add-CachedArtifact 'com.pedropathing\tuning' '1.0.1'
Add-CachedArtifact 'com.bylazar\panels' '1.0.5'
Add-CachedArtifact 'com.bylazar\telemetry' '1.0.5'
Add-CachedArtifact 'androidx.annotation\annotation-jvm' '1.6.0'
Add-CachedArtifact 'org.jetbrains\annotations' '23.0.0'
# Pedro tuning 1.0.1 declares 2.3.21; Panels/telemetry declare the lower 2.1.20.
Add-CachedArtifact 'org.jetbrains.kotlin\kotlin-stdlib' '2.3.21'

$taskCp = $taskClasspath -join ';'
$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRepo 'TeamCode\src\main\java') -Filter '*.java' -Recurse -File)
$taskTests = @(Get-ChildItem -LiteralPath (Join-Path $taskRepo 'tests\java') -Filter '*.java' -Recurse -File)
$taskArgs = @('-source', '8', '-target', '8', '-proc:none', '-encoding', 'UTF-8', '-classpath',
    ('"' + $taskCp.Replace('\', '/') + '"'), '-d', ('"' + $taskClasses.Replace('\', '/') + '"'))
$taskArgs += @($taskSources.FullName) + @($taskTests.FullName) | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }
$taskArgsFile = Join-Path $taskBuild 'javac-arguments.txt'
[IO.File]::WriteAllLines($taskArgsFile, $taskArgs)
[IO.File]::WriteAllText((Join-Path $taskBuild 'classpath.txt'), $taskClasses + ';' + $taskCp)
& javac ('@' + $taskArgsFile)
if ($LASTEXITCODE -ne 0) { throw 'Full production/test compilation against the real SDK failed.' }
Write-Output "Compiled $($taskSources.Count) production and $($taskTests.Count) test sources against real SDK signatures."
if ($CompileOnly) { return }

# Run every standalone check; new checks are included without maintaining a separate name list.
$taskExecuted = 0
foreach ($taskTest in $taskTests | Sort-Object FullName) {
    $taskText = [IO.File]::ReadAllText($taskTest.FullName)
    if ($taskText -notmatch 'public\s+static\s+void\s+main\s*\(') { continue }
    if ($taskText -notmatch '(?m)^package\s+([\w.]+)\s*;') { throw "Missing package in $($taskTest.FullName)." }
    $taskClassName = $Matches[1] + '.' + $taskTest.BaseName
    & java -cp ($taskClasses + ';' + $taskCp) $taskClassName
    if ($LASTEXITCODE -ne 0) { throw "$taskClassName failed." }
    $taskExecuted++
}
# Compile transport doubles separately, after the entire production tree passed real SDK compilation.
# The integration fixture exercises the real autonomous queue, workers, planner/runner and Pedro.
$taskFixtureClasses = Join-Path $taskBuild 'fixture-classes'
New-Item -ItemType Directory -Force -Path $taskFixtureClasses | Out-Null
$taskTransportSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRepo 'tools\limelight-audit\fixtures\com') -Filter '*.java' -Recurse -File)
$taskIntegrationSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'fixtures') -Filter '*.java' -Recurse -File)
& javac -source 8 -target 8 -proc:none -encoding UTF-8 -classpath ($taskClasses + ';' + $taskCp) -d $taskFixtureClasses @($taskTransportSources.FullName) @($taskIntegrationSources.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Competition integration fixture compilation failed.' }
& java -cp ($taskFixtureClasses + ';' + $taskClasses + ';' + $taskCp) CompetitionCollectionAudit
if ($LASTEXITCODE -ne 0) { throw 'CompetitionCollectionAudit failed.' }
Write-Output "Competition audit passed: $taskExecuted standalone check programs and the complete collection integration fixture. Further camera transport checks remain in tools/limelight-audit/run.ps1. Robot performance is not measured."
