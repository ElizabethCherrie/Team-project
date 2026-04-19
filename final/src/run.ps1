$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$outRoot = Join-Path $projectRoot "out"
$libJar = Join-Path $projectRoot "lib\sqlite-jdbc-3.49.1.0.jar"
$dbPath = Join-Path $projectRoot "data\ipos-sa.db"
$port = if ($args.Length -gt 0) { $args[0] } else { "8080" }

# Demo defaults are built into the application:
#   CA stock sync -> http://localhost:8088/stock/ipos
#   CA markup     -> 2
#   PU payment    -> http://localhost:8090/pay
#   PU mail       -> http://localhost:8090/mail
#
# Optional overrides:
#   $env:IPOS_CA_STOCK_SYNC_URL = "http://localhost:8088/stock/ipos"
#   $env:IPOS_CA_MARKUP_RATE    = "2"
#   $env:IPOS_PU_PAYMENT_URL    = "http://localhost:8090/pay"
#   $env:IPOS_PU_MAIL_URL       = "http://localhost:8090/mail"
java --enable-native-access=ALL-UNNAMED -cp "$outRoot;$libJar" ipossa.Main $port $dbPath
