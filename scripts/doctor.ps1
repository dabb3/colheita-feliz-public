[CmdletBinding()]
param([switch]$Json)
$ErrorActionPreference = 'Continue'

function Resolve-Tool([string]$Name) {
  $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $command) { return $null }
  return $command.Source
}
function Run-Version([string]$Path, [string[]]$Arguments) {
  if (-not $Path) { return $null }
  try { return ((& $Path @Arguments 2>&1 | Select-Object -First 1).ToString().Trim()) } catch { return "error: $($_.Exception.Message)" }
}

$jdkCandidates = @(
  $env:JAVA_HOME,
  (Join-Path $env:USERPROFILE '.jdks\corretto-21.0.12.1'),
  (Join-Path $env:USERPROFILE '.jdks\openjdk-27'),
  (Join-Path $env:USERPROFILE '.jdks\semeru-1.8.0_482')
) | Where-Object { $_ } | Select-Object -Unique
$jdks = foreach ($candidate in $jdkCandidates) {
  $java = Join-Path $candidate 'bin\java.exe'
  if (Test-Path -LiteralPath $java) { [pscustomobject]@{ home=$candidate; javaVersion=(Run-Version $java @('-version')) } }
}
$javaPath = Resolve-Tool 'java'
$javacPath = Resolve-Tool 'javac'
$gradlePath = Resolve-Tool 'gradle'
$dockerPath = Resolve-Tool 'docker'
$nodePath = Resolve-Tool 'node'
$npmPath = Resolve-Tool 'npm'
$wslPath = Resolve-Tool 'wsl'
$dockerService = Get-Service -Name 'com.docker.service' -ErrorAction SilentlyContinue | Select-Object -First 1
$dockerProcesses = @(Get-Process -Name 'com.docker.backend','Docker Desktop' -ErrorAction SilentlyContinue | Select-Object ProcessName,Id)
$dockerVersion = $null; $dockerServer = $null; $dockerContext = $null
if ($dockerPath) {
  $dockerVersion = Run-Version $dockerPath @('--version')
  try { $dockerContext = (& $dockerPath context show 2>&1 | Select-Object -First 1).ToString().Trim() } catch {}
  try { $dockerServer = (& $dockerPath version --format '{{.Server.Version}}' 2>&1 | Out-String).Trim() } catch { $dockerServer = "unreachable: $($_.Exception.Message)" }
  if (-not $dockerServer) { $dockerServer = 'unreachable: Docker returned no server version.' }
}
$gradleUserHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$gradleLockProbe = Join-Path $gradleUserHome 'codex-doctor-write-probe.tmp'
$gradleHomeWritable = $false
try { [IO.File]::WriteAllText($gradleLockProbe, 'probe'); Remove-Item -LiteralPath $gradleLockProbe -Force; $gradleHomeWritable=$true } catch { $gradleHomeWritable=$false }
$wslStatus = if ($wslPath) { try { ((& $wslPath --list --verbose 2>&1 | Out-String) -replace [char]0, '').Trim() } catch { "unreachable: $($_.Exception.Message)" } } else { 'not installed' }
$childProcessProbe = if ($nodePath) {
  try {
    $probe = & $nodePath -e "const {spawnSync}=require('node:child_process');const r=spawnSync(process.execPath,['-e','process.exit(0)']);process.stdout.write(r.error?.code??'exit:'+r.status)" 2>&1
    ($probe | Out-String).Trim()
  } catch { "blocked: $($_.Exception.Message)" }
} else { 'not run: Node unavailable' }
$port = Get-NetTCPConnection -LocalPort 55439 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
$pathSegments = $env:PATH -split ';' | ForEach-Object { if ($_ -match '(?i)(secret|token|password|credential|auth|key=)') { '[REDACTED]' } else { $_ } }
$report = [ordered]@{
  timestampUtc = [DateTime]::UtcNow.ToString('o')
  os = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
  shell = $PSVersionTable.PSVersion.ToString()
  workingDirectory = (Get-Location).Path
  tools = [ordered]@{
    java = @{ path=$javaPath; version=(Run-Version $javaPath @('-version')) }
    javac = @{ path=$javacPath; version=(Run-Version $javacPath @('-version')) }
    gradle = @{ path=$gradlePath; version=(Run-Version $gradlePath @('--version')) }
    node = @{ path=$nodePath; version=(Run-Version $nodePath @('--version')) }
    npm = @{ path=$npmPath; version=(Run-Version $npmPath @('--version')) }
    docker = @{ path=$dockerPath; clientVersion=$dockerVersion; serverVersion=$dockerServer; context=$dockerContext }
    dockerService = if ($dockerService) { @{name=$dockerService.Name;status=$dockerService.Status.ToString()} } else { $null }
    dockerProcesses = @($dockerProcesses)
    wsl = @{ path=$wslPath; status=$wslStatus }
  }
  javaHome = $env:JAVA_HOME
  javaCandidates = @($jdks)
  gradleUserHome = $gradleUserHome
  gradleUserHomeWritable = $gradleHomeWritable
  childProcessProbe = $childProcessProbe
  testPort55439Listening = [bool]$port
  path = @($pathSegments)
}
if ($Json) { $report | ConvertTo-Json -Depth 6 } else { $report | Format-List; $report.tools | Format-List; $report.javaCandidates | Format-Table -AutoSize }
