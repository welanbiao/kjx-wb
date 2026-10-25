#Requires -Version 5.1
<#
.SYNOPSIS
  Scan backend **/db/migration/V*.sql for duplicate or out-of-range Flyway versions.

Allowed version segments (docs/13 Flyway ranges, WP-51 ownership):
  WP-02       V100–V149
  WP-10       V200–V249
  WP-11/13    V300–V349
  WP-12/31    V400–V449
  WP-30       V500–V549
  WP-40/41/42 V600–V649
  WP-50       V700–V749

V1 baseline scripts (V1, V1.1, V1.2, … — first path segment 1) are allowed
outside those segments. Any other version outside the segments exits 1.
Duplicate Flyway versions (1 and 1.0 count as the same) also exit 1.

Windows PowerShell 5.1+ / PowerShell 7. Does not connect to MySQL.
Maven target/ copies are ignored so a local compile does not look like a duplicate version.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-RepoRoot {
    return (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
}

function Get-VersionParts {
    param([Parameter(Mandatory = $true)][string]$Version)
    return @($Version.Split('.') | ForEach-Object { [int]$_ })
}

function Get-NormalizedVersionKey {
    param([Parameter(Mandatory = $true)][int[]]$Parts)
    $trimmed = New-Object System.Collections.Generic.List[int]
    foreach ($part in $Parts) {
        [void]$trimmed.Add($part)
    }
    while ($trimmed.Count -gt 1 -and $trimmed[$trimmed.Count - 1] -eq 0) {
        $trimmed.RemoveAt($trimmed.Count - 1)
    }
    return ($trimmed -join '.')
}

function Test-AllowedFlywayVersion {
    param([Parameter(Mandatory = $true)][int]$Major)
    if ($Major -eq 1) {
        return $true
    }
    $ranges = @(
        @{ Lo = 100; Hi = 149 },
        @{ Lo = 200; Hi = 249 },
        @{ Lo = 300; Hi = 349 },
        @{ Lo = 400; Hi = 449 },
        @{ Lo = 500; Hi = 549 },
        @{ Lo = 600; Hi = 649 },
        @{ Lo = 700; Hi = 749 }
    )
    foreach ($range in $ranges) {
        if ($Major -ge $range.Lo -and $Major -le $range.Hi) {
            return $true
        }
    }
    return $false
}

$repoRoot = Get-RepoRoot
$backendRoot = Join-Path $repoRoot 'backend'
if (-not (Test-Path -LiteralPath $backendRoot)) {
    Write-Error "backend directory not found: $backendRoot"
    exit 1
}

$namePattern = '^V(\d+(?:\.\d+)*)__.+\.sql$'
# Skip Maven copies under target/; Flyway only loads src resources (and test resources).
$files = @(Get-ChildItem -LiteralPath $backendRoot -Recurse -File -Filter 'V*.sql' |
    Where-Object {
        $_.FullName -match '[\\/]db[\\/]migration[\\/][^\\/]+$' -and
        $_.FullName -notmatch '[\\/]target[\\/]'
    } |
    Sort-Object FullName)

if ($files.Count -eq 0) {
    Write-Error "No V*.sql files under $backendRoot **/db/migration"
    exit 1
}

$issues = New-Object System.Collections.Generic.List[string]
$byKey = @{}

foreach ($file in $files) {
    $relative = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/')
    if ($file.Name -notmatch $namePattern) {
        $issues.Add("UNPARSABLE`t$relative")
        continue
    }
    $version = $Matches[1]
    $parts = @(Get-VersionParts -Version $version)
    $key = Get-NormalizedVersionKey -Parts $parts
    $major = $parts[0]

    if (-not (Test-AllowedFlywayVersion -Major $major)) {
        $issues.Add("OUT_OF_RANGE`tV$version`t$relative")
    }

    if ($byKey.ContainsKey($key)) {
        $issues.Add("DUPLICATE`tV$version (normalized $key)`t$relative`talso $($byKey[$key])")
    }
    else {
        $byKey[$key] = $relative
    }
}

Write-Output "Flyway V*.sql scan"
Write-Output "backend: $backendRoot"
Write-Output ("files: {0}" -f $files.Count)
Write-Output ("unique versions: {0}" -f $byKey.Count)
Write-Output ""
Write-Output "version`tfile"
foreach ($file in $files) {
    if ($file.Name -match $namePattern) {
        $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/')
        Write-Output ("V{0}`t{1}" -f $Matches[1], $rel)
    }
}

if ($issues.Count -gt 0) {
    Write-Output ""
    Write-Output "FAILED"
    foreach ($issue in $issues) {
        Write-Output $issue
    }
    exit 1
}

Write-Output ""
Write-Output "OK: no duplicate versions; all scripts are V1 baseline or inside allowed segments."
exit 0
