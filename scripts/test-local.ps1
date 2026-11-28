[CmdletBinding()]
param([switch]$RerunTasks)
$ErrorActionPreference = 'Stop'
$docker = Get-Command docker -ErrorAction Stop
$name = "colheita-test-$([Guid]::NewGuid().ToString('N'))"
$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:PATH
$oldDatabaseUrl = $env:TEST_DATABASE_URL
$oldDatabaseUser = $env:TEST_DATABASE_USER
$started = $false
$verificationPassed = $false
try {
  $jdkCandidates = @($env:JAVA_HOME)
  $userJdks = Join-Path $env:USERPROFILE '.jdks'
  if (Test-Path -LiteralPath $userJdks) { $jdkCandidates += Get-ChildItem -LiteralPath $userJdks -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName }
  foreach ($programFiles in @($env:ProgramFiles, ${env:ProgramFiles(x86)}) | Where-Object { $_ }) {
    foreach ($vendor in @('Java', 'Amazon Corretto', 'Eclipse Adoptium', 'Microsoft')) {
      $vendorPath = Join-Path $programFiles $vendor
      if (Test-Path -LiteralPath $vendorPath) { $jdkCandidates += Get-ChildItem -LiteralPath $vendorPath -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName }
    }
  }
  $jdk = $null
  foreach ($candidate in ($jdkCandidates | Where-Object { $_ } | Select-Object -Unique)) {
    $java = Join-Path $candidate 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe'))) { continue }
    $version = (& $java -version 2>&1 | Out-String)
    if ($version -match 'version "21([.+_-]|"|$)|build 21([.+_-]|$)') { $jdk = $candidate; break }
  }
  if (-not $jdk) { throw 'JDK 21 was not found. Set JAVA_HOME for this PowerShell process and rerun.' }
  $env:JAVA_HOME = $jdk
  $env:PATH = "$jdk\bin;$env:PATH"
  & $docker.Source run --detach --rm --name $name --publish '127.0.0.1::5432' --tmpfs '/var/lib/postgresql/data:rw,noexec,nosuid,size=1g' --env POSTGRES_DB=colheita_test --env POSTGRES_USER=postgres --env POSTGRES_HOST_AUTH_METHOD=trust postgres:17-alpine@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Could not start isolated PostgreSQL container (docker exit $LASTEXITCODE)." }
  $started = $true
  $mapping = (& $docker.Source port $name '5432/tcp' 2>&1 | Select-Object -First 1).ToString().Trim()
  if ($LASTEXITCODE -ne 0 -or $mapping -notmatch '127\.0\.0\.1:(\d+)') { throw "Could not resolve the generated loopback port: $mapping" }
  $port = [int]$Matches[1]
  $ready = $false
  for ($i=0; $i -lt 45; $i++) {
    & $docker.Source exec $name pg_isready -U postgres -d colheita_test *> $null
    if ($LASTEXITCODE -eq 0) { $ready=$true; break }
    Start-Sleep -Seconds 1
  }
  if (-not $ready) { throw 'Disposable PostgreSQL did not become ready within 45 seconds.' }
  $env:TEST_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$port/colheita_test"
  $env:TEST_DATABASE_USER = 'postgres'
  npm test --prefix web
  if ($LASTEXITCODE -ne 0) { throw "Web unit tests failed (exit $LASTEXITCODE)." }
  npm run typecheck --prefix web
  if ($LASTEXITCODE -ne 0) { throw "Web typecheck failed (exit $LASTEXITCODE)." }
  npm run build:local --prefix web
  if ($LASTEXITCODE -ne 0) { throw "Local web build failed (exit $LASTEXITCODE)." }
  $gradleArgs = @('--no-daemon')
  if ($RerunTasks) { $gradleArgs += '--rerun-tasks' }
  $gradleArgs += @('test', 'build', ':server:integrationTest')
  & .\gradlew.bat @gradleArgs
  if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed (exit $LASTEXITCODE)." }
  $verificationPassed = $true
  Write-Output "Local verification passed against disposable PostgreSQL at 127.0.0.1:$port. Container: $name"
} finally {
  if ($started) {
    if (-not $verificationPassed) {
      $postgresLog = Join-Path $env:TEMP "$name-postgres.log"
      try {
        & $docker.Source logs $name 2>&1 | Set-Content -LiteralPath $postgresLog
        Write-Warning "PostgreSQL logs saved to $postgresLog"
      } catch {
        Write-Warning "Could not save PostgreSQL logs for ${name}: $_"
      }
    }
    & $docker.Source rm --force $name *> $null
    if ($LASTEXITCODE -ne 0) { Write-Warning "Could not remove the owned container $name." }
  }
  $env:JAVA_HOME = $oldJavaHome
  $env:PATH = $oldPath
  $env:TEST_DATABASE_URL = $oldDatabaseUrl
  $env:TEST_DATABASE_USER = $oldDatabaseUser
}
