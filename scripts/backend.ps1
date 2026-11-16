[CmdletBinding()]
param(
    [ValidateSet('run', 'verify', 'package')]
    [string]$Task = 'run'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousJavaHome = $env:JAVA_HOME

try {
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
        $javaCommand = Get-Command java -ErrorAction Stop
        $javaSettings = & $javaCommand.Source -XshowSettings:properties -version 2>&1
        $javaHomeMatch = $javaSettings | Select-String '^\s*java.home\s*=\s*(.+)$' | Select-Object -First 1
        if (-not $javaHomeMatch) { throw 'Nao foi possivel localizar o JDK. Configure JAVA_HOME para um JDK 21 ou superior.' }
        $env:JAVA_HOME = $javaHomeMatch.Matches[0].Groups[1].Value.Trim()
        if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
            throw 'O Java encontrado nao possui compilador. Instale um JDK 21 ou superior.'
        }
    }

    Push-Location $projectRoot
    try {
        $mavenArguments = @('-f', (Join-Path $projectRoot 'backend/pom.xml'), '-B', '-ntp')
        if ($Task -eq 'run') {
            $mavenArguments += @('spring-boot:run', '-Dspring-boot.run.profiles=local')
        } else {
            $mavenArguments += $Task
        }
        & (Join-Path $projectRoot 'backend/mvnw.cmd') @mavenArguments
        if ($LASTEXITCODE -ne 0) { throw "Maven terminou com codigo $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
