[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$docker = Get-Command docker -ErrorAction Stop
$name = "colheita-test-$([Guid]::NewGuid().ToString('N'))"
$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:PATH
$oldGradleHome = $env:GRADLE_USER_HOME
$oldDatabaseUrl = $env:TEST_DATABASE_URL
$oldDatabaseUser = $env:TEST_DATABASE_USER
$started = $false
try {
  $jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Join-Path $env:USERPROFILE '.jdks\corretto-21.0.12.1' }
  if (-not (Test-Path (Join-Path $jdk 'bin\java.exe'))) { throw 'JDK 21 was not found. Set JAVA_HOME for this PowerShell process and rerun.' }
  $env:JAVA_HOME = $jdk
  $env:PATH = "$jdk\bin;$env:PATH"
  $env:GRADLE_USER_HOME = Join-Path $env:TEMP "gradle-colheita-test-$([Guid]::NewGuid().ToString('N'))"
  & $docker.Source run --detach --rm --name $name --publish '127.0.0.1::5432' --tmpfs '/var/lib/postgresql/data:rw,noexec,nosuid,size=512m' --env POSTGRES_DB=colheita_test --env POSTGRES_USER=postgres --env POSTGRES_HOST_AUTH_METHOD=trust postgres:17-alpine@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b | Out-Null
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
  & .\gradlew.bat --no-daemon test build :server:integrationTest
  if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed (exit $LASTEXITCODE)." }
  Write-Output "Local verification passed against disposable PostgreSQL at 127.0.0.1:$port. Container: $name"
} finally {
  if ($started) {
    & $docker.Source rm --force $name *> $null
    if ($LASTEXITCODE -ne 0) { Write-Warning "Could not remove the owned container $name." }
  }
  $env:JAVA_HOME = $oldJavaHome
  $env:PATH = $oldPath
  $env:GRADLE_USER_HOME = $oldGradleHome
  $env:TEST_DATABASE_URL = $oldDatabaseUrl
  $env:TEST_DATABASE_USER = $oldDatabaseUser
}
