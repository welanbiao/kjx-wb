# Auto commit + push when Agent finishes (stop hook).
# Reads JSON from stdin (ignored); exits 0 always so the agent is not blocked.

$ErrorActionPreference = "Continue"
$null = [Console]::In.ReadToEnd()

function Write-HookOk {
    Write-Output "{}"
    exit 0
}

try {
    $status = git status --porcelain 2>$null
    if (-not $status) {
        Write-HookOk
    }

    # Skip obvious secrets
    $blocked = @(
        "\.env$",
        "\.env\.",
        "credentials\.json$",
        "secret",
        "\.pem$",
        "id_rsa",
        "\.key$"
    )
    $files = git status --porcelain | ForEach-Object { $_.Substring(3).Trim('"') }
    foreach ($f in $files) {
        foreach ($pat in $blocked) {
            if ($f -match $pat) {
                Write-Host "auto-commit skipped: blocked file $f"
                Write-HookOk
            }
        }
    }

    git add -A
    $msg = "auto: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    git commit -m $msg
    if ($LASTEXITCODE -ne 0) {
        Write-HookOk
    }

    git push
    Write-HookOk
}
catch {
    Write-Host "auto-commit error: $_"
    Write-HookOk
}
