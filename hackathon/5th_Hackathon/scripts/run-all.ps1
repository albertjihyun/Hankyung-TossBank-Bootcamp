# OLIVE — 백엔드 + 프런트 + cloudflared 터널을 한 번에 띄우고 공개 URL을 출력.
# 사전조건: SETUP.md Phase 1~4 완료(JDK/Node/cloudflared 설치, seed-products.csv + public/products 준비).
# 사용:  .\scripts\run-all.ps1   (5th_Hackathon 루트 또는 어디서든)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot          # 5th_Hackathon/
$backend = Join-Path $root "backend"
$frontend = Join-Path $root "frontend"

# --- JAVA_HOME 자동 탐지 (없으면) ---
if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    $cand = Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
    if ($cand) { $env:JAVA_HOME = $cand.FullName }
}
Write-Host "JAVA_HOME = $env:JAVA_HOME"

# --- 사전조건 확인 ---
if (-not (Test-Path (Join-Path $backend "seed-products.csv"))) {
    throw "seed-products.csv 없음. SETUP.md Phase 4(prepare-seed.py)를 먼저 실행하세요."
}

# --- 포트 정리 함수 ---
function Free-Port($port) {
    $pids = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -Expand OwningProcess -Unique
    foreach ($procId in $pids) { Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue }
}
Free-Port 8080; Free-Port 3000

# --- 1) 백엔드 ---
Write-Host "`n[1/3] Spring Boot (8080) 기동..."
$be = Start-Process -PassThru -WindowStyle Minimized -FilePath "cmd.exe" `
        -ArgumentList "/c", "set JAVA_HOME=$env:JAVA_HOME&& cd /d `"$backend`" && gradlew.bat bootRun"
# 헬스체크
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $r = Invoke-WebRequest "http://localhost:8080/api/products?size=1" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch { Start-Sleep 3 }
}
if (-not $ok) { throw "백엔드 기동 실패(8080 무응답). backend 로그를 확인하세요." }
Write-Host "    백엔드 OK"

# --- 2) 프런트 ---
Write-Host "[2/3] Next.js (3000) 기동..."
if (-not (Test-Path (Join-Path $frontend "node_modules"))) {
    Push-Location $frontend; npm install; Pop-Location
}
$env:API_BASE = "http://localhost:8080"
$fe = Start-Process -PassThru -WindowStyle Minimized -FilePath "cmd.exe" `
        -ArgumentList "/c", "set API_BASE=http://localhost:8080&& cd /d `"$frontend`" && npm run dev"
$ok = $false
for ($i = 0; $i -lt 40; $i++) {
    try {
        $r = Invoke-WebRequest "http://localhost:3000/" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch { Start-Sleep 2 }
}
if (-not $ok) { throw "프런트 기동 실패(3000 무응답)." }
Write-Host "    프런트 OK"

# --- 3) cloudflared 터널 ---
Write-Host "[3/3] cloudflared 터널 기동..."
$log = Join-Path $env:TEMP "olive-tunnel.log"
if (Test-Path $log) { Remove-Item $log -Force }
$tn = Start-Process -PassThru -WindowStyle Minimized -FilePath "cloudflared" `
        -ArgumentList "tunnel", "--url", "http://localhost:3000" `
        -RedirectStandardError $log
$url = $null
for ($i = 0; $i -lt 30; $i++) {
    if (Test-Path $log) {
        $m = Select-String -Path $log -Pattern "https://[a-z0-9-]+\.trycloudflare\.com" -ErrorAction SilentlyContinue |
             Select-Object -First 1
        if ($m) { $url = $m.Matches[0].Value; break }
    }
    Start-Sleep 2
}

Write-Host "`n=============================================="
if ($url) {
    Write-Host " 공개 URL:  $url"
} else {
    Write-Host " 터널 URL을 못 찾음. 로그 확인: $log"
}
Write-Host " 로컬:      http://localhost:3000"
Write-Host " PIDs:      backend=$($be.Id)  frontend=$($fe.Id)  tunnel=$($tn.Id)"
Write-Host " 종료:      .\scripts\stop-all.ps1  (또는 위 PID들 Stop-Process)"
Write-Host "=============================================="
