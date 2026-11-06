param([switch]$GreenCalibration)
$ErrorActionPreference = 'Stop'
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '../..'))
try {
    if (-not $env:JAVA_HOME) { throw 'Defina JAVA_HOME com o JDK 21 existente.' }
    $preserved = @('assets/data/animals.json','assets/data/crops.json','assets/data/progression.json','docs/game-rule.md')
    $before = @{}
    foreach ($file in $preserved) { $before[$file] = (Get-FileHash $file -Algorithm SHA256).Hash }
    & ./gradlew.bat :core:jar
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o core.' }
    $gsonJar = Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.14.0" -Recurse -Filter gson-2.14.0.jar | Select-Object -First 1
    if (-not $gsonJar) { throw 'Gson 2.14.0 não encontrado no cache usado pelo projeto.' }
    $classpath = "core/build/libs/core.jar;$($gsonJar.FullName)"
    New-Item -ItemType Directory -Force build/animal-v2-classes | Out-Null
    & "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp $classpath -d build/animal-v2-classes analysis/animals-v2/StudyEconomy.java analysis/animals-v2/PortfolioSimulation.java analysis/animals-v2/AnimalBalanceStudy.java analysis/animals-v2/GreenCalibration.java
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o estudo isolado.' }
    $entryPoint = if ($GreenCalibration) { 'GreenCalibration' } else { 'AnimalBalanceStudy' }
    & "$env:JAVA_HOME/bin/java.exe" -ea -Xmx512m -cp "build/animal-v2-classes;$classpath" $entryPoint
    if ($LASTEXITCODE -ne 0) { throw 'O estudo falhou numa verificação.' }
    foreach ($file in $preserved) {
        if ($before[$file] -ne (Get-FileHash $file -Algorithm SHA256).Hash) { throw "Fonte econômica alterada durante o estudo: $file" }
    }
} finally { Pop-Location }
