param(
    [Parameter(Mandatory)][int]$ApiPort,
    [Parameter(Mandatory)][string]$Container,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [int]$WarmupSeconds = 10,
    [int]$MeasurementSeconds = 30,
    [int[]]$Users = @(10,50,100),
    [switch]$CapacityCheck,
    [switch]$CapacityOnly,
    [int]$MetricsPort = 0
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$docker = Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'
$image = 'grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34'
# Only the disposable database created by verify-initial-data.ps1 is accepted.
if ($Container -notmatch '^course-seed-check-[a-f0-9]{32}$') { throw '임시 검증 DB만 허용합니다.' }
$expected = [IO.Path]::GetFullPath((Join-Path $root ('.tools/seed-check-' + $Container.Substring(18))))
$output = [IO.Path]::GetFullPath($OutputDirectory)
if ($output -ne $expected) { throw '검증 실행의 출력 경로가 아닙니다.' }
if ($WarmupSeconds -lt 1 -or $MeasurementSeconds -lt 1 -or ($Users | Where-Object { $_ -lt 1 -or $_ -gt 100 })) { throw '잘못된 부하 조건' }
function Sql([string]$query) {
    $result = & $docker exec $Container psql -U seed_check -d seed_check -At -v ON_ERROR_STOP=1 -c $query
    if ($LASTEXITCODE -ne 0) { throw '부하 검증 DB 쿼리 실패' }
    return ($result -join [Environment]::NewLine)
}
$fixturePath = Join-Path $output 'fixture.json'
$loadContainer = 'course-load-' + [Guid]::NewGuid().ToString('N')
$results = [Collections.Generic.List[object]]::new()
$collector = $null
try {
    $studentCount = if ($CapacityCheck) { 200 } else { 100 }
    $students = (Sql "select student_number from student order by student_number limit $studentCount;") -split '\r?\n'
    $offerings = @((Sql 'select offering_id from course_offering order by capacity, offering_id limit 100;') -split '\r?\n' | ForEach-Object { [long]$_ })
    $tokens = @(foreach ($student in $students) {
        $body = @{studentNumber=[int]$student; password='startup-verification-only'} | ConvertTo-Json -Compress
        $response = Invoke-RestMethod "http://127.0.0.1:$ApiPort/auth/login" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 30
        $response.accessToken
    })
    if ($tokens.Count -ne $studentCount -or $offerings.Count -ne 100 -or ($tokens | Where-Object { -not $_ })) { throw '측정용 데이터 부족' }
    [IO.File]::WriteAllText($fixturePath, (@{tokens=$tokens; offerings=$offerings} | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false))
    $environment = [ordered]@{
        timestampUtc=[DateTime]::UtcNow.ToString('o'); image=$image
        jarSha256=(Get-FileHash (Join-Path $root 'build/libs/Course-Registration-System-0.1.0-SNAPSHOT.jar')).Hash
        logicalProcessors=[Environment]::ProcessorCount; os=[Environment]::OSVersion.VersionString
        docker=(& $docker info --format '{{json .}}' | ConvertFrom-Json | Select-Object ServerVersion,NCPU,MemTotal,OperatingSystem,KernelVersion)
        concentratedOffering=[long]$offerings[0]; capacity=[int](Sql "select capacity from course_offering where offering_id=$($offerings[0]);")
        warmupSeconds=$WarmupSeconds; measurementSeconds=$MeasurementSeconds; diagnostics=($MetricsPort -gt 0)
    }
    $environment | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $output 'load-environment.json') -Encoding utf8
    $headers = @{Authorization='Bearer '+$tokens[0]}
    # Assert management isolation instead of silently exposing diagnostics on the API port.
    $businessMetrics = Invoke-WebRequest "http://127.0.0.1:$ApiPort/actuator/prometheus" -Headers $headers -SkipHttpErrorCheck
    if ([int]$businessMetrics.StatusCode -ne 404) { throw '업무 포트에 지표가 노출됨' }
    if ($MetricsPort -gt 0) {
        $metricsUrl = "http://127.0.0.1:$MetricsPort/actuator/prometheus"
        $anonymous = Invoke-WebRequest $metricsUrl -SkipHttpErrorCheck
        if ([int]$anonymous.StatusCode -ne 401) { throw '지표 인증 누락' }
        $probe = Invoke-WebRequest $metricsUrl -Headers $headers
        if ($probe.Content -notmatch 'hikaricp_connections_active') { throw 'Hikari 지표 누락' }
    }
    if ($CapacityCheck) {
        if ($environment.capacity -ne 30 -or (Sql 'select count(*) from enrollment;') -ne '0') { throw '빈 정원 30명 fixture 필요' }
        & $docker run --rm --name $loadContainer -v "${output}:/work" -v "${PSScriptRoot}:/scripts:ro" -e "BASE_URL=http://host.docker.internal:$ApiPort" $image run --quiet /scripts/load-capacity.js
        if ($LASTEXITCODE -ne 0) { throw '200명 정원 경쟁 HTTP 검증 실패' }
        $capacityRows = Sql "select count(*),count(distinct student_number) from enrollment where offering_id=$($offerings[0]);"
        if ($capacityRows -ne '30|30') { throw "정원 경쟁 DB 확인 실패: $capacityRows" }
        [pscustomobject]@{students=200; capacity=30; rows=30; distinctStudents=30; http=(Get-Content (Join-Path $output 'capacity-summary.json') -Raw | ConvertFrom-Json)} | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $output 'capacity-result.json') -Encoding utf8
        Write-Output 'CAPACITY_RESULT=201:30,409:170,DB:30'
        Sql 'DELETE FROM enrollment;' | Out-Null
    }
    if ($CapacityOnly) { return }
    foreach ($workload in @('concentrated','distributed','mixed')) {
        foreach ($count in $Users) {
            $case = "$workload-$count"
            foreach ($phase in @('warmup','measured')) {
                # All requests have drained before resetting the isolated fixture.
                Sql 'DELETE FROM enrollment;' | Out-Null
                $seconds = if ($phase -eq 'warmup') { $WarmupSeconds } else { $MeasurementSeconds }
                Write-Output "LOAD_CASE=$case PHASE=$phase SECONDS=$seconds"
                [pscustomobject]@{event='start'; case=$case; phase=$phase; timestampUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json -Compress | Add-Content (Join-Path $output 'load-events.jsonl') -Encoding utf8
                if ($MetricsPort -gt 0 -and $phase -eq 'measured') {
                    Sql 'SELECT pg_stat_statements_reset();' | Out-Null
                    $diagnosticDirectory = Join-Path $output "$case-diagnostics"
                    New-Item -ItemType Directory -Force $diagnosticDirectory | Out-Null
                    $collector = Start-Job -FilePath (Join-Path $PSScriptRoot 'collect-load-diagnostics.ps1') -ArgumentList $metricsUrl,$tokens[0],$docker,$Container,$loadContainer,$diagnosticDirectory
                    $deadline = [DateTime]::UtcNow.AddSeconds(20)
                    while (-not (Test-Path (Join-Path $diagnosticDirectory 'ready'))) {
                        if ($collector.State -eq 'Failed' -or [DateTime]::UtcNow -gt $deadline) {
                            $errorFile = Join-Path $diagnosticDirectory 'collector-error.txt'
                            $reason = if (Test-Path $errorFile) { Get-Content $errorFile -Raw } else { $collector.ChildJobs[0].JobStateInfo.Reason.Message }
                            throw "지표 수집기 시작 실패: $reason"
                        }
                        Start-Sleep -Milliseconds 200
                    }
                }
                & $docker run --rm --name $loadContainer -v "${output}:/work" -v "${PSScriptRoot}:/scripts:ro" -e "BASE_URL=http://host.docker.internal:$ApiPort" -e "USERS=$count" -e "WORKLOAD=$workload" -e "SECONDS=$seconds" $image run --quiet /scripts/load-baseline.js
                $loadExitCode = $LASTEXITCODE
                [pscustomobject]@{event='end'; case=$case; phase=$phase; timestampUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json -Compress | Add-Content (Join-Path $output 'load-events.jsonl') -Encoding utf8
                if ($collector) {
                    New-Item -ItemType File (Join-Path $diagnosticDirectory 'stop') | Out-Null
                    $collector | Wait-Job -Timeout 15 | Out-Null
                    if ($collector.State -ne 'Completed') { throw '지표 수집기 종료 실패' }
                    $collector | Receive-Job -ErrorAction Stop | Out-Null
                    $collector | Remove-Job
                    $collector = $null
                    $queryStats = Sql "SELECT coalesce(json_agg(s),'[]'::json)::text FROM (SELECT query,calls,rows,total_exec_time,mean_exec_time,max_exec_time,shared_blks_hit,shared_blks_read FROM pg_stat_statements WHERE dbid=(SELECT oid FROM pg_database WHERE datname=current_database()) AND query NOT LIKE '%pg_stat_activity%' AND query NOT LIKE '%pg_stat_statements%' ORDER BY total_exec_time DESC LIMIT 30) s;"
                    $queryStats | Set-Content (Join-Path $diagnosticDirectory 'queries.json') -Encoding utf8
                }
                if ($loadExitCode -ne 0) { throw "k6 실행 실패: $case/$phase" }
                $summary = Get-Content (Join-Path $output 'summary.json') -Raw | ConvertFrom-Json
                $unexpected = 0
                foreach ($property in $summary.metrics.PSObject.Properties) {
                    if ($property.Name -like 'count_*_unexpected') { $unexpected += $property.Value.values.count }
                }
                if ($unexpected -gt 0) { throw "예상하지 못한 HTTP 응답 $unexpected 건: $case/$phase" }
                if ($phase -eq 'measured') {
                    Copy-Item -LiteralPath (Join-Path $output 'summary.json') -Destination (Join-Path $output "$case.json")
                    $invariants = Sql @'
SELECT
 (SELECT count(*) FROM (SELECT o.offering_id FROM course_offering o JOIN enrollment e USING(offering_id) GROUP BY o.offering_id,o.capacity HAVING count(*)>o.capacity) x),
 (SELECT count(*) FROM (SELECT e.student_number FROM enrollment e JOIN course_offering o USING(offering_id) GROUP BY e.student_number,o.academic_year,o.term HAVING sum(o.credits)>18) x),
 (SELECT count(*) FROM (SELECT e.student_number,o.subject_code,o.academic_year,o.term FROM enrollment e JOIN course_offering o USING(offering_id) GROUP BY e.student_number,o.subject_code,o.academic_year,o.term HAVING count(*)>1) x),
 (SELECT count(*) FROM enrollment a JOIN enrollment b ON a.student_number=b.student_number AND a.enrollment_id<b.enrollment_id JOIN class_meeting m ON m.offering_id=a.offering_id JOIN class_meeting n ON n.offering_id=b.offering_id AND m.day_of_week=n.day_of_week AND m.starts_at<n.ends_at AND n.starts_at<m.ends_at),
 (SELECT count(*) FROM enrollment);
'@
                    if ($invariants -ne '0|0|0|0|0') { throw "측정 종료 정합성/정리 확인 실패: $invariants" }
                    $results.Add([pscustomobject]@{case=$case; workload=$workload; users=$count; invariants=$invariants; metrics=$summary.metrics; state=$summary.state})
                    $results | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $output 'load-results.json') -Encoding utf8
                }
            }
        }
    }
    Sql 'DELETE FROM enrollment;' | Out-Null
    Write-Output "LOAD_RESULTS=$output/load-results.json"
} finally {
    if ($collector) { $collector | Stop-Job; $collector | Remove-Job }
    & $docker rm --force $loadContainer 2>$null | Out-Null
    if (Test-Path -LiteralPath $fixturePath) { Remove-Item -LiteralPath $fixturePath }
}
