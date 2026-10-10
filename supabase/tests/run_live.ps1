# Runs the pgTAP tests and the concurrency check against the live self-hosted database over SSH.
# Every *_test.sql file wraps itself in begin ... rollback, so nothing it creates is kept; the concurrency check
# deletes its throwaway user. Usage: .\supabase\tests\run_live.ps1 [-HostName 54.163.241.161]
# -Pending takes migration file names that are not applied live yet. They are run inside each test's transaction
# and rolled back with it, so new migrations can be checked before they are applied:
#   .\supabase\tests\run_live.ps1 -Pending 20261013000000_payu_billing.sql
param(
    [string]$HostName = "54.163.241.161",
    [string]$DbContainer = "supabase-db-faifqncpzdrvhsf6zbvzwhvb",
    [string[]]$Pending = @()
)
$ErrorActionPreference = "Stop"
$remote = "/tmp/mp3studio-tests"
$here = $PSScriptRoot
$migrations = Join-Path (Split-Path $here) "migrations"
$staging = Join-Path ([System.IO.Path]::GetTempPath()) "mp3studio-tests"

Remove-Item -Recurse -Force $staging -ErrorAction SilentlyContinue
New-Item -ItemType Directory $staging | Out-Null
$prefix = ($Pending | ForEach-Object { Get-Content -Raw (Join-Path $migrations $_) }) -join "`n"
foreach ($file in Get-ChildItem "$here\*_test.sql") {
    $body = Get-Content -Raw $file.FullName
    if ($Pending.Count -gt 0) {
        # The test's own "begin;" opens the transaction; the pending migrations run right after it.
        $body = ([regex]'(?m)^begin;\s*$').Replace($body, "begin;`n$($prefix.Replace('$', '$$'))`n", 1)
    }
    [System.IO.File]::WriteAllText((Join-Path $staging $file.Name), $body, (New-Object System.Text.UTF8Encoding $false))
}
Copy-Item "$here\concurrency_check.sh" $staging

ssh "root@$HostName" "rm -rf $remote && mkdir -p $remote"
scp -q (Get-ChildItem "$staging\*").FullName "root@${HostName}:$remote/"
if ($LASTEXITCODE -ne 0) { throw "Upload failed" }

# The live tables belong to supabase_admin (migrations are applied as that role), so pending migrations run as it.
$dbUser = if ($Pending.Count -gt 0) { "supabase_admin" } else { "postgres" }
$failed = 0
foreach ($file in Get-ChildItem "$staging\*_test.sql" | Sort-Object Name) {
    $output = ssh "root@$HostName" "docker exec -i $DbContainer psql -U $dbUser -X -q -tA < $remote/$($file.Name) 2>&1"
    $notOk = @($output | Where-Object { $_ -match '^not ok|ERROR|Looks like' })
    $passed = @($output | Where-Object { $_ -match '^ok ' }).Count
    if ($notOk.Count -gt 0) {
        $failed++
        Write-Host "FAIL $($file.Name) ($passed passed)" -ForegroundColor Red
        $notOk | ForEach-Object { Write-Host "  $_" }
    } else {
        Write-Host "ok   $($file.Name) ($passed passed)" -ForegroundColor Green
    }
}

ssh "root@$HostName" "sed -i 's/\r$//' $remote/concurrency_check.sh && bash $remote/concurrency_check.sh $DbContainer"
if ($LASTEXITCODE -ne 0) { $failed++ }
ssh "root@$HostName" "rm -rf $remote"
Remove-Item -Recurse -Force $staging

if ($failed -gt 0) { throw "$failed check(s) failed" }
Write-Host "All live checks passed"
