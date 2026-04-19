$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$srcRoot = Join-Path $projectRoot "ipossa"
$outRoot = Join-Path $projectRoot "out"
$libJar = Join-Path $projectRoot "lib\sqlite-jdbc-3.49.1.0.jar"

if (-not (Test-Path $libJar)) {
    throw "Missing SQLite JDBC jar at $libJar"
}

New-Item -ItemType Directory -Force -Path $outRoot | Out-Null

$sources = Get-ChildItem -Path $srcRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -cp $libJar -d $outRoot $sources
