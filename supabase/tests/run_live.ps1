# Runs the pgTAP tests and the concurrency check against the live self-hosted database over SSH.
# Every *_test.sql file wraps itself in begin ... rollback, so nothing it creates is kept; the concurrency check
# deletes its throwaway user. Usage: .\supabase\tests\run_live.ps1 [-HostName 54.163.241.161]
param(
    [string]$HostName = "54.163.241.161",
    [string]$DbContainer = "supabase-db-faifqncpzdrvhsf6zbvzwhvb"
)
$ErrorActionPreference = "Stop"
$remote = "/tmp/mp3studio-tests"
$here = $PSScriptRoot

ssh "root@$HostName" "rm -rf $remote && mkdir -p $remote"
scp -q (Get-ChildItem "$here\*_test.sql").FullName "$here\concurrency_check.sh" "root@${HostName}:$remote/"
if ($LASTEXITCODE -ne 0) { throw "Upload failed" }

$failed = 0
foreach ($file in Get-ChildItem "$here\*_test.sql" | Sort-Object Name) {
    $output = ssh "root@$HostName" "docker exec -i $DbContainer psql -U postgres -X -q -tA < $remote/$($file.Name) 2>&1"
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

if ($failed -gt 0) { throw "$failed check(s) failed" }
Write-Host "All live checks passed"
