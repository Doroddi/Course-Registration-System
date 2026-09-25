param([Parameter(Mandatory)][string]$RunDirectory)
$ErrorActionPreference='Stop'
$samples=@(Get-Content (Join-Path $RunDirectory 'host-samples.jsonl') | ForEach-Object {$_|ConvertFrom-Json})
$events=@(Get-Content (Join-Path $RunDirectory 'load-events.jsonl') | ForEach-Object {$_|ConvertFrom-Json})
$beforeServer=(Get-Content (Join-Path $RunDirectory 'host-events.jsonl') -Raw | ConvertFrom-Json).timestampUtc
function Aggregate($subset) {
    $rows=@($subset)
    if($rows.Count -eq 0){throw 'No host CPU samples for interval'}
    $weight=($rows.intervalSeconds | Measure-Object -Sum).Sum
    $weighted=($rows | ForEach-Object {$_.cpuPercent*$_.intervalSeconds} | Measure-Object -Sum).Sum
    [pscustomobject]@{samples=$rows.Count; meanCpuPercent=$weighted/$weight; minimumCpuPercent=($rows.cpuPercent|Measure-Object -Minimum).Minimum; maximumCpuPercent=($rows.cpuPercent|Measure-Object -Maximum).Maximum}
}
$idle=Aggregate @($samples | Where-Object {[DateTime]$_.timestampUtc -lt [DateTime]$beforeServer})
$cases=@(foreach($start in ($events | Where-Object {$_.event -eq 'start' -and $_.phase -eq 'measured'})) {
    $end=$events | Where-Object {$_.case -eq $start.case -and $_.phase -eq 'measured' -and $_.event -eq 'end'} | Select-Object -First 1
    if(-not $end){throw 'Missing phase end'}
    $subset=@($samples | Where-Object {[DateTime]$_.timestampUtc -ge [DateTime]$start.timestampUtc -and [DateTime]$_.timestampUtc -le [DateTime]$end.timestampUtc})
    [pscustomobject]@{case=$start.case; startUtc=$start.timestampUtc; endUtc=$end.timestampUtc; cpu=(Aggregate $subset)}
})
# Deliberately omit process names/PIDs from the shareable result.
$result=[pscustomobject]@{beforeServer=$idle; phases=$cases}
$result | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $RunDirectory 'host-summary.json') -Encoding utf8
$result
