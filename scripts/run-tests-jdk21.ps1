param(
    [string]$JdkHome
)

$candidateHomes = @()

if ($JdkHome) {
    $candidateHomes += $JdkHome
}

$candidateHomes += @(
    "C:\Program Files\Java\jdk-21",
    "C:\Program Files\Java\jdk-21.0.3",
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot",
    "C:\Program Files\Eclipse Adoptium\jdk-21-hotspot"
)

$resolvedHome = $candidateHomes |
    Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) } |
    Select-Object -First 1

if (-not $resolvedHome) {
    throw "Java 21 was not found. Pass -JdkHome <path to JDK 21> or install JDK 21."
}

$env:JAVA_HOME = $resolvedHome
$env:Path = "$resolvedHome\bin;$env:Path"

Write-Host "Using JAVA_HOME=$resolvedHome"
java -version
mvn test
