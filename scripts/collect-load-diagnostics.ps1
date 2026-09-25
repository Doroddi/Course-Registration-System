param($MetricsUrl,$Token,$Docker,$Container,$LoadContainer,$OutputDirectory)
$ErrorActionPreference = 'Stop'
$headers = @{Authorization='Bearer '+$Token}
$index = 0
try {
    while ($true) {
        $timestamp = [DateTime]::UtcNow.ToString('o')
        $metrics = Invoke-WebRequest $MetricsUrl -Headers $headers -TimeoutSec 5
        $metrics.Content | Set-Content (Join-Path $OutputDirectory "$index.prom") -Encoding utf8
        $activity = & $Docker exec $Container psql -U seed_check -d seed_check -At -v ON_ERROR_STOP=1 -c "SELECT coalesce(json_agg(s),'[]'::json)::text FROM (SELECT pid,state,wait_event_type,wait_event,extract(epoch from clock_timestamp()-query_start)*1000 AS query_age_ms,pg_blocking_pids(pid) AS blockers,left(query,300) AS query FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid()) s;"
        if ($LASTEXITCODE -ne 0) { throw 'DB activity sampling failed' }
        [pscustomobject]@{index=$index; timestampUtc=$timestamp; finishedUtc=[DateTime]::UtcNow.ToString('o'); activity=(($activity -join [Environment]::NewLine) | ConvertFrom-Json)} | ConvertTo-Json -Depth 6 -Compress | Add-Content (Join-Path $OutputDirectory 'samples.jsonl') -Encoding utf8
        if ($index -eq 0) { New-Item -ItemType File (Join-Path $OutputDirectory 'ready') | Out-Null }
        if (Test-Path (Join-Path $OutputDirectory 'stop')) { break }
        # Resource sampling is less frequent; actual timestamps expose sampling delays.
        if ($index % 5 -eq 1) {
            $stats = & $Docker stats --no-stream --format '{{json .}}' $Container $LoadContainer 2>$null
            foreach ($line in $stats) {
                [pscustomobject]@{timestampUtc=[DateTime]::UtcNow.ToString('o'); resource=($line | ConvertFrom-Json)} | ConvertTo-Json -Compress | Add-Content (Join-Path $OutputDirectory 'resources.jsonl') -Encoding utf8
            }
        }
        $index++
        Start-Sleep -Seconds 1
    }
} catch {
    $_.Exception.Message | Set-Content (Join-Path $OutputDirectory 'collector-error.txt') -Encoding utf8
    throw
} finally { $Token = $null; $headers = $null }
