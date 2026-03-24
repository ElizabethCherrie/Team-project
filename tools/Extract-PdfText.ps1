param(
    [Parameter(Mandatory = $true)]
    [string]$PdfPath,

    [Parameter(Mandatory = $true)]
    [string]$OutPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Expand-Escapes {
    param([string]$Value)

    $result = [System.Text.StringBuilder]::new()
    for ($i = 0; $i -lt $Value.Length; $i++) {
        $ch = $Value[$i]
        if ($ch -ne '\') {
            [void]$result.Append($ch)
            continue
        }

        if ($i + 1 -ge $Value.Length) {
            break
        }

        $i++
        $next = $Value[$i]
        switch ($next) {
            'n' { [void]$result.Append("`n") }
            'r' { [void]$result.Append("`r") }
            't' { [void]$result.Append("`t") }
            'b' { [void]$result.Append([char]8) }
            'f' { [void]$result.Append([char]12) }
            '(' { [void]$result.Append('(') }
            ')' { [void]$result.Append(')') }
            '\' { [void]$result.Append('\') }
            default {
                if ($next -match '[0-7]') {
                    $octal = [string]$next
                    for ($j = 0; $j -lt 2 -and $i + 1 -lt $Value.Length; $j++) {
                        if ($Value[$i + 1] -match '[0-7]') {
                            $i++
                            $octal += $Value[$i]
                        } else {
                            break
                        }
                    }
                    [void]$result.Append([char][Convert]::ToInt32($octal, 8))
                } else {
                    [void]$result.Append($next)
                }
            }
        }
    }

    $result.ToString()
}

function Get-BalancedLiteralStrings {
    param([string]$Content)

    $values = New-Object System.Collections.Generic.List[string]
    $current = [System.Text.StringBuilder]::new()
    $depth = 0
    $escaped = $false

    for ($i = 0; $i -lt $Content.Length; $i++) {
        $ch = $Content[$i]

        if ($depth -eq 0) {
            if ($ch -eq '(') {
                $depth = 1
                $current.Clear() | Out-Null
            }
            continue
        }

        if ($escaped) {
            [void]$current.Append('\')
            [void]$current.Append($ch)
            $escaped = $false
            continue
        }

        if ($ch -eq '\') {
            $escaped = $true
            continue
        }

        if ($ch -eq '(') {
            $depth++
            [void]$current.Append($ch)
            continue
        }

        if ($ch -eq ')') {
            $depth--
            if ($depth -eq 0) {
                $values.Add((Expand-Escapes $current.ToString()))
                continue
            }
        }

        [void]$current.Append($ch)
    }

    $values
}

[byte[]]$bytes = [System.IO.File]::ReadAllBytes($PdfPath)
$raw = [System.Text.Encoding]::ASCII.GetString($bytes)
$streamPattern = [regex]'stream\s*(\r\n|\n|\r)(?<data>.*?)(\r\n|\n|\r)endstream'
$textChunks = New-Object System.Collections.Generic.List[string]

foreach ($match in $streamPattern.Matches($raw)) {
    $offset = $match.Groups['data'].Index
    $length = $match.Groups['data'].Length
    if ($offset + $length -gt $bytes.Length) {
        continue
    }

    $streamBytes = $bytes[$offset..($offset + $length - 1)]
    try {
        $memory = New-Object System.IO.MemoryStream
        $memory.Write($streamBytes, 0, $streamBytes.Length)
        $memory.Position = 0

        $deflate = New-Object System.IO.Compression.DeflateStream($memory, [System.IO.Compression.CompressionMode]::Decompress)
        $reader = New-Object System.IO.StreamReader($deflate, [System.Text.Encoding]::GetEncoding('ISO-8859-1'))
        $decoded = $reader.ReadToEnd()
        $reader.Dispose()
        $deflate.Dispose()
        $memory.Dispose()
    } catch {
        continue
    }

    if ($decoded -notmatch 'BT|Tj|TJ') {
        continue
    }

    foreach ($literal in Get-BalancedLiteralStrings $decoded) {
        $clean = ($literal -replace '[^\u0009\u000A\u000D\u0020-\u007E]', ' ').Trim()
        if ($clean.Length -gt 0) {
            $textChunks.Add($clean)
        }
    }
}

$output = $textChunks -join "`r`n"
[System.IO.File]::WriteAllText($OutPath, $output)
