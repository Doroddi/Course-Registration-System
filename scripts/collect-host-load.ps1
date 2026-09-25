param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
# Read-only Windows CPU counters. Kernel time includes idle time.
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class HostCpuClock {
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool GetSystemTimes(out long idle, out long kernel, out long user);
    public static long[] Read() {
        long idle, kernel, user;
        if (!GetSystemTimes(out idle,out kernel,out user)) throw new System.ComponentModel.Win32Exception();
        return new long[] {idle,kernel+user};
    }
}
'@
function Processes {
    $snapshot=@{}
    foreach($process in (Get-Process)) {
        try { if($process.Id -ne 0 -and $null -ne $process.CPU) {
            $snapshot[$process.Id]=[pscustomobject]@{name=$process.ProcessName; cpu=$process.CPU; started=$process.StartTime.ToUniversalTime().Ticks}
        }} catch { } # Exited or inaccessible processes are omitted, never stopped.
    }
    return $snapshot
}
try {
    $previous=Processes
    $before=[HostCpuClock]::Read()
    $watch=[Diagnostics.Stopwatch]::StartNew()
    $elapsed=$watch.Elapsed.TotalSeconds
    New-Item -ItemType File (Join-Path $OutputDirectory 'host-ready') | Out-Null
    do {
        Start-Sleep -Seconds 5
        $now=[HostCpuClock]::Read()
        $time=$watch.Elapsed.TotalSeconds
        $current=Processes
        $total=$now[1]-$before[1]
        if($total -le 0){throw 'Invalid Windows CPU counter interval'}
        $top=@(foreach($key in $current.Keys){
            if($previous.ContainsKey($key) -and $previous[$key].started -eq $current[$key].started){
                $delta=$current[$key].cpu-$previous[$key].cpu
                if($delta -ge 0){[pscustomobject]@{processId=$key; name=$current[$key].name; cpuPercent=100*$delta/($time-$elapsed)/[Environment]::ProcessorCount}}
            }
        }) | Sort-Object cpuPercent -Descending | Select-Object -First 10
        [pscustomobject]@{timestampUtc=[DateTime]::UtcNow.ToString('o'); intervalSeconds=$time-$elapsed; logicalProcessors=[Environment]::ProcessorCount; cpuPercent=100*(1-($now[0]-$before[0])/$total); topProcesses=@($top)} | ConvertTo-Json -Depth 5 -Compress | Add-Content (Join-Path $OutputDirectory 'host-samples.jsonl') -Encoding utf8
        $before=$now; $elapsed=$time; $previous=$current
    } while(-not(Test-Path (Join-Path $OutputDirectory 'host-stop')))
} catch {
    $_.Exception.Message | Set-Content (Join-Path $OutputDirectory 'host-error.txt') -Encoding utf8
    throw
}
