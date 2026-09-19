param([switch]$FirstOnly, [switch]$SqlStats, [int]$AcademicYear = 2026, [int]$Term = 2)
# 별도 임시 PostgreSQL 컨테이너에서 실행 JAR의 초기화·재시작을 검증한다.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$docker = Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'
$java = Join-Path $root '.tools/jdk-25.0.4.1+1/bin/java.exe'
$jar = Get-ChildItem (Join-Path $root 'build/libs') -Filter '*.jar' | Where-Object Name -NotLike '*-plain.jar' | Select-Object -First 1
if (-not $jar) { throw '먼저 bootJar를 실행하세요.' }
$runId = [Guid]::NewGuid().ToString('N')
$container = "course-seed-check-$runId"
$logs = Join-Path $root ".tools/seed-check-$runId"
New-Item -ItemType Directory $logs | Out-Null
$ownedContainer = $false
$appProcess = $null
$keys = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'INITIAL_STUDENT_PASSWORD', 'SERVER_PORT', 'ENROLLMENT_YEAR', 'ENROLLMENT_TERM')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }

function Sql([string]$query) {
    $result = & $docker exec $container psql -U seed_check -d seed_check -At -v ON_ERROR_STOP=1 -c $query
    if ($LASTEXITCODE -ne 0) { throw '검증용 DB 쿼리 실패' }
    return ($result -join "`n")
}
function Snapshot {
    $rows = foreach ($table in @('department','professor','student','subject','course_offering','class_meeting','teaching_assignment','enrollment')) {
        # 내용은 출력하지 않고, 정렬된 전체 행의 해시로 재시작 전후 보존 여부를 비교한다.
        Sql "SELECT '$table|' || count(*) || '|' || md5(coalesce(string_agg(row_text, E'\n' ORDER BY row_text), '')) FROM (SELECT row_to_json(t)::text AS row_text FROM $table t) rows;"
    }
    return ($rows -join "`n")
}
function ReadLiveLog([string]$path) {
    if (-not (Test-Path $path)) { return '' }
    $stream = [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
function Launch([string]$stage, [bool]$expectSuccess) {
    $stdout = Join-Path $logs "$stage.log"
    $stderr = Join-Path $logs "$stage.err.log"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $observedNotReady = $false
    $script:appProcess = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar.FullName + '"'), '--server.port=0', '--app.initial-data.enabled=true') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    while ($watch.Elapsed.TotalSeconds -lt 240) {
        $body = ReadLiveLog $stdout
        $readyMatch = [regex]::Match($body, 'Initial data ready: mode=(created|validated), elapsedMs=(\d+)')
        $port = [regex]::Match($body, 'Tomcat started on port (\d+)').Groups[1].Value
        if ($port) {
            $response = $null
            try { $response = Invoke-WebRequest "http://127.0.0.1:$port/health" -SkipHttpErrorCheck -TimeoutSec 2 } catch {}
            if ($response -and [int]$response.StatusCode -eq 503) { $observedNotReady = $true }
            if ($response -and [int]$response.StatusCode -eq 200) {
                if (-not $expectSuccess) { throw '불완전한 DB가 /health 200을 반환했습니다.' }
                # 로그 읽기와 HTTP 요청 사이에도 초기화가 완료될 수 있으므로 최신 로그로 확인한다.
                $readyMatch = [regex]::Match((ReadLiveLog $stdout), 'Initial data ready: mode=(created|validated), elapsedMs=(\d+)')
                if (-not $readyMatch.Success) { throw '데이터 준비 로그 없이 /health 200을 반환했습니다.' }
                if (($response.Content | ConvertFrom-Json).status -ne 'UP') { throw '준비 응답 본문 불일치' }
                return [pscustomobject]@{ stage=$stage; mode=$readyMatch.Groups[1].Value; observedSeconds=[math]::Round($watch.Elapsed.TotalSeconds, 3); runnerMs=[long]$readyMatch.Groups[2].Value; healthStatus=200; observedNotReady=$observedNotReady }
            }
        }
        if ($script:appProcess.HasExited) {
            $script:appProcess.WaitForExit()
            if ($expectSuccess) { throw "기동 실패. 로그: $stdout" }
            if ($script:appProcess.ExitCode -eq 0 -or $body -notmatch '수업 시간이 없는 강좌') { throw "예상한 검증 실패가 아닙니다. 로그: $stdout" }
            return [pscustomobject]@{ stage=$stage; exitCode=$script:appProcess.ExitCode; rejected=$true; observedNotReady=$observedNotReady }
        }
        Start-Sleep -Milliseconds 500
    }
    throw "기동 제한 시간 초과. 로그: $stdout"
}
function StopOwnedApp {
    if ($script:appProcess -and -not $script:appProcess.HasExited) {
        Stop-Process -Id $script:appProcess.Id -Force
        $script:appProcess.WaitForExit()
    }
    $script:appProcess = $null
}
try {
    $env:ENROLLMENT_YEAR = [string]$AcademicYear
    $env:ENROLLMENT_TERM = [string]$Term
    $env:DB_PASSWORD = [Guid]::NewGuid().ToString('N')
    $pgOptions = if ($SqlStats) { @('-c', 'shared_preload_libraries=pg_stat_statements') } else { @() }
    & $docker run --detach --rm --name $container -e POSTGRES_USER=seed_check -e POSTGRES_DB=seed_check -e "POSTGRES_PASSWORD=$env:DB_PASSWORD" -p '127.0.0.1::5432' postgres:18.6 @pgOptions | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '검증용 컨테이너 생성 실패' }
    $ownedContainer = $true
    $binding = & $docker port $container 5432/tcp
    $port = ($binding -split ':')[-1].Trim()
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$port/seed_check"
    $env:DB_USERNAME = 'seed_check'
    $env:INITIAL_STUDENT_PASSWORD = 'startup-verification-only'
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        & $docker exec $container pg_isready -h 127.0.0.1 -U seed_check -d seed_check *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw '검증용 PostgreSQL 준비 실패' }
    if ($SqlStats) { Sql 'CREATE EXTENSION pg_stat_statements;' | Out-Null }
    $first = Launch 'first' $true
    $first | ConvertTo-Json -Compress | Write-Output
    StopOwnedApp
    if ($SqlStats) {
        $stats = Sql "SELECT coalesce(json_agg(s), '[]'::json)::text FROM (SELECT query, calls, rows, round(total_exec_time::numeric, 3) AS total_exec_ms FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database()) ORDER BY calls DESC) s;"
        $stats | Set-Content (Join-Path $logs 'sql-stats.json') -Encoding utf8
    }
    $counts = Sql "SELECT (SELECT count(*) FROM department), (SELECT count(*) FROM professor), (SELECT count(*) FROM student), (SELECT count(*) FROM subject), (SELECT count(*) FROM course_offering), (SELECT count(*) FROM class_meeting), (SELECT count(*) FROM teaching_assignment), (SELECT count(*) FROM enrollment);"
    if ($counts -ne '10|100|10000|250|500|500|500|0') { throw "초기 개수 불일치: $counts" }
    $configuredCount = Sql "SELECT count(*) FROM course_offering WHERE academic_year = $AcademicYear AND term = $Term;"
    if ($configuredCount -ne '500') { throw "신청 대상 학기 설정 불일치: $configuredCount" }
    if ($FirstOnly) {
        [pscustomobject]@{ first=$first; counts=$counts; instrumented=[bool]$SqlStats } | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $logs 'result.json') -Encoding utf8
        Write-Output "RESULT_PATH=$logs/result.json"
        return
    }
    Sql 'INSERT INTO enrollment(student_number, offering_id) SELECT min(student_number), (SELECT min(offering_id) FROM course_offering) FROM student;' | Out-Null
    $before = Snapshot
    [Environment]::SetEnvironmentVariable('INITIAL_STUDENT_PASSWORD', $null, 'Process')
    $restart = Launch 'restart' $true
    if ($restart.mode -ne 'validated') { throw '재시작 시 생성 경로 실행' }
    $restart | ConvertTo-Json -Compress | Write-Output
    StopOwnedApp
    if ((Snapshot) -ne $before) { throw '재시작 후 데이터가 변경됐습니다.' }
    Sql 'DELETE FROM class_meeting WHERE meeting_id = (SELECT min(meeting_id) FROM class_meeting);' | Out-Null
    $brokenBefore = Snapshot
    $failure = Launch 'incomplete' $false
    $failure | ConvertTo-Json -Compress | Write-Output
    if ((Snapshot) -ne $brokenBefore) { throw '실패한 기동이 기존 데이터를 변경했습니다.' }
    $report = [pscustomobject]@{ first=$first; restart=$restart; incomplete=$failure; counts=$counts; preserved=$true; failurePreserved=$true; passwordAbsentOnRestart=$true }
    $report | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $logs 'result.json') -Encoding utf8
    Write-Output "RESULT_PATH=$logs/result.json"
} finally {
    StopOwnedApp
    if ($ownedContainer) { & $docker rm --force --volumes $container | Out-Null }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
