param([Parameter(Mandatory)][string]$RunDirectory)
$ErrorActionPreference = 'Stop'
function Metric([string]$body,[string]$name) {
    $match=[regex]::Match($body,'(?m)^'+[regex]::Escape($name)+'(?:\{[^\r\n]*\})?\s+([-+0-9.eE]+)')
    if(-not $match.Success){throw "Missing metric: $name"}
    return [double]::Parse($match.Groups[1].Value,[Globalization.CultureInfo]::InvariantCulture)
}
$results=Get-Content (Join-Path $RunDirectory 'load-results.json') -Raw | ConvertFrom-Json
$report=@(foreach($case in $results){
    $directory=Join-Path $RunDirectory ($case.case+'-diagnostics')
    $files=@(Get-ChildItem $directory -Filter '*.prom' | Sort-Object {[int]$_.BaseName})
    if($files.Count -lt 2){throw 'Insufficient diagnostic samples'}
    $series=@(foreach($file in $files){
        $body=Get-Content $file.FullName -Raw
        $heap=0.0
        foreach($line in ($body -split '\r?\n')){
            if($line -match '^jvm_memory_used_bytes\{.*area="heap".*\}\s+([-+0-9.eE]+)'){
                $heap += [double]::Parse($Matches[1],[Globalization.CultureInfo]::InvariantCulture)
            }
        }
        [pscustomobject]@{
            index=[int]$file.BaseName
            active=Metric $body 'hikaricp_connections_active'
            pending=Metric $body 'hikaricp_connections_pending'
            maximum=Metric $body 'hikaricp_connections_max'
            acquireCount=Metric $body 'hikaricp_connections_acquire_seconds_count'
            acquireSeconds=Metric $body 'hikaricp_connections_acquire_seconds_sum'
            usageCount=Metric $body 'hikaricp_connections_usage_seconds_count'
            usageSeconds=Metric $body 'hikaricp_connections_usage_seconds_sum'
            processCpu=Metric $body 'process_cpu_usage'
            systemCpu=Metric $body 'system_cpu_usage'
            heapBytes=$heap
        }
    })
    $activity=@(Get-Content (Join-Path $directory 'samples.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
    $waitSeries=@(foreach($sample in $activity){
        $locks=@($sample.activity | Where-Object wait_event_type -eq 'Lock')
        [pscustomobject]@{index=$sample.index; timestampUtc=$sample.timestampUtc; finishedUtc=$sample.finishedUtc; lockWaiters=$locks.Count; blockedPids=@($locks | Select-Object pid,wait_event_type,wait_event,blockers)}
    })
    $first=$series[0]; $last=$series[-1]
    $acquires=$last.acquireCount-$first.acquireCount
    $usages=$last.usageCount-$first.usageCount
    if($acquires -le 0 -or $usages -le 0){throw 'No connection timer samples'}
    [pscustomobject]@{
        case=$case.case; http=$case.metrics; httpState=$case.state; invariants=$case.invariants
        samples=$series.Count; poolMaximum=$last.maximum
        peakActive=($series.active | Measure-Object -Maximum).Maximum
        peakPending=($series.pending | Measure-Object -Maximum).Maximum
        meanAcquireMs=1000*($last.acquireSeconds-$first.acquireSeconds)/$acquires
        meanUsageMs=1000*($last.usageSeconds-$first.usageSeconds)/$usages
        acquisitions=$acquires
        peakLockWaiters=($waitSeries.lockWaiters | Measure-Object -Maximum).Maximum
        peakProcessCpu=($series.processCpu | Measure-Object -Maximum).Maximum
        peakSystemCpu=($series.systemCpu | Measure-Object -Maximum).Maximum
        peakHeapBytes=($series.heapBytes | Measure-Object -Maximum).Maximum
        series=$series; waits=$waitSeries
        queries=(Get-Content (Join-Path $directory 'queries.json') -Raw | ConvertFrom-Json)
        resources=@(Get-Content (Join-Path $directory 'resources.jsonl') | ForEach-Object {$_ | ConvertFrom-Json})
    }
})
$report | ConvertTo-Json -Depth 15 | Set-Content (Join-Path $RunDirectory 'diagnostics-summary.json') -Encoding utf8
$report | Select-Object case,samples,poolMaximum,peakActive,peakPending,meanAcquireMs,meanUsageMs,peakLockWaiters,peakProcessCpu,peakSystemCpu,peakHeapBytes
